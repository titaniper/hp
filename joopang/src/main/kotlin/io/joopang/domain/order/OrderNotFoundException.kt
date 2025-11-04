package io.joopang.domain.order

class OrderNotFoundException(orderId: String) :
    RuntimeException("Order with id $orderId was not found")
