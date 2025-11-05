package io.joopang.services.order.presentation

import io.joopang.services.order.application.OrderService
import io.joopang.services.order.domain.OrderAggregate
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

@RestController
@RequestMapping("/api/orders")
class OrderController(
    private val orderService: OrderService,
) {

    @PostMapping
    fun createOrder(
        @RequestBody request: CreateOrderRequest,
    ): OrderResponse =
        orderService
            .createOrder(request.toCommand())
            .toResponse()

    @GetMapping("/{orderId}")
    fun getOrder(
        @PathVariable orderId: UUID,
    ): OrderResponse =
        orderService
            .getOrder(orderId)
            .toResponse()

    @GetMapping
    fun listOrders(): List<OrderResponse> =
        orderService
            .listOrders()
            .map { it.toResponse() }

    @PostMapping("/{orderId}/payment")
    fun processPayment(
        @PathVariable orderId: UUID,
        @RequestBody request: ProcessPaymentRequest,
    ): PaymentResponse =
        orderService
            .processPayment(
                OrderService.ProcessPaymentCommand(
                    orderId = orderId,
                    userId = request.userId,
                ),
            )
            .toResponse()

    private fun CreateOrderRequest.toCommand(): OrderService.CreateOrderCommand =
        OrderService.CreateOrderCommand(
            userId = userId,
            recipientName = recipientName,
            items = items.map { it.toCommand() },
            couponId = couponId,
            memo = memo,
            imageUrl = imageUrl,
            zoneId = parseZoneId(zoneId),
        )

    private fun CreateOrderItemRequest.toCommand(): OrderService.CreateOrderItemCommand =
        OrderService.CreateOrderItemCommand(
            productId = productId,
            productItemId = productItemId,
            quantity = quantity,
        )

    private fun OrderAggregate.toResponse(): OrderResponse =
        OrderResponse(
            orderId = order.id,
            userId = order.userId,
            status = order.status.name,
            recipientName = order.recipientName,
            orderedAt = ISO_INSTANT.format(order.orderedAt),
            paidAt = order.paidAt?.let { ISO_INSTANT.format(it) },
            orderMonth = order.orderMonth.format(),
            totalAmount = order.totalAmount.toBigDecimal(),
            discountAmount = order.discountAmount.toBigDecimal(),
            payableAmount = order.payableAmount().toBigDecimal(),
            imageUrl = order.imageUrl,
            memo = order.memo,
            items = items.map { item ->
                OrderItemResponse(
                    orderItemId = item.id,
                    productId = item.productId,
                    productItemId = item.productItemId,
                    productName = item.productName,
                    quantity = item.quantity.value,
                    unitPrice = item.unitPrice.toBigDecimal(),
                    subtotal = item.subtotal.toBigDecimal(),
                )
            },
            discounts = discounts.map { discount ->
                OrderDiscountResponse(
                    discountId = discount.id,
                    type = discount.type.name,
                    referenceId = discount.referenceId,
                    amount = discount.price.toBigDecimal(),
                    couponId = discount.couponId,
                )
            },
        )

    private fun OrderService.PaymentResult.toResponse(): PaymentResponse =
        PaymentResponse(
            orderId = orderId,
            status = status.name,
            paidAmount = paidAmount.toBigDecimal(),
            remainingBalance = remainingBalance.toBigDecimal(),
            paidAt = ISO_INSTANT.format(paidAt),
        )

    private fun parseZoneId(value: String?): ZoneId? =
        value?.let {
            runCatching { ZoneId.of(it) }
                .getOrElse { ex ->
                    throw ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Unsupported zone id: $value",
                        ex,
                    )
                }
        }

    companion object {
        private val ISO_INSTANT: DateTimeFormatter = DateTimeFormatter.ISO_INSTANT
    }
}

data class CreateOrderRequest(
    val userId: UUID,
    val recipientName: String,
    val items: List<CreateOrderItemRequest>,
    val couponId: UUID?,
    val memo: String?,
    val imageUrl: String?,
    val zoneId: String?,
)

data class CreateOrderItemRequest(
    val productId: UUID,
    val productItemId: UUID?,
    val quantity: Int,
)

data class OrderResponse(
    val orderId: UUID,
    val userId: UUID,
    val status: String,
    val recipientName: String,
    val orderedAt: String,
    val paidAt: String?,
    val orderMonth: String,
    val totalAmount: BigDecimal,
    val discountAmount: BigDecimal,
    val payableAmount: BigDecimal,
    val imageUrl: String?,
    val memo: String?,
    val items: List<OrderItemResponse>,
    val discounts: List<OrderDiscountResponse>,
)

data class OrderItemResponse(
    val orderItemId: UUID,
    val productId: UUID?,
    val productItemId: UUID?,
    val productName: String,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val subtotal: BigDecimal,
)

data class OrderDiscountResponse(
    val discountId: UUID,
    val type: String,
    val referenceId: UUID?,
    val amount: BigDecimal,
    val couponId: UUID?,
)

data class ProcessPaymentRequest(
    val userId: UUID,
)

data class PaymentResponse(
    val orderId: UUID,
    val status: String,
    val paidAmount: BigDecimal,
    val remainingBalance: BigDecimal,
    val paidAt: String,
)
