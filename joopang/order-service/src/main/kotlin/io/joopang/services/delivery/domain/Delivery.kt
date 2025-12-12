package io.joopang.services.delivery.domain

import io.joopang.services.common.domain.Address
import io.joopang.services.common.domain.BaseEntity
import io.joopang.services.common.domain.Money
import io.joopang.services.common.domain.PhoneNumber
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(
    name = "deliveries",
    indexes = [
        Index(
            name = "idx_deliveries_order_item_id",
            columnList = "order_item_id",
        ),
    ],
)
class Delivery(
    id: Long? = null,
    @Column(name = "order_item_id", columnDefinition = "BIGINT", nullable = false)
    var orderItemId: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var type: DeliveryType = DeliveryType.DIRECT_DELIVERY,

    @Embedded
    var address: Address = Address(),

    @Column(name = "receiver_tel", nullable = false, length = 32)
    var receiverTel: PhoneNumber = PhoneNumber("0000000000"),

    @Column(name = "estimated_delivery_date")
    var estimatedDeliveryDate: LocalDate? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: DeliveryStatus = DeliveryStatus.PREPARING,

    @Column(name = "tracking_number")
    var trackingNumber: String? = null,

    @Column(name = "delivery_fee", precision = 19, scale = 2, nullable = false)
    var deliveryFee: Money = Money.ZERO,
) : BaseEntity(id) {

    init {
        require(deliveryFee >= Money.ZERO) { "Delivery fee cannot be negative" }
    }

    @Suppress("unused")
    constructor() : this(
        id = null,
        orderItemId = 0,
        type = DeliveryType.DIRECT_DELIVERY,
        address = Address(),
        receiverTel = PhoneNumber("0000000000"),
        estimatedDeliveryDate = null,
        status = DeliveryStatus.PREPARING,
        trackingNumber = null,
        deliveryFee = Money.ZERO,
    )
}
