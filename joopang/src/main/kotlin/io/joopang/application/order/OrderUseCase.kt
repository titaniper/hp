package io.joopang.application.order

import io.joopang.domain.common.Money
import io.joopang.domain.common.OrderMonth
import io.joopang.domain.common.Quantity
import io.joopang.domain.coupon.Coupon
import io.joopang.domain.coupon.CouponNotFoundException
import io.joopang.domain.coupon.CouponType
import io.joopang.domain.coupon.InvalidCouponException
import io.joopang.domain.order.Order
import io.joopang.domain.order.OrderAggregate
import io.joopang.domain.order.OrderDiscount
import io.joopang.domain.order.OrderDiscountType
import io.joopang.domain.order.OrderNotFoundException
import io.joopang.domain.order.OrderStatus
import io.joopang.domain.product.InsufficientStockException
import io.joopang.domain.product.ProductItemNotFoundException
import io.joopang.domain.product.ProductNotFoundException
import io.joopang.domain.product.ProductWithItems
import io.joopang.domain.product.StockQuantity
import io.joopang.domain.user.UserNotFoundException
import io.joopang.infrastructure.coupon.CouponRepository
import io.joopang.infrastructure.order.OrderRepository
import io.joopang.infrastructure.product.ProductRepository
import io.joopang.infrastructure.user.UserRepository
import org.springframework.stereotype.Service
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

