package io.joopang.services.delivery.application

import io.joopang.services.common.domain.Address
import io.joopang.services.common.domain.Money
import io.joopang.services.common.domain.PhoneNumber
import io.joopang.services.delivery.domain.Delivery
import io.joopang.services.delivery.domain.DeliveryNotFoundException
import io.joopang.services.delivery.domain.DeliveryStatus
import io.joopang.services.delivery.domain.DeliveryType
import io.joopang.services.delivery.infrastructure.DeliveryRepository
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.UUID

@Service
class DeliveryService(
    private val deliveryRepository: DeliveryRepository,
) {

    fun listDeliveries(orderItemId: UUID?): List<Delivery> =
        if (orderItemId == null) {
            deliveryRepository.findAll()
        } else {
            deliveryRepository.findByOrderItemId(orderItemId)
        }

    fun getDelivery(id: UUID): Delivery =
        deliveryRepository.findById(id)
            ?: throw DeliveryNotFoundException(id.toString())

    fun registerDelivery(command: RegisterDeliveryCommand): Delivery {
        val delivery = Delivery(
            id = command.id ?: UUID.randomUUID(),
            orderItemId = command.orderItemId,
            type = command.type,
            address = command.address,
            receiverTel = command.receiverTel,
            estimatedDeliveryDate = command.estimatedDeliveryDate,
            status = command.status,
            trackingNumber = command.trackingNumber,
            deliveryFee = command.deliveryFee ?: Money.ZERO,
        )
        return deliveryRepository.save(delivery)
    }

    data class RegisterDeliveryCommand(
        val orderItemId: UUID,
        val type: DeliveryType,
        val address: Address,
        val receiverTel: PhoneNumber,
        val estimatedDeliveryDate: LocalDate?,
        val status: DeliveryStatus,
        val trackingNumber: String?,
        val deliveryFee: Money?,
        val id: UUID? = null,
    )
}
