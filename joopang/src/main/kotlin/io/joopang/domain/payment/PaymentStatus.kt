package io.joopang.domain.payment

enum class PaymentStatus {
    PENDING,
    COMPLETED,
    FAILED,
    CANCELLED,
    REFUNDED,
    PARTIAL_REFUNDED,
}
