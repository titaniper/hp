package io.joopang.infrastructure.product

import io.joopang.domain.common.Money
import io.joopang.domain.common.Percentage
import io.joopang.domain.product.Product
import io.joopang.domain.product.ProductCode
import io.joopang.domain.product.ProductItem
import io.joopang.domain.product.ProductItemCode
import io.joopang.domain.product.ProductItemStatus
import io.joopang.domain.product.ProductSearchCondition
import io.joopang.domain.product.ProductSort
import io.joopang.domain.product.ProductStatus
import io.joopang.domain.product.ProductWithItems
import io.joopang.domain.product.StockQuantity
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@Repository
open class ProductRepository {

    private val products = CopyOnWriteArrayList<Product>()
    private val productItems = CopyOnWriteArrayList<ProductItem>()
    private val productCreatedAt = ConcurrentHashMap<UUID, LocalDate>()
    private val dailySales = ConcurrentHashMap<UUID, List<DailySale>>()

    init {
        seed()
    }

    open fun findAll(condition: ProductSearchCondition): List<ProductWithItems> {
        val filtered = products.filter { product ->
            condition.categoryId?.let { product.categoryId == it } ?: true
        }

        val sorted = when (condition.sort) {
            ProductSort.NEWEST -> filtered.sortedByDescending { productCreatedAt[it.id] ?: LocalDate.MIN }
            ProductSort.SALES -> filtered.sortedByDescending { totalSalesSince(it.id, LocalDate.MIN) }
            ProductSort.PRICE_ASC -> filtered.sortedBy { it.price.toBigDecimal() }
            ProductSort.PRICE_DESC -> filtered.sortedByDescending { it.price.toBigDecimal() }
        }

        return sorted.map { product ->
            ProductWithItems(product, items(product.id))
        }
    }

    open fun findTopSelling(startDateInclusive: LocalDate, limit: Int): List<ProductWithItems> =
        products
            .map { product ->
                product to totalSalesSince(product.id, startDateInclusive)
            }
            .sortedByDescending { (_, sales) -> sales }
            .take(limit)
            .map { (product, _) ->
                ProductWithItems(product, items(product.id))
            }

    open fun findById(productId: UUID): ProductWithItems? =
        products.firstOrNull { it.id == productId }
            ?.let { found ->
                ProductWithItems(found, items(found.id))
            }

    open fun save(aggregate: ProductWithItems): ProductWithItems {
        require(products.none { it.id == aggregate.product.id }) {
            "Product with id ${aggregate.product.id} already exists"
        }
        products += aggregate.product
        replaceItems(aggregate.product.id, aggregate.items)
        productCreatedAt[aggregate.product.id] = LocalDate.now()
        dailySales.putIfAbsent(aggregate.product.id, emptyList())
        return ProductWithItems(aggregate.product, items(aggregate.product.id))
    }

    open fun update(aggregate: ProductWithItems): ProductWithItems {
        val index = products.indexOfFirst { it.id == aggregate.product.id }
        require(index >= 0) { "Product with id ${aggregate.product.id} not found" }
        products[index] = aggregate.product
        replaceItems(aggregate.product.id, aggregate.items)
        return ProductWithItems(aggregate.product, items(aggregate.product.id))
    }

    private fun items(productId: UUID): List<ProductItem> =
        productItems.filter { it.productId == productId }

    private fun replaceItems(productId: UUID, newItems: List<ProductItem>) {
        require(newItems.all { it.productId == productId }) {
            "Product item productId must match aggregate id"
        }
        productItems.removeIf { it.productId == productId }
        productItems.addAll(newItems)
    }

    private fun totalSalesSince(productId: UUID, startDateInclusive: LocalDate): Int =
        dailySales[productId]
            ?.filter { !it.date.isBefore(startDateInclusive) }
            ?.sumOf { it.quantity }
            ?: 0

