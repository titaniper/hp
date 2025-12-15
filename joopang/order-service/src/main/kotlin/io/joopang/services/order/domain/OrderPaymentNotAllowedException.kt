package io.joopang.services.order.domain

class OrderPaymentNotAllowedException(
    orderId: String,
    status: OrderStatus,
) : RuntimeException("Order $orderId cannot be paid while in status $status")
