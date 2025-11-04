package io.joopang.domain.order

import java.util.UUID

interface OrderRepository {
    fun nextIdentity(): UUID
    fun save(aggregate: OrderAggregate): OrderAggregate
    fun findById(orderId: UUID): OrderAggregate?
    fun findAll(): List<OrderAggregate>
    fun update(aggregate: OrderAggregate): OrderAggregate
}
