package io.joopang.domain.product

import java.time.LocalDate
import java.util.UUID

data class ProductSearchCondition(
    val categoryId: UUID? = null,
    val sort: ProductSort = ProductSort.NEWEST,
)

enum class ProductSort {
    NEWEST,
    SALES,
    PRICE_ASC,
    PRICE_DESC,
}

data class ProductWithItems(
    val product: Product,
    val items: List<ProductItem>,
)

interface ProductRepository {
    fun findAll(condition: ProductSearchCondition = ProductSearchCondition()): List<ProductWithItems>
    fun findTopSelling(startDateInclusive: LocalDate, limit: Int): List<ProductWithItems>
    fun findById(productId: UUID): ProductWithItems?
    fun save(aggregate: ProductWithItems): ProductWithItems
    fun update(aggregate: ProductWithItems): ProductWithItems
}
