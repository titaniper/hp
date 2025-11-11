package io.joopang.services.delivery.infrastructure

import io.joopang.services.delivery.domain.Delivery
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Repository
@Transactional(readOnly = true)
open class DeliveryRepository(
    @PersistenceContext private val entityManager: EntityManager,
) {

    open fun findAll(): List<Delivery> =
        entityManager.createQuery("select d from Delivery d", Delivery::class.java)
            .resultList

    open fun findById(id: UUID): Delivery? =
        entityManager.find(Delivery::class.java, id)

    open fun findByOrderItemId(orderItemId: UUID): List<Delivery> =
        entityManager.createQuery(
            "select d from Delivery d where d.orderItemId = :orderItemId",
            Delivery::class.java,
        )
            .setParameter("orderItemId", orderItemId)
            .resultList

    @Transactional
    open fun save(delivery: Delivery): Delivery =
        entityManager.merge(delivery)
}
