package io.joopang.services.product.domain

import io.joopang.services.common.domain.Money
import java.util.UUID

data class ProductItem(
    val id: UUID,
    val productId: UUID,
    val name: String,
    val unitPrice: Money,
    val description: String?,
    val stock: StockQuantity,
    val status: ProductItemStatus,
    val code: ProductItemCode,
) {

    init {
        require(name.isNotBlank()) { "Product item name must not be blank" }
    }

    fun isActive(): Boolean = status == ProductItemStatus.ACTIVE
}
