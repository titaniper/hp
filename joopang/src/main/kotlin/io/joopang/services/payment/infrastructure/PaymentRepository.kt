package io.joopang.services.payment.infrastructure

import io.joopang.services.payment.domain.Payment
import io.joopang.services.payment.infrastructure.jpa.PaymentEntity
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Repository
@Transactional(readOnly = true)
open class PaymentRepository(
    @PersistenceContext private val entityManager: EntityManager,
) {

    open fun findAll(): List<Payment> =
        entityManager.createQuery("select p from PaymentEntity p", PaymentEntity::class.java)
            .resultList
            .map(PaymentEntity::toDomain)

    open fun findById(id: UUID): Payment? =
        entityManager.find(PaymentEntity::class.java, id)?.toDomain()

    open fun findByOrderId(orderId: UUID): List<Payment> =
        entityManager.createQuery(
            "select p from PaymentEntity p where p.orderId = :orderId",
            PaymentEntity::class.java,
        )
            .setParameter("orderId", orderId)
            .resultList
            .map(PaymentEntity::toDomain)

    @Transactional
    open fun save(payment: Payment): Payment =
        entityManager.merge(PaymentEntity.from(payment)).toDomain()
}
