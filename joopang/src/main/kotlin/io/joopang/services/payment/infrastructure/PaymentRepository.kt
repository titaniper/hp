package io.joopang.services.payment.infrastructure

import io.joopang.services.common.domain.Money
import io.joopang.services.payment.domain.Payment
import io.joopang.services.payment.domain.PaymentMethod
import io.joopang.services.payment.domain.PaymentStatus
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Repository
open class PaymentRepository {

    private val store = ConcurrentHashMap<UUID, Payment>()

    init {
        seed()
    }

    open fun findAll(): List<Payment> = store.values.toList()

    open fun findById(id: UUID): Payment? = store[id]

    open fun findByOrderId(orderId: UUID): List<Payment> =
        store.values.filter { it.orderId == orderId }

    open fun save(payment: Payment): Payment {
        store[payment.id] = payment
        return payment
    }

    private fun seed() {
        val orderId = UUID.fromString("ffffffff-aaaa-bbbb-cccc-dddddddddddd")
        val paymentId = UUID.fromString("77777777-8888-9999-aaaa-bbbbbbbbbbbb")

        store[paymentId] = Payment(
            id = paymentId,
            orderId = orderId,
            paymentGateway = "CoupayPay",
            paymentMethod = PaymentMethod.CREDIT_CARD,
            paymentAmount = Money.of(120_000L),
            remainingBalance = Money.of(880_000L),
            status = PaymentStatus.COMPLETED,
            paymentKey = "payment-key-$paymentId",
            transactionId = "tx-$paymentId",
            requestedAt = Instant.now().minusSeconds(3600),
            approvedAt = Instant.now().minusSeconds(1800),
        )
    }
}
