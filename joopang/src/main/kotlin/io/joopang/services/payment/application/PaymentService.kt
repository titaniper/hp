package io.joopang.services.payment.application

import io.joopang.services.common.domain.Money
import io.joopang.services.payment.domain.Payment
import io.joopang.services.payment.domain.PaymentMethod
import io.joopang.services.payment.domain.PaymentNotFoundException
import io.joopang.services.payment.domain.PaymentStatus
import io.joopang.services.payment.infrastructure.PaymentRepository
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class PaymentService(
    private val paymentRepository: PaymentRepository,
) {

    fun listPayments(orderId: UUID?): List<Payment> =
        if (orderId == null) {
            paymentRepository.findAll()
        } else {
            paymentRepository.findByOrderId(orderId)
        }

    fun getPayment(id: UUID): Payment =
        paymentRepository.findById(id)
            ?: throw PaymentNotFoundException(id.toString())

    fun registerPayment(command: RegisterPaymentCommand): Payment {
        val payment = Payment(
            id = command.id ?: UUID.randomUUID(),
            orderId = command.orderId,
            paymentGateway = command.paymentGateway,
            paymentMethod = command.paymentMethod,
            paymentAmount = command.paymentAmount,
            remainingBalance = command.remainingBalance,
            status = command.status,
            paymentKey = command.paymentKey,
            transactionId = command.transactionId,
            requestedAt = command.requestedAt,
            approvedAt = command.approvedAt,
            cancelledAt = command.cancelledAt,
        )
        return paymentRepository.save(payment)
    }

    data class RegisterPaymentCommand(
        val orderId: UUID,
        val paymentGateway: String,
        val paymentMethod: PaymentMethod,
        val paymentAmount: Money,
        val remainingBalance: Money,
        val status: PaymentStatus,
        val paymentKey: String?,
        val transactionId: String?,
        val requestedAt: Instant,
        val approvedAt: Instant?,
        val cancelledAt: Instant?,
        val id: UUID? = null,
    )
}
