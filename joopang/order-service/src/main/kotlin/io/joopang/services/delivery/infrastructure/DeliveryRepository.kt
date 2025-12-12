package io.joopang.services.delivery.infrastructure

import io.joopang.services.delivery.domain.Delivery
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface DeliveryRepository : JpaRepository<Delivery, Long> {
    fun findByOrderItemId(orderItemId: Long): List<Delivery>
}
