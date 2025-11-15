package io.joopang.services.payment.infrastructure

import io.joopang.services.payment.domain.Payment
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Repository

@Repository
class PaymentRepository(
    @PersistenceContext private val entityManager: EntityManager,
) {

    fun findAll(): List<Payment> =
        entityManager.createQuery("select p from Payment p", Payment::class.java)
            .resultList

    fun findById(id: Long): Payment? =
        entityManager.find(Payment::class.java, id)

    fun findByOrderId(orderId: Long): List<Payment> =
        entityManager.createQuery(
            "select p from Payment p where p.orderId = :orderId",
            Payment::class.java,
        )
            .setParameter("orderId", orderId)
            .resultList

    fun save(payment: Payment): Payment =
        entityManager.merge(payment)
}
