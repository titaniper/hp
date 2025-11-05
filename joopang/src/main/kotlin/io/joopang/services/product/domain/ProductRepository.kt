package io.joopang.services.product.domain

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
