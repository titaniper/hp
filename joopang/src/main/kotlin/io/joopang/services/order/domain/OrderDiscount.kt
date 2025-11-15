package io.joopang.services.order.domain

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
    name = "order_discounts",
    indexes = [
        Index(
            name = "idx_order_discounts_order_id",
            columnList = "order_id",
        ),
    ],
)
class OrderDiscount(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "BIGINT")
    var id: Long = 0,

    @Column(name = "order_id", columnDefinition = "BIGINT", nullable = false)
    var orderId: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var type: OrderDiscountType = OrderDiscountType.POINT,

    @Column(name = "reference_id", columnDefinition = "BIGINT")
    var referenceId: Long? = null,

    @Column(name = "price", precision = 19, scale = 2, nullable = false)
    var price: Money = Money.ZERO,

    @Column(name = "coupon_id", columnDefinition = "BIGINT")
    var couponId: Long? = null,
) {

    init {
        require(price >= Money.ZERO) { "Discount price cannot be negative" }
    }
}
