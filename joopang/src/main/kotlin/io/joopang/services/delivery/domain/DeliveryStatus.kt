package io.joopang.services.delivery.domain

enum class DeliveryStatus {
    PREPARING,
    IN_TRANSIT,
    OUT_FOR_DELIVERY,
    DELIVERED,
    DELIVERY_FAILED,
}
