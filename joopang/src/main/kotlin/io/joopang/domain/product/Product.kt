package io.joopang.domain.product

import io.joopang.domain.common.Money
import io.joopang.domain.common.Percentage
import java.util.UUID

data class Product(
    val id: UUID,
    val name: String,
    val code: ProductCode,
    val description: String?,
    val content: String?,
    val status: ProductStatus,
    val sellerId: UUID,
    val categoryId: UUID,
    val price: Money,
    val discountRate: Percentage? = null,
    val version: Int,
    val viewCount: Int = 0,
    val salesCount: Int = 0,
) {

    init {
        require(name.isNotBlank()) { "Product name must not be blank" }
        require(version >= 0) { "Product version must be non-negative" }
        require(viewCount >= 0) { "View count must be non-negative" }
        require(salesCount >= 0) { "Sales count must be non-negative" }
    }

    fun discountedPrice(): Money =
        discountRate?.let {
            val discount = price.toBigDecimal()
                .multiply(it.toDecimalFraction(scale = 4))
            Money.of(price.toBigDecimal().subtract(discount))
        } ?: price

    fun hasDiscount(): Boolean = discountRate != null
}
