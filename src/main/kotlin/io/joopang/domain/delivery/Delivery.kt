package io.joopang.domain.delivery

import io.joopang.domain.common.Address
import io.joopang.domain.common.Money
import io.joopang.domain.common.PhoneNumber
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
