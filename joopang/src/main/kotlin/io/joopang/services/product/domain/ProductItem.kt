package io.joopang.services.product.domain

import io.joopang.services.common.domain.Money
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(
    name = "product_items",
    indexes = [
        Index(
            name = "idx_product_items_product_id",
            columnList = "product_id",
        ),
    ],
)
class ProductItem(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "BIGINT")
    var id: Long = 0,

    @Column(name = "product_id", columnDefinition = "BIGINT", nullable = false)
    var productId: Long? = null,

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
        if (id != 0L || name.isNotBlank()) {
            require(name.isNotBlank()) { "Product item name must not be blank" }
        }
    }

    fun isActive(): Boolean = status == ProductItemStatus.ACTIVE

    @Suppress("unused")
    constructor() : this(
        id = 0,
        productId = null,
        name = "",
        unitPrice = Money.ZERO,
        description = null,
        stock = StockQuantity.ZERO,
        status = ProductItemStatus.ACTIVE,
        code = ProductItemCode("DEFAULT_ITEM"),
    )

    fun copy(
        id: Long = this.id,
        productId: Long? = this.productId,
        name: String = this.name,
        unitPrice: Money = this.unitPrice,
        description: String? = this.description,
        stock: StockQuantity = this.stock,
        status: ProductItemStatus = this.status,
        code: ProductItemCode = this.code,
    ): ProductItem =
        ProductItem(
            id = id,
            productId = productId,
            name = name,
            unitPrice = unitPrice,
            description = description,
            stock = stock,
            status = status,
            code = code,
        )
}
