package io.joopang.services.delivery.domain

import io.joopang.services.common.domain.Address
import io.joopang.services.common.domain.Money
import io.joopang.services.common.domain.PhoneNumber
import java.time.LocalDate
import java.util.UUID

data class Delivery(
    val id: UUID,
    val orderItemId: UUID,
    val type: DeliveryType,
    val address: Address,
    val receiverTel: PhoneNumber,
    val estimatedDeliveryDate: LocalDate?,
    val status: DeliveryStatus,
    val trackingNumber: String?,
    val deliveryFee: Money = Money.ZERO,
) {

    init {
        require(deliveryFee >= Money.ZERO) { "Delivery fee cannot be negative" }
    }
}
