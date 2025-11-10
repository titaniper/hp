package io.joopang.services.payment.infrastructure.jpa

import io.joopang.services.common.domain.Money
import io.joopang.services.payment.domain.Payment
import io.joopang.services.payment.domain.PaymentMethod
import io.joopang.services.payment.domain.PaymentStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "payments")
class PaymentEntity(
    @Id
    @Column(columnDefinition = "BINARY(16)")
    var id: UUID,

    @Column(name = "order_id", columnDefinition = "BINARY(16)", nullable = false)
    var orderId: UUID,

    @Column(name = "payment_gateway", nullable = false)
    var paymentGateway: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 32)
    var paymentMethod: PaymentMethod,

    @Column(name = "payment_amount", precision = 19, scale = 2, nullable = false)
    var paymentAmount: Money,

    @Column(name = "remaining_balance", precision = 19, scale = 2, nullable = false)
    var remainingBalance: Money,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: PaymentStatus,

    @Column(name = "payment_key")
    var paymentKey: String?,

    @Column(name = "transaction_id")
    var transactionId: String?,

    @Column(name = "requested_at", nullable = false)
    var requestedAt: Instant,

    @Column(name = "approved_at")
    var approvedAt: Instant?,

    @Column(name = "cancelled_at")
    var cancelledAt: Instant?,
) {
    fun toDomain(): Payment = Payment(
        id = id,
        orderId = orderId,
        paymentGateway = paymentGateway,
        paymentMethod = paymentMethod,
        paymentAmount = paymentAmount,
        remainingBalance = remainingBalance,
        status = status,
        paymentKey = paymentKey,
        transactionId = transactionId,
        requestedAt = requestedAt,
        approvedAt = approvedAt,
        cancelledAt = cancelledAt,
    )

    companion object {
        fun from(domain: Payment): PaymentEntity = PaymentEntity(
            id = domain.id,
            orderId = domain.orderId,
            paymentGateway = domain.paymentGateway,
            paymentMethod = domain.paymentMethod,
            paymentAmount = domain.paymentAmount,
            remainingBalance = domain.remainingBalance,
            status = domain.status,
            paymentKey = domain.paymentKey,
            transactionId = domain.transactionId,
            requestedAt = domain.requestedAt,
            approvedAt = domain.approvedAt,
            cancelledAt = domain.cancelledAt,
        )
    }
}
