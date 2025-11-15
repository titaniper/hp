package io.joopang.services.payment.infrastructure

import io.joopang.services.payment.domain.Payment
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
@Transactional(readOnly = true)
open class PaymentRepository(
    @PersistenceContext private val entityManager: EntityManager,
) {

    open fun findAll(): List<Payment> =
        entityManager.createQuery("select p from Payment p", Payment::class.java)
            .resultList

    open fun findById(id: Long): Payment? =
        entityManager.find(Payment::class.java, id)

    open fun findByOrderId(orderId: Long): List<Payment> =
        entityManager.createQuery(
            "select p from Payment p where p.orderId = :orderId",
            Payment::class.java,
        )
            .setParameter("orderId", orderId)
            .resultList

    @Transactional
    open fun save(payment: Payment): Payment =
        entityManager.merge(payment)
}
