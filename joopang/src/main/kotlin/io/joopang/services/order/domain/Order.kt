package io.joopang.services.order.domain

import io.joopang.services.common.domain.Money
import io.joopang.services.common.domain.OrderMonth
import java.time.Instant
import java.util.UUID

data class Order(
    val id: UUID,
    val userId: UUID,
    val imageUrl: String?,
    val status: OrderStatus,
    val recipientName: String,
    val orderMonth: OrderMonth,
    val totalAmount: Money,
    val discountAmount: Money = Money.ZERO,
    val orderedAt: Instant,
    val paidAt: Instant? = null,
    val memo: String? = null,
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
