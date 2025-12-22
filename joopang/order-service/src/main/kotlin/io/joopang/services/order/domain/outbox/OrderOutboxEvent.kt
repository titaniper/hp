package io.joopang.services.order.domain.outbox

import io.joopang.services.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Lob
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(
    name = "order_outbox_events",
    indexes = [
        Index(name = "idx_order_outbox_events_status_created_at", columnList = "status, created_at")
    ],
)
class OrderOutboxEvent(
    @Column(name = "aggregate_type", nullable = false, length = 64)
    val aggregateType: String = "",

    @Column(name = "aggregate_id", nullable = false, length = 191)
    val aggregateId: String = "",

    @Column(name = "event_type", nullable = false, length = 128)
    val eventType: String = "",

    @Lob
    @Column(name = "payload", nullable = false, columnDefinition = "LONGTEXT")
    val payload: String = "",

    @Column(name = "occurred_at", nullable = false, columnDefinition = "DATETIME(6)")
    val occurredAt: Instant = Instant.EPOCH,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: OrderOutboxEventStatus = OrderOutboxEventStatus.PENDING,

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
    var createdAt: Instant = Instant.now(),

    @Column(name = "published_at", columnDefinition = "DATETIME(6)")
    var publishedAt: Instant? = null,

    @Column(name = "last_error", columnDefinition = "TEXT")
    var lastError: String? = null,

    @Column(name = "retry_count", nullable = false)
    var retryCount: Int = 0,
    id: Long? = null,
) : BaseEntity(id)

enum class OrderOutboxAggregateType(val value: String) {
    ORDER("order"),
}

enum class OrderOutboxEventType(val value: String) {
    ORDER_PAID("order-paid"),
}

enum class OrderOutboxEventStatus {
    PENDING,
    PUBLISHED,
    FAILED,
}
