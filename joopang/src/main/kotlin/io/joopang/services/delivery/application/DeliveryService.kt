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

    fun listDeliveries(orderItemId: UUID?): List<Output> =
        if (orderItemId == null) {
            deliveryRepository.findAll()
        } else {
            deliveryRepository.findByOrderItemId(orderItemId)
        }.map { it.toOutput() }

    fun getDelivery(id: UUID): Output =
        deliveryRepository.findById(id)
            ?.toOutput()
            ?: throw DeliveryNotFoundException(id.toString())

    fun registerDelivery(command: RegisterDeliveryCommand): Output {
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
        return deliveryRepository.save(delivery).toOutput()
    }

    private fun Delivery.toOutput(): Output =
        Output(
            id = id,
            orderItemId = orderItemId,
            type = type,
            address = address,
            receiverTel = receiverTel,
            estimatedDeliveryDate = estimatedDeliveryDate,
            status = status,
            trackingNumber = trackingNumber,
            deliveryFee = deliveryFee,
        )

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

    data class Output(
        val id: UUID,
        val orderItemId: UUID,
        val type: DeliveryType,
        val address: Address,
        val receiverTel: PhoneNumber,
        val estimatedDeliveryDate: LocalDate?,
        val status: DeliveryStatus,
        val trackingNumber: String?,
        val deliveryFee: Money,
    )
}
