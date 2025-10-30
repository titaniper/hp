package io.joopang.api.order

import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.util.UUID

@RestController
@RequestMapping("/api/orders")
class OrderController {
    @PostMapping
    fun createOrder(@RequestBody request: CreateOrderRequestDto): OrderResponseDto =
        MOCK_ORDER_RESPONSE.copy(
            userId = request.userId,
        )

    @PostMapping("/{orderId}/payment")
    fun processPayment(
        @PathVariable orderId: UUID,
        @RequestBody body: PaymentRequestDto,
    ): PaymentResponseDto = MOCK_PAYMENT_RESPONSE.copy(
        paymentId = UUID.randomUUID(),
        orderId = orderId,
    )
}

private val MOCK_ORDER_ID: UUID = UUID.fromString("99999999-9999-9999-9999-999999999999")

private val MOCK_ORDER_RESPONSE = OrderResponseDto(
    orderId = MOCK_ORDER_ID,
    userId = UUID.fromString("aaaaaaaa-1111-2222-3333-444444444444"),
    status = OrderStatusDto.PAID,
    orderedAt = "2024-03-15T14:23:00Z",
    orderMonth = "2024-03",
    items = listOf(
        OrderItemResponseDto(
            orderItemId = UUID.fromString("12121212-3434-4545-5656-787878787878"),
            productId = UUID.fromString("11111111-1111-1111-1111-111111111111"),
            productItemId = UUID.fromString("22222222-2222-2222-2222-222222222222"),
            productName = "프리미엄 드립 커피 세트",
            unitAmount = BigDecimal("39800"),
            priceAmount = BigDecimal("39800"),
            discountAmount = BigDecimal.ZERO,
            quantity = 1,
            subtotalAmount = BigDecimal("39800"),
        ),
        OrderItemResponseDto(
            orderItemId = UUID.fromString("89898989-4545-5656-6767-989898989898"),
            productId = UUID.fromString("44444444-4444-4444-4444-444444444444"),
            productItemId = UUID.fromString("55555555-5555-5555-5555-555555555555"),
            productName = "에센셜 핸드크림 3종 세트",
            unitAmount = BigDecimal("25900"),
            priceAmount = BigDecimal("51800"),
            discountAmount = BigDecimal("4600"),
            quantity = 2,
            subtotalAmount = BigDecimal("47200"),
        ),
    ),
    subtotalAmount = BigDecimal("91600"),
    discountAmount = BigDecimal("4600"),
    totalAmount = BigDecimal("87000"),
    discounts = listOf(
        OrderDiscountResponseDto(
            discountId = UUID.fromString("abababab-abab-abab-abab-abababababab"),
            type = OrderDiscountTypeDto.COUPON,
            referenceId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
            amount = BigDecimal("4600"),
            couponId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
        ),
    ),
)

private val MOCK_PAYMENT_RESPONSE = PaymentResponseDto(
    paymentId = UUID.fromString("c1c1c1c1-d2d2-4444-8888-eeeeeeeeeeee"),
    orderId = MOCK_ORDER_ID,
    paymentGateway = "TOSS_PAYMENTS",
    paymentMethod = PaymentMethodDto.CARD,
    paymentAmount = BigDecimal("87000"),
    remainingBalance = BigDecimal.ZERO,
    status = PaymentStatusDto.APPROVED,
    paymentKey = "pay_test_20240315_001",
    transactionId = "tx_20240315_001",
    requestedAt = "2024-03-15T14:24:30Z",
    approvedAt = "2024-03-15T14:24:45Z",
    cancelledAt = null,
)
