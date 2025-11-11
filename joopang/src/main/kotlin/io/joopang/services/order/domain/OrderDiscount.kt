package io.joopang.services.order.domain

import io.joopang.services.common.domain.Money
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "order_discounts")
data class OrderDiscount(
    @Id
    @Column(columnDefinition = "BINARY(16)")
    var id: UUID = UUID(0L, 0L),

    @Column(name = "order_id", columnDefinition = "BINARY(16)", nullable = false)
    var orderId: UUID = UUID(0L, 0L),

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var type: OrderDiscountType = OrderDiscountType.POINT,

    @Column(name = "reference_id", columnDefinition = "BINARY(16)")
    var referenceId: UUID? = null,

    @Column(name = "price", precision = 19, scale = 2, nullable = false)
    var price: Money = Money.ZERO,

    @Column(name = "coupon_id", columnDefinition = "BINARY(16)")
    var couponId: UUID? = null,
) {

    init {
        require(price >= Money.ZERO) { "Discount price cannot be negative" }
    }
}
