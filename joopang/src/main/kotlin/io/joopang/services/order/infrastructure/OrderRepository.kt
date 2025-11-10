package io.joopang.services.order.infrastructure

import io.joopang.services.order.domain.OrderAggregate
import org.springframework.stereotype.Repository
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Repository
open class OrderRepository {

    private val store = ConcurrentHashMap<UUID, OrderAggregate>()

    open fun nextIdentity(): UUID = UUID.randomUUID()

    open fun save(aggregate: OrderAggregate): OrderAggregate {
        val previous = store.putIfAbsent(aggregate.order.id, aggregate)
        require(previous == null) { "Order with id ${aggregate.order.id} already exists" }
        return aggregate
    }

    open fun findById(orderId: UUID): OrderAggregate? = store[orderId]

    open fun findAll(): List<OrderAggregate> =
        store.values.sortedBy { it.order.orderedAt }

    open fun update(aggregate: OrderAggregate): OrderAggregate {
        require(store.containsKey(aggregate.order.id)) {
            "Order with id ${aggregate.order.id} does not exist"
        }
        store[aggregate.order.id] = aggregate
        return aggregate
    }
}
