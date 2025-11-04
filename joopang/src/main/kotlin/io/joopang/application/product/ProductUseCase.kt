package io.joopang.application.product

import io.joopang.application.common.CacheService
import io.joopang.domain.common.Money
import io.joopang.domain.common.Percentage
import io.joopang.domain.product.Product
import io.joopang.domain.product.ProductCode
import io.joopang.domain.product.ProductItem
import io.joopang.domain.product.ProductItemCode
import io.joopang.domain.product.ProductItemStatus
import io.joopang.domain.product.ProductNotFoundException
import io.joopang.domain.product.ProductRepository
import io.joopang.domain.product.ProductSearchCondition
import io.joopang.domain.product.ProductSort
import io.joopang.domain.product.ProductStatus
import io.joopang.domain.product.ProductWithItems
import io.joopang.domain.product.StockQuantity
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@Service
class ProductUseCase(
    private val productRepository: ProductRepository,
    private val cacheService: CacheService,
) {

    fun getProducts(
        categoryId: UUID? = null,
        sort: ProductSort = ProductSort.NEWEST,
    ): List<ProductWithItems> {
        val cacheKey = buildProductsCacheKey(categoryId, sort)
        val cached = cacheService.get(cacheKey) as? List<ProductWithItems>
        if (cached != null) {
            return cached
        }

        val products = productRepository.findAll(
            ProductSearchCondition(categoryId = categoryId, sort = sort),
        )
        cacheService.put(cacheKey, products, DEFAULT_CACHE_TTL_SECONDS)

        return products
    }

    fun getProduct(productId: UUID): ProductWithItems =
        productRepository.findById(productId)
            ?: throw ProductNotFoundException(productId.toString())

    fun createProduct(command: CreateProductCommand): ProductWithItems {
        require(command.items.isNotEmpty()) { "Product must have at least one item" }

        val productId = UUID.randomUUID()
        val product = Product(
            id = productId,
            name = command.name,
            code = ProductCode(command.code),
            description = command.description,
            content = command.content,
            status = command.status,
            sellerId = command.sellerId,
            categoryId = command.categoryId,
            price = Money.of(command.price),
            discountRate = command.discountRate?.let(Percentage::of),
            version = command.version,
        )

        val items = command.items.map { itemCommand ->
            ProductItem(
                id = UUID.randomUUID(),
                productId = productId,
                name = itemCommand.name,
                unitPrice = Money.of(itemCommand.unitPrice),
                description = itemCommand.description,
                stock = StockQuantity.of(itemCommand.stock),
                status = itemCommand.status,
                code = ProductItemCode(itemCommand.code),
            )
        }

        val saved = productRepository.save(ProductWithItems(product, items))
        invalidateProductCaches(product.categoryId)

        return saved
    }

    fun updateProduct(productId: UUID, command: UpdateProductCommand): ProductWithItems {
        require(command.items.isNotEmpty()) { "Product must have at least one item" }

        val existing = productRepository.findById(productId)
            ?: throw ProductNotFoundException(productId.toString())

        val updatedProduct = existing.product.copy(
            name = command.name,
            code = ProductCode(command.code),
            description = command.description,
            content = command.content,
            status = command.status,
            sellerId = command.sellerId,
            categoryId = command.categoryId,
            price = Money.of(command.price),
            discountRate = command.discountRate?.let(Percentage::of),
            version = command.version,
        )

        val updatedItems = command.items.map { itemCommand ->
            ProductItem(
                id = itemCommand.id ?: UUID.randomUUID(),
                productId = productId,
                name = itemCommand.name,
                unitPrice = Money.of(itemCommand.unitPrice),
                description = itemCommand.description,
                stock = StockQuantity.of(itemCommand.stock),
                status = itemCommand.status,
                code = ProductItemCode(itemCommand.code),
            )
        }

        val updatedAggregate = productRepository.update(ProductWithItems(updatedProduct, updatedItems))
        invalidateProductCaches(existing.product.categoryId, updatedProduct.categoryId)

        return updatedAggregate
    }

    fun getTopProducts(days: Long = 3, limit: Int = 5): TopProductsResult {
        require(days > 0) { "Days must be greater than zero" }
        require(limit > 0) { "Limit must be greater than zero" }

        val startDate = LocalDate.now().minusDays(days)
        val topProducts = productRepository.findTopSelling(startDate, limit)

        val ranked = topProducts
            .take(limit)
            .mapIndexed { index, aggregate ->
                TopProduct(
                    rank = index + 1,
                    aggregate = aggregate,
                )
            }

        return TopProductsResult(
            period = "${days}days",
            products = ranked,
        )
    }

    fun checkStock(productId: UUID, quantity: Long): StockCheckResult {
        require(quantity > 0) { "Requested quantity must be at least 1" }

        val aggregate = productRepository.findById(productId)
            ?: throw ProductNotFoundException(productId.toString())

        val requested = StockQuantity.of(quantity)
        val available = aggregate.items
            .filter { it.isActive() }
            .map { it.stock }
            .fold(StockQuantity.ZERO) { acc, stock -> acc + stock }

        return StockCheckResult(
            available = available.isGreaterOrEqual(requested),
            currentStock = available,
            requested = requested,
            aggregate = aggregate,
        )
    }

    private fun buildProductsCacheKey(categoryId: UUID?, sort: ProductSort): String =
        "products:${categoryId ?: "all"}:${sort.name.lowercase()}"

    private fun invalidateProductCaches(vararg categoryIds: UUID?) {
        val uniqueCategories = (categoryIds.toSet() + setOf<UUID?>(null))
        ProductSort.entries.forEach { sort ->
            uniqueCategories.forEach { categoryId ->
                cacheService.evict(buildProductsCacheKey(categoryId, sort))
            }
        }
    }

    data class CreateProductCommand(
        val name: String,
        val code: String,
        val description: String?,
        val content: String?,
        val status: ProductStatus = ProductStatus.ON_SALE,
        val sellerId: UUID,
        val categoryId: UUID,
        val price: BigDecimal,
        val discountRate: BigDecimal? = null,
        val version: Int = 0,
        val items: List<CreateProductItemCommand>,
    )

    data class CreateProductItemCommand(
        val name: String,
        val unitPrice: BigDecimal,
        val description: String?,
        val stock: BigDecimal,
        val status: ProductItemStatus = ProductItemStatus.ACTIVE,
        val code: String,
    )

    data class UpdateProductCommand(
        val name: String,
        val code: String,
        val description: String?,
        val content: String?,
        val status: ProductStatus,
        val sellerId: UUID,
        val categoryId: UUID,
        val price: BigDecimal,
        val discountRate: BigDecimal? = null,
        val version: Int,
        val items: List<UpdateProductItemCommand>,
    )

    data class UpdateProductItemCommand(
        val id: UUID?,
        val name: String,
        val unitPrice: BigDecimal,
        val description: String?,
        val stock: BigDecimal,
        val status: ProductItemStatus,
        val code: String,
    )

    data class TopProductsResult(
        val period: String,
        val products: List<TopProduct>,
    )

    data class TopProduct(
        val rank: Int,
        val aggregate: ProductWithItems,
    )

    data class StockCheckResult(
        val available: Boolean,
        val currentStock: StockQuantity,
        val requested: StockQuantity,
        val aggregate: ProductWithItems,
    )

    companion object {
        private const val DEFAULT_CACHE_TTL_SECONDS = 60L
    }
}
