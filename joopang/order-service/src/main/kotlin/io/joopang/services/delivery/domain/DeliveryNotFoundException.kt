package io.joopang.services.delivery.domain

class DeliveryNotFoundException(deliveryId: String) : RuntimeException("Delivery $deliveryId not found")
