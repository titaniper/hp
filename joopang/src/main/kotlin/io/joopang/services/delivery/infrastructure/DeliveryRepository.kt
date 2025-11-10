package io.joopang.services.delivery.infrastructure

import io.joopang.services.delivery.domain.Delivery
import io.joopang.services.delivery.infrastructure.jpa.DeliveryEntity
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
        entityManager.createQuery("select d from DeliveryEntity d", DeliveryEntity::class.java)
            .resultList
            .map(DeliveryEntity::toDomain)

    open fun findById(id: UUID): Delivery? =
        entityManager.find(DeliveryEntity::class.java, id)?.toDomain()

    open fun findByOrderItemId(orderItemId: UUID): List<Delivery> =
        entityManager.createQuery(
            "select d from DeliveryEntity d where d.orderItemId = :orderItemId",
            DeliveryEntity::class.java,
        )
            .setParameter("orderItemId", orderItemId)
            .resultList
            .map(DeliveryEntity::toDomain)

    @Transactional
    open fun save(delivery: Delivery): Delivery =
        entityManager.merge(DeliveryEntity.from(delivery)).toDomain()
}
