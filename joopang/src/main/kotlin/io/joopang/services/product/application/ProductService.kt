package io.joopang.services.product.application

import io.joopang.services.common.application.CacheService
import io.joopang.services.common.domain.Money
import io.joopang.services.common.domain.Percentage
import io.joopang.services.product.domain.Product
import io.joopang.services.product.domain.ProductCode
import io.joopang.services.product.domain.ProductItem
import io.joopang.services.product.domain.ProductItemCode
import io.joopang.services.product.domain.ProductItemStatus
import io.joopang.services.product.domain.ProductNotFoundException
import io.joopang.services.product.domain.ProductSort
import io.joopang.services.product.domain.ProductStatus
import io.joopang.services.product.domain.ProductWithItems
import io.joopang.services.product.domain.StockQuantity
import io.joopang.services.product.infrastructure.ProductRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@Service
class ProductService(
    private val productRepository: ProductRepository,

    // TODO: cachable 로 개선
    private val cacheService: CacheService,
) {

    fun getProducts(
        categoryId: UUID? = null,
        sort: ProductSort = ProductSort.NEWEST,
    ): List<Output> {
        val cacheKey = buildProductsCacheKey(categoryId, sort)
        val cached = cacheService.get(cacheKey) as? List<Output>
        if (cached != null) {
            return cached
        }

        val products = productRepository.findAll()
            .let { aggregates ->
                aggregates
                    .filter { aggregate ->
                        categoryId?.let { aggregate.product.categoryId == it } ?: true
                    }
                    .sortedWith(productComparator(sort))
            }
        val outputs = products.map { it.toOutput() }
        cacheService.put(cacheKey, outputs, DEFAULT_CACHE_TTL_SECONDS)

        return outputs
    }

    fun getProduct(productId: UUID): Output =
        productRepository.findById(productId)
            ?.toOutput()
            ?: throw ProductNotFoundException(productId.toString())

    fun createProduct(command: CreateProductCommand): Output {
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

        return saved.toOutput()
    }

    fun updateProduct(productId: UUID, command: UpdateProductCommand): Output {
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

        return updatedAggregate.toOutput()
    }

    fun getTopProducts(days: Long = 3, limit: Int = 5): TopProductsOutput {
        require(days > 0) { "Days must be greater than zero" }
        require(limit > 0) { "Limit must be greater than zero" }

        val startDate = LocalDate.now().minusDays(days)
        val ranked = productRepository.findAll()
            .map { aggregate ->
                aggregate to totalSalesSince(aggregate.product.id, startDate)
            }
            .sortedByDescending { (_, sales) -> sales }
            .take(limit)
            .mapIndexed { index, (aggregate, _) ->
                TopProductOutput(
                    rank = index + 1,
                    product = aggregate.toOutput(),
                )
            }

        return TopProductsOutput(
            period = "${days}days",
            products = ranked,
        )
    }

    fun checkStock(productId: UUID, quantity: Long): StockCheckOutput {
        require(quantity > 0) { "Requested quantity must be at least 1" }

        val aggregate = productRepository.findById(productId)
            ?: throw ProductNotFoundException(productId.toString())

        val requested = StockQuantity.of(quantity)
        val available = aggregate.items
            .filter { it.isActive() }
            .map { it.stock }
            .fold(StockQuantity.ZERO) { acc, stock -> acc + stock }

        return StockCheckOutput(
            available = available.isGreaterOrEqual(requested),
            currentStock = available,
            requested = requested,
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

    private fun productComparator(sort: ProductSort): Comparator<ProductWithItems> =
        when (sort) {
            ProductSort.NEWEST -> compareByDescending { aggregate ->
                productRepository.findProductCreatedAt(aggregate.product.id) ?: LocalDate.MIN
            }
            ProductSort.SALES -> compareByDescending { aggregate ->
                totalSalesSince(aggregate.product.id, LocalDate.MIN)
            }
            ProductSort.PRICE_ASC -> compareBy { aggregate -> aggregate.product.price.toBigDecimal() }
            ProductSort.PRICE_DESC -> compareByDescending { aggregate -> aggregate.product.price.toBigDecimal() }
        }

    private fun totalSalesSince(productId: UUID, startDateInclusive: LocalDate): Int =
        productRepository.findDailySales(productId)
            .filter { !it.date.isBefore(startDateInclusive) }
            .sumOf { it.quantity }

    private fun ProductWithItems.toOutput(): Output =
        Output(
            id = product.id,
            name = product.name,
            code = product.code.value,
            description = product.description,
            content = product.content,
            status = product.status,
            sellerId = product.sellerId,
            categoryId = product.categoryId,
            price = product.price,
            discountRate = product.discountRate?.value,
            version = product.version,
            viewCount = product.viewCount,
            salesCount = product.salesCount,
            items = items.map { it.toOutput() },
        )

    private fun ProductItem.toOutput(): Output.Item =
        Output.Item(
            id = id,
            name = name,
            unitPrice = unitPrice,
            description = description,
            stock = stock,
            status = status,
            code = code.value,
        )

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

    data class Output(
        val id: UUID,
        val name: String,
        val code: String,
        val description: String?,
        val content: String?,
        val status: ProductStatus,
        val sellerId: UUID,
        val categoryId: UUID,
        val price: Money,
        val discountRate: BigDecimal?,
        val version: Int,
        val viewCount: Int,
        val salesCount: Int,
        val items: List<Item>,
    ) {
        data class Item(
            val id: UUID,
            val name: String,
            val unitPrice: Money,
            val description: String?,
            val stock: StockQuantity,
            val status: ProductItemStatus,
            val code: String,
        )
    }

    data class TopProductsOutput(
        val period: String,
        val products: List<TopProductOutput>,
    )

    data class TopProductOutput(
        val rank: Int,
        val product: Output,
    )

    data class StockCheckOutput(
        val available: Boolean,
        val currentStock: StockQuantity,
        val requested: StockQuantity,
    )

    companion object {
        private const val DEFAULT_CACHE_TTL_SECONDS = 60L
    }
}
