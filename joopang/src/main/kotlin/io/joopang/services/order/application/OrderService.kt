package io.joopang.services.order.application

import io.joopang.services.common.domain.Money
import io.joopang.services.common.domain.OrderMonth
import io.joopang.services.common.domain.Quantity
import io.joopang.services.coupon.domain.Coupon
import io.joopang.services.coupon.domain.CouponNotFoundException
import io.joopang.services.coupon.domain.CouponStatus
import io.joopang.services.coupon.domain.CouponType
import io.joopang.services.coupon.domain.InvalidCouponException
import io.joopang.services.coupon.infrastructure.CouponRepository
import io.joopang.services.order.domain.Order
import io.joopang.services.order.domain.OrderAggregate
import io.joopang.services.order.domain.OrderDiscount
import io.joopang.services.order.domain.OrderDiscountType
import io.joopang.services.order.domain.OrderItem
import io.joopang.services.order.domain.OrderNotFoundException
import io.joopang.services.order.domain.OrderStatus
import io.joopang.services.order.infrastructure.OrderRepository
import io.joopang.services.product.domain.InsufficientStockException
import io.joopang.services.product.domain.ProductItemNotFoundException
import io.joopang.services.product.domain.ProductNotFoundException
import io.joopang.services.product.domain.ProductWithItems
import io.joopang.services.product.domain.StockQuantity
import io.joopang.services.product.infrastructure.ProductRepository
import io.joopang.services.user.domain.UserNotFoundException
import io.joopang.services.user.infrastructure.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

