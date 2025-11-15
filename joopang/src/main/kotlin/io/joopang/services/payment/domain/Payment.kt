package io.joopang.services.payment.domain

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
import java.time.Instant

@Entity
@Table(
    name = "payments",
    indexes = [
        Index(
            name = "idx_payments_order_id",
            columnList = "order_id",
        ),
    ],
)
class Payment(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "BIGINT")
    var id: Long = 0,

    @Column(name = "order_id", columnDefinition = "BIGINT", nullable = false)
    var orderId: Long = 0,

    @Column(name = "payment_gateway", nullable = false)
    var paymentGateway: String = "",

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 32)
    var paymentMethod: PaymentMethod = PaymentMethod.CREDIT_CARD,

    @Column(name = "payment_amount", precision = 19, scale = 2, nullable = false)
    var paymentAmount: Money = Money.ZERO,

    @Column(name = "remaining_balance", precision = 19, scale = 2, nullable = false)
    var remainingBalance: Money = Money.ZERO,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: PaymentStatus = PaymentStatus.PENDING,

    @Column(name = "payment_key")
    var paymentKey: String? = null,

    @Column(name = "transaction_id")
    var transactionId: String? = null,

    @Column(name = "requested_at", nullable = false)
    var requestedAt: Instant = Instant.EPOCH,

    @Column(name = "approved_at")
    var approvedAt: Instant? = null,

    @Column(name = "cancelled_at")
    var cancelledAt: Instant? = null,
) {

    init {
        require(paymentGateway.isNotBlank()) { "Payment gateway must not be blank" }
        require(paymentAmount >= Money.ZERO) { "Payment amount cannot be negative" }
        require(remainingBalance >= Money.ZERO) { "Remaining balance cannot be negative" }
    }

    fun isCompleted(): Boolean = status == PaymentStatus.COMPLETED

    fun isRefunded(): Boolean = status == PaymentStatus.REFUNDED || status == PaymentStatus.PARTIAL_REFUNDED
}
