package io.joopang.services.product.domain

import io.joopang.services.common.domain.BaseEntity
import io.joopang.services.common.domain.Money
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
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
    id: Long? = null,
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
) : BaseEntity(id) {

    init {
        if (id != null || name.isNotBlank()) {
            require(name.isNotBlank()) { "Product item name must not be blank" }
        }
    }

    fun isActive(): Boolean = status == ProductItemStatus.ACTIVE

    @Suppress("unused")
    constructor() : this(
        id = null,
        productId = null,
        name = "",
        unitPrice = Money.ZERO,
        description = null,
        stock = StockQuantity.ZERO,
        status = ProductItemStatus.ACTIVE,
        code = ProductItemCode("DEFAULT_ITEM"),
    )

    fun copy(
        id: Long? = this.id,
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