@Service
@Transactional(readOnly = true)
class OrderService(
    private val orderRepository: OrderRepository,
    private val productRepository: ProductRepository,
    private val userRepository: UserRepository,
    private val couponRepository: CouponRepository,
    private val dataTransmissionService: OrderDataTransmissionService,
    private val productLockManager: ProductLockManager,
) {

    @Transactional
    fun createOrder(command: CreateOrderCommand): Output {
        val user = userRepository.findById(command.userId)
            ?: throw UserNotFoundException(command.userId.toString())
        val userId = user.id ?: throw IllegalStateException("User id is null")
        val now = Instant.now()
        val orderMonth = toOrderMonth(now, command.zoneId)

        require(command.items.isNotEmpty()) { "Order must contain at least one item" }

        val reservation = reserveStock(command.items)

        val subtotal = reservation.subtotal

        val couponResult = command.couponId?.let { couponId ->
            val coupon = couponRepository.findUserCoupon(userId, couponId)
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
                        type = OrderDiscountType.COUPON,
                        referenceId = result.coupon.couponTemplateId,
                        price = result.discountAmount,
                        couponId = result.coupon.id,
                    ),
                )
            } ?: emptyList()

        val order = Order(
            userId = userId,
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
            OrderItem(
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

        return orderRepository.save(aggregate).toOutput()
    }

    fun getOrder(orderId: Long): Output =
        orderRepository.findById(orderId)
            ?.toOutput()
            ?: throw OrderNotFoundException(orderId.toString())

    fun listOrders(): List<Output> =
        orderRepository.findAll()
            .map { it.toOutput() }

    @Transactional
    fun processPayment(command: ProcessPaymentCommand): PaymentOutput {
        val aggregate = orderRepository.findById(command.orderId)
            ?: throw OrderNotFoundException(command.orderId.toString())

        if (aggregate.order.userId != command.userId) {
            throw IllegalStateException("Order does not belong to user ${command.userId}")
        }
        if (!aggregate.order.canPay()) {
            throw IllegalStateException("Order ${aggregate.order.id} cannot be paid in status ${aggregate.order.status}")
        }
        val orderId = aggregate.order.id ?: throw IllegalStateException("Order id is null")

        val user = userRepository.findById(command.userId)
            ?: throw UserNotFoundException(command.userId.toString())

        val payableAmount = aggregate.order.payableAmount()
        val updatedUser = user.deduct(payableAmount)
        userRepository.save(updatedUser)

        aggregate.discounts.forEach { discount ->
            discount.couponId?.let { couponId ->
                val coupon = couponRepository.findById(couponId)
                    ?: throw CouponNotFoundException(couponId.toString())
                if (coupon.userId != aggregate.order.userId) {
                    throw IllegalStateException("Coupon $couponId does not belong to user ${aggregate.order.userId}")
                }
                if (coupon.status != CouponStatus.AVAILABLE) {
                    throw IllegalStateException("Coupon $couponId is not available")
                }
                val usedAt = Instant.now()
                val updatedCoupon = coupon.markUsed(
                    orderId = orderId,
                    usedAt = usedAt,
                )
                couponRepository.save(updatedCoupon)
            }
        }

        val paidAt = Instant.now()
        val updatedAggregate = aggregate.copy(order = aggregate.order.markPaid(paidAt))
        orderRepository.update(updatedAggregate)

        val payload = OrderDataPayload(
            orderId = orderId,
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

        return PaymentOutput(
            orderId = orderId,
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

                    productItems.forEach { itemCommand ->
                        val productItemId = itemCommand.productItemId
                            ?: throw ProductItemNotFoundException(productId.toString(), "null")
                        val currentItem = aggregate.items.firstOrNull { it.id == productItemId }
                            ?: throw ProductItemNotFoundException(productId.toString(), productItemId.toString())

                        if (!currentItem.isActive()) {
                            throw IllegalStateException("Product item $productItemId is not active")
                        }

                        val requested = StockQuantity.of(itemCommand.quantity.toLong())
                        if (!currentItem.stock.isGreaterOrEqual(requested)) {
                            throw ProductItemNotFoundException(productId.toString(), productItemId.toString())
                        }

                        val consumed = productRepository.consumeStock(productItemId, itemCommand.quantity.toLong())
                        if (!consumed) {
                            throw InsufficientStockException(productId.toString(), productItemId.toString())
                        }

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

    private fun OrderAggregate.toOutput(): Output =
        Output(
            orderId = order.id,
            userId = order.userId,
            status = order.status,
            recipientName = order.recipientName,
            orderedAt = order.orderedAt,
            paidAt = order.paidAt,
            orderMonth = order.orderMonth,
            totalAmount = order.totalAmount,
            discountAmount = order.discountAmount,
            payableAmount = order.payableAmount(),
            imageUrl = order.imageUrl,
            memo = order.memo,
            items = items.map { it.toOutput() },
            discounts = discounts.map { it.toOutput() },
        )

    private fun OrderItem.toOutput(): Output.Item =
        Output.Item(
            orderItemId = id,
            productId = productId,
            productItemId = productItemId,
            productName = productName,
            quantity = quantity,
            unitPrice = unitPrice,
            subtotal = subtotal,
            refundedAmount = refundedAmount,
            refundedQuantity = refundedQuantity,
        )

    private fun OrderDiscount.toOutput(): Output.Discount =
        Output.Discount(
            discountId = id,
            type = type,
            referenceId = referenceId,
            amount = price,
            couponId = couponId,
        )

    data class CreateOrderCommand(
        val userId: Long,
        val recipientName: String,
        val items: List<CreateOrderItemCommand>,
        val couponId: Long? = null,
        val memo: String? = null,
        val imageUrl: String? = null,
        val zoneId: ZoneId? = null,
    )

    data class CreateOrderItemCommand(
        val productId: Long,
        val productItemId: Long?,
        val quantity: Int,
    )

    data class ProcessPaymentCommand(
        val orderId: Long,
        val userId: Long,
    )

    data class Output(
        val orderId: Long,
        val userId: Long,
        val status: OrderStatus,
        val recipientName: String,
        val orderedAt: Instant,
        val paidAt: Instant?,
        val orderMonth: OrderMonth,
        val totalAmount: Money,
        val discountAmount: Money,
        val payableAmount: Money,
        val imageUrl: String?,
        val memo: String?,
        val items: List<Item>,
        val discounts: List<Discount>,
    ) {
        data class Item(
            val orderItemId: Long,
            val productId: Long?,
            val productItemId: Long?,
            val productName: String,
            val quantity: Quantity,
            val unitPrice: Money,
            val subtotal: Money,
            val refundedAmount: Money,
            val refundedQuantity: Quantity,
        )

        data class Discount(
            val discountId: Long,
            val type: OrderDiscountType,
            val referenceId: Long?,
            val amount: Money,
            val couponId: Long?,
        )
    }

    data class PaymentOutput(
        val orderId: Long,
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
        val productId: Long,
        val productName: String,
        val productItemId: Long?,
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
