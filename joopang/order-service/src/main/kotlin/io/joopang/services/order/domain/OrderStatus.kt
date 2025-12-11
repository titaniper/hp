package io.joopang.services.order.domain

enum class OrderStatus {
    PENDING,
    PAID,
    SHIPPING,
    DELIVERED,
    CANCELED,
    REFUNDED,
}
