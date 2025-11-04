package io.joopang.infrastructure.order

import io.joopang.domain.order.OrderAggregate
import io.joopang.domain.order.OrderRepository
import org.springframework.stereotype.Repository
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Repository
class OrderRepositoryImpl : OrderRepository {

    private val store = ConcurrentHashMap<UUID, OrderAggregate>()

    override fun nextIdentity(): UUID = UUID.randomUUID()

    override fun save(aggregate: OrderAggregate): OrderAggregate {
        val previous = store.putIfAbsent(aggregate.order.id, aggregate)
        require(previous == null) { "Order with id ${aggregate.order.id} already exists" }
        return aggregate
    }

    override fun findById(orderId: UUID): OrderAggregate? = store[orderId]

    override fun findAll(): List<OrderAggregate> =
        store.values.sortedBy { it.order.orderedAt }

    override fun update(aggregate: OrderAggregate): OrderAggregate {
        require(store.containsKey(aggregate.order.id)) {
            "Order with id ${aggregate.order.id} does not exist"
        }
        store[aggregate.order.id] = aggregate
        return aggregate
    }
}
