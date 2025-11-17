package io.joopang.services.delivery.infrastructure

import io.joopang.services.delivery.domain.Delivery
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Repository

@Repository
class DeliveryRepository(
    @PersistenceContext private val entityManager: EntityManager,
) {

    fun findAll(): List<Delivery> =
        entityManager.createQuery("select d from Delivery d", Delivery::class.java)
            .resultList

    fun findById(id: Long): Delivery? =
        entityManager.find(Delivery::class.java, id)

    fun findByOrderItemId(orderItemId: Long): List<Delivery> =
        entityManager.createQuery(
            "select d from Delivery d where d.orderItemId = :orderItemId",
            Delivery::class.java,
        )
            .setParameter("orderItemId", orderItemId)
            .resultList

    fun save(delivery: Delivery): Delivery =
        entityManager.merge(delivery)
}
