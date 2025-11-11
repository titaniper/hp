package io.joopang.services.order.infrastructure

import io.joopang.services.order.domain.Order
import io.joopang.services.order.domain.OrderAggregate
import io.joopang.services.order.domain.OrderDiscount
import io.joopang.services.order.domain.OrderItem
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
        val existing = entityManager.find(Order::class.java, aggregate.order.id)
        require(existing == null) { "Order with id ${aggregate.order.id} already exists" }

        entityManager.persist(aggregate.order)
        aggregate.items.forEach(entityManager::persist)
        aggregate.discounts.forEach(entityManager::persist)
        entityManager.flush()

        return aggregate
    }

    open fun findById(orderId: UUID): OrderAggregate? {
        val order = entityManager.find(Order::class.java, orderId) ?: return null
        return OrderAggregate(
            order = order,
            items = findItems(orderId),
            discounts = findDiscounts(orderId),
        )
    }

    open fun findAll(): List<OrderAggregate> =
        entityManager.createQuery("select o from Order o order by o.orderedAt", Order::class.java)
            .resultList
            .map { order ->
                OrderAggregate(
                    order = order,
                    items = findItems(order.id),
                    discounts = findDiscounts(order.id),
                )
            }

    @Transactional
    open fun update(aggregate: OrderAggregate): OrderAggregate {
        val existing = entityManager.find(Order::class.java, aggregate.order.id)
            ?: throw IllegalArgumentException("Order with id ${aggregate.order.id} does not exist")

        entityManager.merge(aggregate.order)
        deleteItemsByOrderId(existing.id)
        deleteDiscountsByOrderId(existing.id)
        aggregate.items.forEach(entityManager::persist)
        aggregate.discounts.forEach(entityManager::persist)
        entityManager.flush()

        return aggregate
    }

    private fun findItems(orderId: UUID): List<OrderItem> =
        entityManager.createQuery(
            "select i from OrderItem i where i.orderId = :orderId",
            OrderItem::class.java,
        )
            .setParameter("orderId", orderId)
            .resultList

    private fun findDiscounts(orderId: UUID): List<OrderDiscount> =
        entityManager.createQuery(
            "select d from OrderDiscount d where d.orderId = :orderId",
            OrderDiscount::class.java,
        )
            .setParameter("orderId", orderId)
            .resultList

    private fun deleteItemsByOrderId(orderId: UUID) {
        entityManager.createQuery("delete from OrderItem i where i.orderId = :orderId")
            .setParameter("orderId", orderId)
            .executeUpdate()
    }

    private fun deleteDiscountsByOrderId(orderId: UUID) {
        entityManager.createQuery("delete from OrderDiscount d where d.orderId = :orderId")
            .setParameter("orderId", orderId)
            .executeUpdate()
    }
}
