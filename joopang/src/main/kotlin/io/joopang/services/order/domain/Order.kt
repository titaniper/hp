package io.joopang.services.order.domain

import io.joopang.services.common.domain.Money
import io.joopang.services.common.domain.OrderMonth
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "orders")
data class Order(
    @Id
    @Column(columnDefinition = "BINARY(16)")
    var id: UUID = UUID(0L, 0L),

    @Column(name = "user_id", columnDefinition = "BINARY(16)", nullable = false)
    var userId: UUID = UUID(0L, 0L),

    @Column(name = "image_url")
    var imageUrl: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: OrderStatus = OrderStatus.PENDING,

    @Column(name = "recipient_name", nullable = false)
    var recipientName: String = "",

    @Column(name = "order_month", nullable = false, length = 7)
    var orderMonth: OrderMonth = OrderMonth.from(1970, 1),

    @Column(name = "total_amount", precision = 19, scale = 2, nullable = false)
    var totalAmount: Money = Money.ZERO,

    @Column(name = "discount_amount", precision = 19, scale = 2, nullable = false)
    var discountAmount: Money = Money.ZERO,

    @Column(name = "ordered_at", nullable = false)
    var orderedAt: Instant = Instant.EPOCH,

    @Column(name = "paid_at")
    var paidAt: Instant? = null,

    @Column(columnDefinition = "TEXT")
    var memo: String? = null,
) {

    init {
        require(recipientName.isNotBlank()) { "Recipient name must not be blank" }
        require(totalAmount >= Money.ZERO) { "Total amount cannot be negative" }
        require(discountAmount >= Money.ZERO) { "Discount amount cannot be negative" }
        require(discountAmount <= totalAmount) { "Discount cannot exceed total amount" }
    }

    fun payableAmount(): Money = totalAmount - discountAmount

    fun canPay(): Boolean = status == OrderStatus.PENDING

    fun markPaid(paidTimestamp: Instant): Order =
        copy(status = OrderStatus.PAID, paidAt = paidTimestamp)
}
