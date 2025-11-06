package io.joopang.services.payment.presentation

import io.joopang.services.common.domain.Money
import io.joopang.services.payment.application.PaymentService
import io.joopang.services.payment.domain.Payment
import io.joopang.services.payment.domain.PaymentMethod
import io.joopang.services.payment.domain.PaymentStatus
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/payments")
class PaymentController(
    private val paymentService: PaymentService,
) {

    @GetMapping
    fun listPayments(
        @RequestParam(required = false) orderId: UUID?,
    ): List<PaymentResponse> =
        paymentService
            .listPayments(orderId)
            .map { it.toResponse() }

    @GetMapping("/{id}")
    fun getPayment(
        @PathVariable id: UUID,
    ): PaymentResponse =
        paymentService
            .getPayment(id)
            .toResponse()

    @PostMapping
    fun registerPayment(
        @RequestBody request: RegisterPaymentRequest,
    ): PaymentResponse =
        paymentService
            .registerPayment(request.toCommand())
            .toResponse()

    private fun Payment.toResponse(): PaymentResponse =
        PaymentResponse(
            id = id,
            orderId = orderId,
            paymentGateway = paymentGateway,
            paymentMethod = paymentMethod.name,
            paymentAmount = paymentAmount.toBigDecimal(),
            remainingBalance = remainingBalance.toBigDecimal(),
            status = status.name,
            paymentKey = paymentKey,
            transactionId = transactionId,
            requestedAt = requestedAt,
            approvedAt = approvedAt,
            cancelledAt = cancelledAt,
        )

    private fun RegisterPaymentRequest.toCommand(): PaymentService.RegisterPaymentCommand =
        PaymentService.RegisterPaymentCommand(
            orderId = orderId,
            paymentGateway = paymentGateway,
            paymentMethod = parseMethod(paymentMethod),
            paymentAmount = Money.of(paymentAmount),
            remainingBalance = Money.of(remainingBalance),
            status = parseStatus(status),
            paymentKey = paymentKey,
            transactionId = transactionId,
            requestedAt = requestedAt,
            approvedAt = approvedAt,
            cancelledAt = cancelledAt,
            id = id,
        )

    private fun parseMethod(value: String): PaymentMethod =
        runCatching { PaymentMethod.valueOf(value.uppercase()) }
            .getOrElse {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported payment method: $value",
                )
            }

    private fun parseStatus(value: String): PaymentStatus =
        runCatching { PaymentStatus.valueOf(value.uppercase()) }
            .getOrElse {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported payment status: $value",
                )
            }
}

data class RegisterPaymentRequest(
    val orderId: UUID,
    val paymentGateway: String,
    val paymentMethod: String,
    val paymentAmount: BigDecimal,
    val remainingBalance: BigDecimal,
    val status: String,
    val paymentKey: String?,
    val transactionId: String?,
    val requestedAt: Instant,
    val approvedAt: Instant?,
    val cancelledAt: Instant?,
    val id: UUID? = null,
)

data class PaymentResponse(
    val id: UUID,
    val orderId: UUID,
    val paymentGateway: String,
    val paymentMethod: String,
    val paymentAmount: BigDecimal,
    val remainingBalance: BigDecimal,
    val status: String,
    val paymentKey: String?,
    val transactionId: String?,
    val requestedAt: Instant,
    val approvedAt: Instant?,
    val cancelledAt: Instant?,
)
