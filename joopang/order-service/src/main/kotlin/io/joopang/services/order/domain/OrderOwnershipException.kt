package io.joopang.services.order.domain

class OrderOwnershipException(
    orderId: String,
    userId: String,
) : RuntimeException("Order $orderId does not belong to user $userId")
