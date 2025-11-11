package io.joopang.services.product.domain

import io.joopang.services.common.domain.Money
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "product_items")
data class ProductItem(
    @Id
    @Column(columnDefinition = "BINARY(16)")
    var id: UUID = UUID(0L, 0L),

    @Column(name = "product_id", columnDefinition = "BINARY(16)", nullable = false)
    var productId: UUID = UUID(0L, 0L),

    @Column(nullable = false)
    var name: String = "",

    @Column(name = "unit_price", precision = 19, scale = 2, nullable = false)
    var unitPrice: Money = Money.ZERO,

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    @Column(name = "stock", precision = 19, scale = 2, nullable = false)
    var stock: StockQuantity = StockQuantity.ZERO,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: ProductItemStatus = ProductItemStatus.ACTIVE,

    @Column(nullable = false, unique = true, length = 191)
    var code: ProductItemCode = ProductItemCode("DEFAULT_ITEM"),
) {

    init {
        require(name.isNotBlank()) { "Product item name must not be blank" }
    }

    fun isActive(): Boolean = status == ProductItemStatus.ACTIVE
}
