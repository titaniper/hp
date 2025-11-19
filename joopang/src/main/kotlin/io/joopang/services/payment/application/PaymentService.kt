package io.joopang.services.payment.application

import io.joopang.services.common.domain.Money
import io.joopang.services.payment.domain.Payment
import io.joopang.services.payment.domain.PaymentMethod
import io.joopang.services.payment.domain.PaymentNotFoundException
import io.joopang.services.payment.domain.PaymentStatus
import io.joopang.services.payment.infrastructure.PaymentRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional(readOnly = true)
class PaymentService(
    private val paymentRepository: PaymentRepository,
) {

    fun listPayments(orderId: Long?): List<Output> =
        if (orderId == null) {
            paymentRepository.findAll()
        } else {
            paymentRepository.findAllByOrderId(orderId)
        }.map { it.toOutput() }

    fun getPayment(id: Long): Output =
        paymentRepository.findByIdOrNull(id)
            ?.toOutput()
            ?: throw PaymentNotFoundException(id.toString())

    @Transactional
    fun registerPayment(command: RegisterPaymentCommand): Output {
        val payment = Payment(
            id = command.id ?: 0,
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
        return paymentRepository.save(payment).toOutput()
    }

    private fun Payment.toOutput(): Output =
        Output(
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

    data class RegisterPaymentCommand(
        val orderId: Long,
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
        val id: Long? = null,
    )

    data class Output(
        val id: Long,
        val orderId: Long,
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
    )
}
