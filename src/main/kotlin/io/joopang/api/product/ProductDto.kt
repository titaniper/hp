package io.joopang.api.product

import java.math.BigDecimal
import java.util.UUID

data class MoneyDto(
    val amount: BigDecimal,
    val currency: String,
)

data class ProductDto(
    val id: UUID,
    val name: String,
    val code: String,
    val description: String?,
    val content: String?,
    val status: ProductStatus,
    val sellerId: UUID,
    val categoryId: UUID,
    val price: MoneyDto,
    val discountRate: BigDecimal,
    val version: Int,
    val items: List<ProductItemDto>,
)

data class ProductItemDto(
    val id: UUID,
    val name: String,
    val code: String,
    val description: String?,
    val status: ProductItemStatus,
    val stock: BigDecimal,
    val unitPrice: BigDecimal,
    val price: MoneyDto,
)

enum class ProductStatus {
    ON_SALE,
}

enum class ProductItemStatus {
    ACTIVE,
}
