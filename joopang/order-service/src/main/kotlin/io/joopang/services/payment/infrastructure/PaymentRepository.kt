package io.joopang.services.payment.infrastructure

import io.joopang.services.payment.domain.Payment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PaymentRepository : JpaRepository<Payment, Long> {
    fun findAllByOrderId(orderId: Long): List<Payment>
}
