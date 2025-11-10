package io.joopang.services.payment.domain

import io.joopang.services.common.domain.Money
import java.time.Instant
import java.util.UUID

data class Payment(
    val id: UUID,
    val orderId: UUID,
    val paymentGateway: String,
    val paymentMethod: PaymentMethod,
    val paymentAmount: Money,
    val remainingBalance: Money,
    val status: PaymentStatus,
    val paymentKey: String? = null,
    val transactionId: String? = null,
    val requestedAt: Instant,
    val approvedAt: Instant? = null,
    val cancelledAt: Instant? = null,
) {

    init {
        require(paymentGateway.isNotBlank()) { "Payment gateway must not be blank" }
        require(paymentAmount >= Money.ZERO) { "Payment amount cannot be negative" }
        require(remainingBalance >= Money.ZERO) { "Remaining balance cannot be negative" }
    }

    fun isCompleted(): Boolean = status == PaymentStatus.COMPLETED

    fun isRefunded(): Boolean = status == PaymentStatus.REFUNDED || status == PaymentStatus.PARTIAL_REFUNDED
}