@Service
class OrderUseCase(
    private val orderRepository: OrderRepository,
    private val productRepository: ProductRepository,
    private val userRepository: UserRepository,
    private val couponRepository: CouponRepository,
    private val dataTransmissionService: OrderDataTransmissionService,
    private val productLockManager: ProductLockManager,
) {

    fun createOrder(command: CreateOrderCommand): OrderAggregate {
        val user = userRepository.findById(command.userId)
            ?: throw UserNotFoundException(command.userId.toString())

        val orderId = orderRepository.nextIdentity()
        val now = Instant.now()
        val orderMonth = toOrderMonth(now, command.zoneId)

        require(command.items.isNotEmpty()) { "Order must contain at least one item" }

        val reservation = reserveStock(command.items)

        val subtotal = reservation.subtotal

        val couponResult = command.couponId?.let { couponId ->
            val coupon = couponRepository.findUserCoupon(command.userId, couponId)
                ?: throw CouponNotFoundException(couponId.toString())
            validateCoupon(coupon)
            val discountAmount = calculateDiscount(subtotal, coupon)
            CouponResult(coupon, discountAmount)
        }

        val orderDiscounts = couponResult
            ?.takeIf { it.discountAmount > Money.ZERO }
            ?.let { result ->
                listOf(
                    OrderDiscount(
                        id = UUID.randomUUID(),
                        orderId = orderId,
                        type = OrderDiscountType.COUPON,
                        referenceId = result.coupon.couponTemplateId,
                        price = result.discountAmount,
                        couponId = result.coupon.id,
                    ),
                )
            } ?: emptyList()

        val order = Order(
            id = orderId,
            userId = user.id,
            imageUrl = command.imageUrl,
            status = OrderStatus.PENDING,
            recipientName = command.recipientName,
            orderMonth = orderMonth,
            totalAmount = subtotal,
            discountAmount = couponResult?.discountAmount ?: Money.ZERO,
            orderedAt = now,
            memo = command.memo,
        )

        val items = reservation.drafts.map { draft ->
            io.joopang.domain.order.OrderItem(
                id = UUID.randomUUID(),
                orderId = orderId,
                productId = draft.productId,
                productItemId = draft.productItemId,
                productName = draft.productName,
                quantity = draft.quantity,
                unitPrice = draft.unitPrice,
                subtotal = draft.subtotal,
            )
        }

        val aggregate = OrderAggregate(
            order = order,
            items = items,
            discounts = orderDiscounts,
        )

        return orderRepository.save(aggregate)
    }

    fun getOrder(orderId: UUID): OrderAggregate =
        orderRepository.findById(orderId)
            ?: throw OrderNotFoundException(orderId.toString())

    fun listOrders(): List<OrderAggregate> = orderRepository.findAll()

    fun processPayment(command: ProcessPaymentCommand): PaymentResult {
        val aggregate = orderRepository.findById(command.orderId)
            ?: throw OrderNotFoundException(command.orderId.toString())

        if (aggregate.order.userId != command.userId) {
            throw IllegalStateException("Order does not belong to user ${command.userId}")
        }
        if (!aggregate.order.canPay()) {
            throw IllegalStateException("Order ${aggregate.order.id} cannot be paid in status ${aggregate.order.status}")
        }

        val user = userRepository.findById(command.userId)
            ?: throw UserNotFoundException(command.userId.toString())

        val payableAmount = aggregate.order.payableAmount()
        val updatedUser = user.deduct(payableAmount)
        userRepository.save(updatedUser)

        aggregate.discounts.forEach { discount ->
            discount.couponId?.let {
                couponRepository.markUsed(
                    couponId = it,
                    orderId = aggregate.order.id,
                    usedAt = Instant.now(),
                )
            }
        }

        val paidAt = Instant.now()
        val updatedAggregate = aggregate.copy(order = aggregate.order.markPaid(paidAt))
        orderRepository.update(updatedAggregate)

        val payload = OrderDataPayload(
            orderId = updatedAggregate.order.id,
            userId = updatedAggregate.order.userId,
            items = updatedAggregate.items.map { item ->
                OrderDataLineItem(
                    productId = item.productId,
                    productItemId = item.productItemId,
                    quantity = item.quantity.value,
                    unitPrice = item.unitPrice,
                    subtotal = item.subtotal,
                )
            },
            totalAmount = updatedAggregate.order.totalAmount,
            discountAmount = updatedAggregate.order.discountAmount,
            paidAt = updatedAggregate.order.paidAt,
        )

        try {
            dataTransmissionService.send(payload)
        } catch (ex: Exception) {
            dataTransmissionService.addToRetryQueue(payload)
        }

        return PaymentResult(
            orderId = updatedAggregate.order.id,
            paidAmount = updatedAggregate.order.payableAmount(),
            remainingBalance = updatedUser.balance,
            status = PaymentStatus.SUCCESS,
            paidAt = paidAt,
        )
    }

    private fun reserveStock(items: List<CreateOrderItemCommand>): ReservationResult {
        val drafts = mutableListOf<OrderItemDraft>()
        var subtotal = Money.ZERO

        items.forEach { require(it.quantity > 0) { "Quantity must be positive" } }

        items.groupBy { it.productId }
            .forEach { (productId, productItems) ->
                productLockManager.withProductLock(productId) {
                    val aggregate = productRepository.findById(productId)
                        ?: throw ProductNotFoundException(productId.toString())

                    val updatedItems = aggregate.items.toMutableList()

                    productItems.forEach { itemCommand ->
                        val productItemId = itemCommand.productItemId
                            ?: throw ProductItemNotFoundException(productId.toString(), "null")
                        val currentItemIndex = updatedItems.indexOfFirst { it.id == productItemId }
                        if (currentItemIndex < 0) {
                            throw ProductItemNotFoundException(productId.toString(), productItemId.toString())
                        }

                        val currentItem = updatedItems[currentItemIndex]
                        if (!currentItem.isActive()) {
                            throw IllegalStateException("Product item $productItemId is not active")
                        }

                        val requested = StockQuantity.of(itemCommand.quantity.toLong())
                        if (!currentItem.stock.isGreaterOrEqual(requested)) {
                            throw InsufficientStockException(productId.toString(), productItemId.toString())
                        }

                        val updatedItem = currentItem.copy(stock = currentItem.stock - requested)
                        updatedItems[currentItemIndex] = updatedItem

                        val quantity = Quantity(itemCommand.quantity)
                        val lineSubtotal = currentItem.unitPrice * quantity.value
                        subtotal += lineSubtotal
                        drafts += OrderItemDraft(
                            productId = productId,
                            productName = aggregate.product.name,
                            productItemId = productItemId,
                            quantity = quantity,
                            unitPrice = currentItem.unitPrice,
                            subtotal = lineSubtotal,
                        )
                    }

                    productRepository.update(ProductWithItems(aggregate.product, updatedItems))
                }
            }

        return ReservationResult(drafts = drafts, subtotal = subtotal)
    }

    private fun validateCoupon(coupon: Coupon) {
        if (coupon.isUsed()) {
            throw InvalidCouponException("Coupon ${coupon.id} is already used")
        }
        if (coupon.isExpired()) {
            throw InvalidCouponException("Coupon ${coupon.id} is expired")
        }
    }

    private fun calculateDiscount(subtotal: Money, coupon: Coupon): Money {
        val rawDiscount = when (coupon.type) {
            CouponType.PERCENTAGE ->
                Money.of(
                    subtotal.toBigDecimal()
                        .multiply(coupon.value)
                        .setScale(2, RoundingMode.HALF_UP),
                )

            CouponType.AMOUNT ->
                Money.of(coupon.value)

            CouponType.GIFT ->
                subtotal
        }

        return if (rawDiscount > subtotal) subtotal else rawDiscount
    }

    private fun toOrderMonth(instant: Instant, zoneId: ZoneId?): OrderMonth {
        val zone = zoneId ?: ZoneId.systemDefault()
        val zoned = ZonedDateTime.ofInstant(instant, zone)
        return OrderMonth.from(zoned.year, zoned.monthValue)
    }

    data class CreateOrderCommand(
        val userId: UUID,
        val recipientName: String,
        val items: List<CreateOrderItemCommand>,
        val couponId: UUID? = null,
        val memo: String? = null,
        val imageUrl: String? = null,
        val zoneId: ZoneId? = null,
    )

    data class CreateOrderItemCommand(
        val productId: UUID,
        val productItemId: UUID?,
        val quantity: Int,
    )

    data class ProcessPaymentCommand(
        val orderId: UUID,
        val userId: UUID,
    )

    data class PaymentResult(
        val orderId: UUID,
        val paidAmount: Money,
        val remainingBalance: Money,
        val status: PaymentStatus,
        val paidAt: Instant,
    )

    enum class PaymentStatus {
        SUCCESS,
        FAILED,
    }

    private data class OrderItemDraft(
        val productId: UUID,
        val productName: String,
        val productItemId: UUID?,
        val quantity: Quantity,
        val unitPrice: Money,
        val subtotal: Money,
    )

    private data class ReservationResult(
        val drafts: List<OrderItemDraft>,
        val subtotal: Money,
    )

    private data class CouponResult(
        val coupon: Coupon,
        val discountAmount: Money,
    )
}
