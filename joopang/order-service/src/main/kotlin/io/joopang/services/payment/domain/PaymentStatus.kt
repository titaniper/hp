package io.joopang.services.payment.domain

enum class PaymentStatus {
    PENDING,
    COMPLETED,
    FAILED,
    CANCELLED,
    REFUNDED,
    PARTIAL_REFUNDED,
}
