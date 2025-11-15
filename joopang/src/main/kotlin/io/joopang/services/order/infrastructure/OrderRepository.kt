package io.joopang.services.order.infrastructure

import io.joopang.services.order.domain.Order
import io.joopang.services.order.domain.OrderAggregate
import io.joopang.services.order.domain.OrderDiscount
import io.joopang.services.order.domain.OrderItem
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
@Transactional(readOnly = true)
open class OrderRepository(
    @PersistenceContext private val entityManager: EntityManager,
) {

    @Transactional
    open fun save(aggregate: OrderAggregate): OrderAggregate {
        val order = aggregate.order
        require(order.id == 0L) { "Order already has an id ${order.id}" }

        entityManager.persist(order)
        entityManager.flush()

        val orderId = order.id
        aggregate.items.forEach { item ->
            item.orderId = orderId
            entityManager.persist(item)
        }
        aggregate.discounts.forEach { discount ->
            discount.orderId = orderId
            entityManager.persist(discount)
        }
        entityManager.flush()

        return aggregate
    }

    open fun findById(orderId: Long): OrderAggregate? {
        val order = entityManager.find(Order::class.java, orderId) ?: return null
        val id = order.id
        return OrderAggregate(
            order = order,
            items = findItems(id),
            discounts = findDiscounts(id),
        )
    }

    open fun findAll(): List<OrderAggregate> {
        val orders = entityManager.createQuery(
            "select o from Order o order by o.orderedAt",
            Order::class.java,
        )
            .resultList
        if (orders.isEmpty()) {
            return emptyList()
        }

        val orderIds = orders.map { it.id }
        val itemsByOrderId = findItemsByOrderIds(orderIds)
        val discountsByOrderId = findDiscountsByOrderIds(orderIds)

        return orders.map { order ->
            OrderAggregate(
                order = order,
                items = itemsByOrderId[order.id].orEmpty(),
                discounts = discountsByOrderId[order.id].orEmpty(),
            )
        }
    }

    @Transactional
    open fun update(aggregate: OrderAggregate): OrderAggregate {
        val existing = entityManager.find(Order::class.java, aggregate.order.id)
            ?: throw IllegalArgumentException("Order with id ${aggregate.order.id} does not exist")

        entityManager.merge(aggregate.order)
        val orderId = existing.id
        deleteItemsByOrderId(orderId)
        deleteDiscountsByOrderId(orderId)
        aggregate.items.forEach { item ->
            item.orderId = orderId
            entityManager.persist(item)
        }
        aggregate.discounts.forEach { discount ->
            discount.orderId = orderId
            entityManager.persist(discount)
        }
        entityManager.flush()

        return aggregate
    }

    private fun findItems(orderId: Long): List<OrderItem> =
        findItemsByOrderIds(listOf(orderId))[orderId].orEmpty()

    private fun findDiscounts(orderId: Long): List<OrderDiscount> =
        findDiscountsByOrderIds(listOf(orderId))[orderId].orEmpty()

    private fun findItemsByOrderIds(orderIds: List<Long>): Map<Long, List<OrderItem>> {
        if (orderIds.isEmpty()) {
            return emptyMap()
        }

        return entityManager.createQuery(
            "select i from OrderItem i where i.orderId in :orderIds",
            OrderItem::class.java,
        )
            .setParameter("orderIds", orderIds)
            .resultList
            .groupBy { it.orderId as Long }
    }

    private fun findDiscountsByOrderIds(orderIds: List<Long>): Map<Long, List<OrderDiscount>> {
        if (orderIds.isEmpty()) {
            return emptyMap()
        }

        return entityManager.createQuery(
            "select d from OrderDiscount d where d.orderId in :orderIds",
            OrderDiscount::class.java,
        )
            .setParameter("orderIds", orderIds)
            .resultList
            .groupBy { it.orderId as Long }
    }

    private fun deleteItemsByOrderId(orderId: Long) {
        entityManager.createQuery("delete from OrderItem i where i.orderId = :orderId")
            .setParameter("orderId", orderId)
            .executeUpdate()
    }

    private fun deleteDiscountsByOrderId(orderId: Long) {
        entityManager.createQuery("delete from OrderDiscount d where d.orderId = :orderId")
            .setParameter("orderId", orderId)
            .executeUpdate()
    }
}
