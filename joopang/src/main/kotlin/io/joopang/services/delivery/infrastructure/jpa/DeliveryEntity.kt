package io.joopang.services.delivery.infrastructure.jpa

import io.joopang.persistence.embeddable.AddressEmbeddable
import io.joopang.services.common.domain.Money
import io.joopang.services.common.domain.PhoneNumber
import io.joopang.services.delivery.domain.Delivery
import io.joopang.services.delivery.domain.DeliveryStatus
import io.joopang.services.delivery.domain.DeliveryType
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "deliveries")
class DeliveryEntity(
    @Id
    @Column(columnDefinition = "BINARY(16)")
    var id: UUID,

    @Column(name = "order_item_id", columnDefinition = "BINARY(16)", nullable = false)
    var orderItemId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var type: DeliveryType,

    @Embedded
    var address: AddressEmbeddable,

    @Column(name = "receiver_tel", nullable = false, length = 32)
    var receiverTel: PhoneNumber,

    @Column(name = "estimated_delivery_date")
    var estimatedDeliveryDate: LocalDate?,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: DeliveryStatus,

    @Column(name = "tracking_number")
    var trackingNumber: String?,

    @Column(name = "delivery_fee", precision = 19, scale = 2, nullable = false)
    var deliveryFee: Money,
) {
    fun toDomain(): Delivery = Delivery(
        id = id,
        orderItemId = orderItemId,
        type = type,
        address = address.toDomain(),
        receiverTel = receiverTel,
        estimatedDeliveryDate = estimatedDeliveryDate,
        status = status,
        trackingNumber = trackingNumber,
        deliveryFee = deliveryFee,
    )

    companion object {
        fun from(domain: Delivery): DeliveryEntity = DeliveryEntity(
            id = domain.id,
            orderItemId = domain.orderItemId,
            type = domain.type,
            address = AddressEmbeddable.from(domain.address),
            receiverTel = domain.receiverTel,
            estimatedDeliveryDate = domain.estimatedDeliveryDate,
            status = domain.status,
            trackingNumber = domain.trackingNumber,
            deliveryFee = domain.deliveryFee,
        )
    }
}
