package io.joopang.services.payment.domain

class PaymentNotFoundException(paymentId: String) : RuntimeException("Payment $paymentId not found")
