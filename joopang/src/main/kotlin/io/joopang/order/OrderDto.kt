package io.joopang.api.order

import java.math.BigDecimal
import java.util.UUID

data class CreateOrderRequestDto(
    val userId: UUID,
    val items: List<CreateOrderItemRequestDto>,
    val couponId: UUID?,
)

data class CreateOrderItemRequestDto(
    val productId: UUID,
    val productItemId: UUID?,
    val quantity: Int,
)

data class OrderResponseDto(
    val orderId: UUID,
    val userId: UUID,
    val status: OrderStatusDto,
    val orderedAt: String,
    val orderMonth: String,
    val items: List<OrderItemResponseDto>,
    val subtotalAmount: BigDecimal,
    val discountAmount: BigDecimal,
    val totalAmount: BigDecimal,
    val discounts: List<OrderDiscountResponseDto>,
)

data class OrderItemResponseDto(
    val orderItemId: UUID,
    val productId: UUID?,
    val productItemId: UUID?,
    val productName: String,
    val unitAmount: BigDecimal,
    val priceAmount: BigDecimal,
    val discountAmount: BigDecimal,
    val quantity: Int,
    val subtotalAmount: BigDecimal,
)

enum class OrderStatusDto {
    PENDING,
    PAID,
    SHIPPING,
    DELIVERED,
    CANCELED,
    REFUNDED,
}

data class OrderDiscountResponseDto(
    val discountId: UUID,
    val type: OrderDiscountTypeDto,
    val referenceId: UUID?,
    val amount: BigDecimal,
    val couponId: UUID?,
)

enum class OrderDiscountTypeDto {
    POINT,
    COUPON,
}

data class PaymentRequestDto(
    val userId: UUID,
)

data class PaymentResponseDto(
    val paymentId: UUID,
    val orderId: UUID,
    val paymentGateway: String,
    val paymentMethod: PaymentMethodDto,
    val paymentAmount: BigDecimal,
    val remainingBalance: BigDecimal,
    val status: PaymentStatusDto,
    val paymentKey: String?,
    val transactionId: String?,
    val requestedAt: String,
    val approvedAt: String?,
    val cancelledAt: String?,
)

enum class PaymentStatusDto {
    REQUESTED,
    APPROVED,
    FAILED,
    CANCELED,
}

enum class PaymentMethodDto {
    CARD,
    BANK_TRANSFER,
    MOBILE,
}
