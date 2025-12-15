package io.joopang.services.delivery.presentation

import io.joopang.services.common.domain.Address
import io.joopang.services.common.domain.Money
import io.joopang.services.common.domain.PhoneNumber
import io.joopang.services.delivery.application.DeliveryService
import io.joopang.services.delivery.domain.DeliveryStatus
import io.joopang.services.delivery.domain.DeliveryType
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.time.LocalDate

@RestController
@RequestMapping("/api/deliveries")
class DeliveryController(
    private val deliveryService: DeliveryService,
) {

    @GetMapping
    fun listDeliveries(
        @RequestParam(required = false) orderItemId: Long?,
    ): List<DeliveryResponse> =
        deliveryService
            .listDeliveries(orderItemId)
            .map { it.toResponse() }

    @GetMapping("/{id}")
    fun getDelivery(
        @PathVariable id: Long,
    ): DeliveryResponse =
        deliveryService
            .getDelivery(id)
            .toResponse()

    @PostMapping
    fun registerDelivery(
        @RequestBody request: RegisterDeliveryRequest,
    ): DeliveryResponse =
        deliveryService
            .registerDelivery(request.toCommand())
            .toResponse()

    private fun DeliveryService.Output.toResponse(): DeliveryResponse =
        DeliveryResponse(
            id = id,
            orderItemId = orderItemId,
            type = type.name,
            address = DeliveryAddressResponse(
                zipCode = address.zipCode,
                baseAddress = address.baseAddress,
                detailAddress = address.detailAddress,
            ),
            receiverTel = receiverTel.value,
            estimatedDeliveryDate = estimatedDeliveryDate,
            status = status.name,
            trackingNumber = trackingNumber,
            deliveryFee = deliveryFee.toBigDecimal(),
        )

    private fun RegisterDeliveryRequest.toCommand(): DeliveryService.RegisterDeliveryCommand =
        DeliveryService.RegisterDeliveryCommand(
            orderItemId = orderItemId,
            type = parseType(type),
            address = Address(
                zipCode = address.zipCode,
                baseAddress = address.baseAddress,
                detailAddress = address.detailAddress,
            ),
            receiverTel = PhoneNumber(receiverTel),
            estimatedDeliveryDate = estimatedDeliveryDate,
            status = parseStatus(status),
            trackingNumber = trackingNumber,
            deliveryFee = deliveryFee?.let { Money.of(it) },
            id = id,
        )

    private fun parseType(value: String): DeliveryType =
        runCatching { DeliveryType.valueOf(value.uppercase()) }
            .getOrElse {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported delivery type: $value",
                )
            }

    private fun parseStatus(value: String): DeliveryStatus =
        runCatching { DeliveryStatus.valueOf(value.uppercase()) }
            .getOrElse {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported delivery status: $value",
                )
            }
}

data class RegisterDeliveryRequest(
    val orderItemId: Long,
    val type: String,
    val address: DeliveryAddressRequest,
    val receiverTel: String,
    val estimatedDeliveryDate: LocalDate?,
    val status: String,
    val trackingNumber: String?,
    val deliveryFee: BigDecimal?,
    val id: Long? = null,
)

data class DeliveryAddressRequest(
    val zipCode: String,
    val baseAddress: String,
    val detailAddress: String?,
)

data class DeliveryResponse(
    val id: Long,
    val orderItemId: Long,
    val type: String,
    val address: DeliveryAddressResponse,
    val receiverTel: String,
    val estimatedDeliveryDate: LocalDate?,
    val status: String,
    val trackingNumber: String?,
    val deliveryFee: BigDecimal,
)

data class DeliveryAddressResponse(
    val zipCode: String,
    val baseAddress: String,
    val detailAddress: String?,
)
