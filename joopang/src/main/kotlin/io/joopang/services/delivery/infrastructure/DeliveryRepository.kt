package io.joopang.services.delivery.infrastructure

import io.joopang.services.common.domain.Address
import io.joopang.services.common.domain.Money
import io.joopang.services.common.domain.PhoneNumber
import io.joopang.services.delivery.domain.Delivery
import io.joopang.services.delivery.domain.DeliveryStatus
import io.joopang.services.delivery.domain.DeliveryType
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Repository
open class DeliveryRepository {

    private val store = ConcurrentHashMap<UUID, Delivery>()

    init {
        seed()
    }

    open fun findAll(): List<Delivery> = store.values.toList()

    open fun findById(id: UUID): Delivery? = store[id]

    open fun findByOrderItemId(orderItemId: UUID): List<Delivery> =
        store.values.filter { it.orderItemId == orderItemId }

    open fun save(delivery: Delivery): Delivery {
        store[delivery.id] = delivery
        return delivery
    }

    private fun seed() {
        val deliveryId = UUID.fromString("66666666-7777-8888-9999-000000000000")
        val orderItemId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")

        store[deliveryId] = Delivery(
            id = deliveryId,
            orderItemId = orderItemId,
            type = DeliveryType.DIRECT_DELIVERY,
            address = Address(
                zipCode = "06000",
                baseAddress = "Seoul, Gangnam-gu, Teheran-ro 152",
                detailAddress = "12F",
            ),
            receiverTel = PhoneNumber("010-1234-5678"),
            estimatedDeliveryDate = LocalDate.now().plusDays(3),
            status = DeliveryStatus.IN_TRANSIT,
            trackingNumber = "JP123456789KR",
            deliveryFee = Money.of(3000L),
        )
    }
}