    private fun seed() {
        if (products.isNotEmpty()) {
            return
        }

        val sellerId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
        val electronicsId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
        val beautyId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc")

        val phoneId = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val phone = Product(
            id = phoneId,
            name = "Galaxy Fold",
            code = ProductCode("GALAXY-FOLD"),
            description = "Latest foldable smartphone",
            content = "Premium foldable experience with cutting-edge display.",
            status = ProductStatus.ON_SALE,
            sellerId = sellerId,
            categoryId = electronicsId,
            price = Money.of(239900L),
            discountRate = Percentage.of(5.0),
            version = 3,
            viewCount = 4820,
            salesCount = 350,
        )
        registerProduct(phone, LocalDate.now().minusDays(2))
        productItems.addAll(
            listOf(
                ProductItem(
                    id = UUID.fromString("21111111-1111-1111-1111-111111111111"),
                    productId = phoneId,
                    name = "Galaxy Fold Phantom Black",
                    unitPrice = Money.of(239900L),
                    description = "Phantom Black color option",
                    stock = StockQuantity.of(25L),
                    status = ProductItemStatus.ACTIVE,
                    code = ProductItemCode("GFOLD-BLK"),
                ),
                ProductItem(
                    id = UUID.fromString("21111111-1111-1111-1111-222222222222"),
                    productId = phoneId,
                    name = "Galaxy Fold Matte Silver",
                    unitPrice = Money.of(239900L),
                    description = "Matte Silver color option",
                    stock = StockQuantity.of(18L),
                    status = ProductItemStatus.ACTIVE,
                    code = ProductItemCode("GFOLD-SLV"),
                ),
            ),
        )
        dailySales[phoneId] = listOf(
            DailySale(LocalDate.now().minusDays(1), 24),
            DailySale(LocalDate.now().minusDays(2), 17),
            DailySale(LocalDate.now().minusDays(3), 11),
        )

        val lipstickId = UUID.fromString("22222222-2222-2222-2222-222222222222")
        val lipstick = Product(
            id = lipstickId,
            name = "Velvet Matte Lipstick",
            code = ProductCode("VELVET-LIP"),
            description = "Luxurious matte lipstick",
            content = "Smooth matte finish with long-lasting color payoff.",
            status = ProductStatus.ON_SALE,
            sellerId = sellerId,
            categoryId = beautyId,
            price = Money.of(25900L),
            discountRate = Percentage.of(15.0),
            version = 5,
            viewCount = 10950,
            salesCount = 1240,
        )
        registerProduct(lipstick, LocalDate.now().minusDays(5))
        productItems.addAll(
            listOf(
                ProductItem(
                    id = UUID.fromString("23333333-3333-3333-3333-333333333333"),
                    productId = lipstickId,
                    name = "Velvet Matte - Ruby Red",
                    unitPrice = Money.of(25900L),
                    description = "Bold ruby red shade",
                    stock = StockQuantity.of(120L),
                    status = ProductItemStatus.ACTIVE,
                    code = ProductItemCode("VELVET-RUBY"),
                ),
                ProductItem(
                    id = UUID.fromString("24444444-4444-4444-4444-444444444444"),
                    productId = lipstickId,
                    name = "Velvet Matte - Vintage Rose",
                    unitPrice = Money.of(25900L),
                    description = "Romantic vintage rose shade",
                    stock = StockQuantity.of(87L),
                    status = ProductItemStatus.ACTIVE,
                    code = ProductItemCode("VELVET-ROSE"),
                ),
            ),
        )
        dailySales[lipstickId] = listOf(
            DailySale(LocalDate.now().minusDays(1), 56),
            DailySale(LocalDate.now().minusDays(2), 42),
            DailySale(LocalDate.now().minusDays(3), 31),
            DailySale(LocalDate.now().minusDays(4), 22),
        )

        val earbudsId = UUID.fromString("33333333-3333-3333-3333-333333333333")
        val earbuds = Product(
            id = earbudsId,
            name = "NeoBuds Pro",
            code = ProductCode("NEOBUDS-PRO"),
            description = "Noise-cancelling wireless earbuds",
            content = "Adaptive ANC with studio-quality sound.",
            status = ProductStatus.ON_SALE,
            sellerId = sellerId,
            categoryId = electronicsId,
            price = Money.of(129000L),
            discountRate = null,
            version = 2,
            viewCount = 6540,
            salesCount = 780,
        )
        registerProduct(earbuds, LocalDate.now().minusDays(1))
        productItems.addAll(
            listOf(
                ProductItem(
                    id = UUID.fromString("25555555-5555-5555-5555-555555555555"),
                    productId = earbudsId,
                    name = "NeoBuds Pro - Graphite",
                    unitPrice = Money.of(129000L),
                    description = "Graphite color",
                    stock = StockQuantity.of(65L),
                    status = ProductItemStatus.ACTIVE,
                    code = ProductItemCode("NEOBUDS-GRP"),
                ),
                ProductItem(
                    id = UUID.fromString("26666666-6666-6666-6666-666666666666"),
                    productId = earbudsId,
                    name = "NeoBuds Pro - Pearl",
                    unitPrice = Money.of(129000L),
                    description = "Pearl white color",
                    stock = StockQuantity.of(54L),
                    status = ProductItemStatus.ACTIVE,
                    code = ProductItemCode("NEOBUDS-PRL"),
                ),
            ),
        )
        dailySales[earbudsId] = listOf(
            DailySale(LocalDate.now().minusDays(1), 28),
            DailySale(LocalDate.now().minusDays(2), 33),
            DailySale(LocalDate.now().minusDays(3), 19),
        )
    }

    private fun registerProduct(product: Product, createdAt: LocalDate) {
        products += product
        productCreatedAt[product.id] = createdAt
    }

    private data class DailySale(
        val date: LocalDate,
        val quantity: Int,
    )
}
