package io.joopang.services.order.infrastructure

import io.joopang.services.order.domain.OrderAggregate
import io.joopang.services.order.infrastructure.jpa.OrderEntity
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Repository
@Transactional(readOnly = true)
open class OrderRepository(
    @PersistenceContext private val entityManager: EntityManager,
) {

    open fun nextIdentity(): UUID = UUID.randomUUID()

    @Transactional
    open fun save(aggregate: OrderAggregate): OrderAggregate {
        val existing = entityManager.find(OrderEntity::class.java, aggregate.order.id)
        require(existing == null) { "Order with id ${aggregate.order.id} already exists" }
        return entityManager.merge(OrderEntity.fromAggregate(aggregate)).toAggregate()
    }

    open fun findById(orderId: UUID): OrderAggregate? =
        entityManager.find(OrderEntity::class.java, orderId)?.toAggregate()

    open fun findAll(): List<OrderAggregate> =
        entityManager.createQuery("select o from OrderEntity o order by o.orderedAt", OrderEntity::class.java)
            .resultList
            .map(OrderEntity::toAggregate)

    @Transactional
    open fun update(aggregate: OrderAggregate): OrderAggregate {
        val existing = entityManager.find(OrderEntity::class.java, aggregate.order.id)
            ?: throw IllegalArgumentException("Order with id ${aggregate.order.id} does not exist")
        existing.updateFrom(aggregate)
        return existing.toAggregate()
    }
}
