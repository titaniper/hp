package io.joopang.services.product.application

import io.joopang.services.common.application.CacheService
import io.joopang.services.common.domain.Money
import io.joopang.services.common.domain.Percentage
import io.joopang.services.common.domain.requireId
import io.joopang.services.common.monitoring.TrackPerformance
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
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Service
@Transactional(readOnly = true)
class ProductService(
    private val productRepository: ProductRepository,

    // TODO: cachable 로 개선
    private val cacheService: CacheService,
) {

    fun getProducts(
        categoryId: Long? = null,
        sort: ProductSort = ProductSort.NEWEST,
    ): List<Output> {
        val cacheKey = buildProductsCacheKey(categoryId, sort)
        val cached = cacheService.get(cacheKey) as? List<Output>
        if (cached != null) {
            return cached
        }

        val products = productRepository.findProducts(categoryId, sort)
        val outputs = products.map { it.toOutput() }
        cacheService.put(cacheKey, outputs, DEFAULT_CACHE_TTL_SECONDS)

        return outputs
    }

    fun getProduct(productId: Long): Output =
        productRepository.findById(productId)
            ?.toOutput()
            ?: throw ProductNotFoundException(productId.toString())

    @Transactional
    fun createProduct(command: CreateProductCommand): Output {
        require(command.items.isNotEmpty()) { "Product must have at least one item" }

        val product = Product(
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
                productId = null,
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

    @Transactional
    fun updateProduct(productId: Long, command: UpdateProductCommand): Output {
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
                id = itemCommand.id,
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

    @TrackPerformance("getTopProducts")
    fun getTopProducts(days: Long = 3, limit: Int = 5): TopProductsOutput {
        require(days > 0) { "Days must be greater than zero" }
        require(limit > 0) { "Limit must be greater than zero" }

        val cutoff = Instant.now().minus(days, ChronoUnit.DAYS)
        val rows = productRepository.findPopularProductsSince(cutoff, limit)
        if (rows.isEmpty()) {
            return TopProductsOutput(period = "${days}days", products = emptyList())
        }

        val aggregatesById = productRepository
            .findProductsByIds(rows.map { it.productId })
            .associateBy { it.product.id }

        val ranked = rows.mapIndexed { index, row ->
            val aggregate = aggregatesById[row.productId]
                ?: throw ProductNotFoundException(row.productId.toString())
            TopProductOutput(
                rank = index + 1,
                product = aggregate.toOutput(),
                salesCount = row.salesCount,
                revenue = row.revenue,
            )
        }

        return TopProductsOutput(
            period = "${days}days",
            products = ranked,
        )
    }

    fun checkStock(productId: Long, quantity: Long): StockCheckOutput {
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

    private fun buildProductsCacheKey(categoryId: Long?, sort: ProductSort): String =
        "products:${categoryId ?: "all"}:${sort.name.lowercase()}"

    private fun invalidateProductCaches(vararg categoryIds: Long?) {
        val uniqueCategories = (categoryIds.toSet() + setOf<Long?>(null))
        ProductSort.entries.forEach { sort ->
            uniqueCategories.forEach { categoryId ->
                cacheService.evict(buildProductsCacheKey(categoryId, sort))
            }
        }
    }

    private fun productComparator(sort: ProductSort): Comparator<ProductWithItems> =
        when (sort) {
            ProductSort.NEWEST -> compareByDescending { aggregate ->
                val productId = aggregate.product.requireId()
                productRepository.findProductCreatedAt(productId) ?: LocalDate.MIN
            }
            ProductSort.SALES -> compareByDescending { aggregate ->
                totalSalesSince(aggregate.product.requireId(), LocalDate.MIN)
            }
            ProductSort.PRICE_ASC -> compareBy { aggregate -> aggregate.product.price.toBigDecimal() }
            ProductSort.PRICE_DESC -> compareByDescending { aggregate -> aggregate.product.price.toBigDecimal() }
        }

    private fun totalSalesSince(productId: Long, startDateInclusive: LocalDate): Int {
        return productRepository.findDailySales(productId)
            .filter { !it.date.isBefore(startDateInclusive) }
            .sumOf { it.quantity }
    }

    private fun ProductWithItems.toOutput(): Output =
        Output(
            id = product.requireId(),
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
            id = requireId(),
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
        val sellerId: Long,
        val categoryId: Long,
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
        val sellerId: Long,
        val categoryId: Long,
        val price: BigDecimal,
        val discountRate: BigDecimal? = null,
        val version: Int,
        val items: List<UpdateProductItemCommand>,
    )

    data class UpdateProductItemCommand(
        val id: Long?,
        val name: String,
        val unitPrice: BigDecimal,
        val description: String?,
        val stock: BigDecimal,
        val status: ProductItemStatus,
        val code: String,
    )

    data class Output(
        val id: Long,
        val name: String,
        val code: String,
        val description: String?,
        val content: String?,
        val status: ProductStatus,
        val sellerId: Long,
        val categoryId: Long,
        val price: Money,
        val discountRate: BigDecimal?,
        val version: Int,
        val viewCount: Int,
        val salesCount: Int,
        val items: List<Item>,
    ) {
        data class Item(
            val id: Long,
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
        val salesCount: Long,
        val revenue: Money,
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
