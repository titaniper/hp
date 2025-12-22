package io.joopang.services.order.infrastructure.outbox

import io.joopang.services.order.domain.outbox.OrderOutboxEvent
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface OrderOutboxEventRepository : JpaRepository<OrderOutboxEvent, Long>
