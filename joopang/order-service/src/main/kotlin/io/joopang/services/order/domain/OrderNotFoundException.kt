package io.joopang.services.order.domain

class OrderNotFoundException(orderId: String) :
    RuntimeException("Order with id $orderId was not found")
