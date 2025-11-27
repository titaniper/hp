package io.joopang.services.order.application

import io.joopang.common.lock.DistributedLock
import io.joopang.services.common.domain.Money
import io.joopang.services.common.domain.OrderMonth
import io.joopang.services.common.domain.Quantity
import io.joopang.services.common.domain.requireId
import io.joopang.services.coupon.domain.Coupon
import io.joopang.services.coupon.domain.CouponNotFoundException
import io.joopang.services.coupon.domain.CouponStatus
import io.joopang.services.coupon.domain.CouponType
import io.joopang.services.coupon.domain.InvalidCouponException
import io.joopang.services.coupon.infrastructure.CouponRepository
import io.joopang.services.order.domain.Order
import io.joopang.services.order.domain.OrderDiscount
import io.joopang.services.order.domain.OrderDiscountType
import io.joopang.services.order.domain.OrderItem
import io.joopang.services.order.domain.OrderNotFoundException
import io.joopang.services.order.domain.OrderOwnershipException
import io.joopang.services.order.domain.OrderStatus
import io.joopang.services.order.domain.OrderPaymentNotAllowedException
import io.joopang.services.order.infrastructure.OrderRepository
import io.joopang.services.product.domain.InsufficientStockException
import io.joopang.services.product.domain.ProductItemNotFoundException
import io.joopang.services.product.domain.ProductNotFoundException
import io.joopang.services.product.domain.ProductWithItems
import io.joopang.services.product.domain.ProductItemInactiveException
import io.joopang.services.product.domain.StockQuantity
import io.joopang.services.product.infrastructure.ProductRepository
import io.joopang.services.user.domain.UserNotFoundException
import io.joopang.services.user.infrastructure.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
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
) {
    private val logger = LoggerFactory.getLogger(OrderService::class.java)

    @Transactional
    fun createOrder(command: CreateOrderCommand): Output {
        val user = userRepository.findByIdOrNull(command.userId)
            ?: throw UserNotFoundException(command.userId.toString())
        val userId = user.requireId()
        val now = Instant.now()
        val orderMonth = toOrderMonth(now, command.zoneId)

        require(command.items.isNotEmpty()) { "Order must contain at least one item" }

        val reservation = reserveStock(command.items)

        val subtotal = reservation.subtotal

        val couponResult = command.couponId?.let { couponId ->
            val coupon = couponRepository.findByIdAndUserId(couponId, userId)
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
                        couponId = result.coupon.requireId(),
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

        items.forEach(order::addItem)
        orderDiscounts.forEach(order::addDiscount)

        return orderRepository.save(order).toOutput()
    }

    fun getOrder(orderId: Long): Output =
        orderRepository.findWithDetailsById(orderId)
            ?.toOutput()
            ?: throw OrderNotFoundException(orderId.toString())

    fun listOrders(): List<Output> =
        orderRepository.findAllWithDetails()
            .map { it.toOutput() }

    @Transactional
    fun processPayment(command: ProcessPaymentCommand): PaymentOutput {
        val order = orderRepository.findWithDetailsByIdForUpdate(command.orderId)
            ?: throw OrderNotFoundException(command.orderId.toString())

        if (order.userId != command.userId) {
            throw OrderOwnershipException(command.orderId.toString(), command.userId.toString())
        }
        if (!order.canPay()) {
            throw OrderPaymentNotAllowedException(
                (order.id ?: command.orderId).toString(),
                order.status,
            )
        }
        val orderId = order.id ?: command.orderId

        val user = userRepository.findByIdForUpdate(command.userId)
            ?: throw UserNotFoundException(command.userId.toString())

        val payableAmount = order.payableAmount()
        val updatedUser = user.deduct(payableAmount)
        userRepository.save(updatedUser)

        order.discounts.forEach { discount ->
            discount.couponId?.let { couponId ->
                val coupon = couponRepository.findByIdForUpdate(couponId)
                    ?: throw CouponNotFoundException(couponId.toString())
                if (coupon.userId != order.userId) {
                    throw InvalidCouponException("Coupon $couponId does not belong to user ${order.userId}")
                }
                if (coupon.status != CouponStatus.AVAILABLE) {
                    throw InvalidCouponException("Coupon $couponId is not available")
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
        order.markPaid(paidAt)
        orderRepository.save(order)

        val payload = OrderDataPayload(
            orderId = orderId,
            userId = order.userId,
            items = order.items.map { item ->
                OrderDataLineItem(
                    productId = item.productId,
                    productItemId = item.productItemId,
                    quantity = item.quantity.value,
                    unitPrice = item.unitPrice,
                    subtotal = item.subtotal,
                )
            },
            totalAmount = order.totalAmount,
            discountAmount = order.discountAmount,
            paidAt = order.paidAt,
        )

        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() {
                    try {
                        dataTransmissionService.send(payload)
                    } catch (ex: Exception) {
                        logger.error("Failed to send order data. Adding to retry queue.", ex)
                        dataTransmissionService.addToRetryQueue(payload)
                    }
                }
            },
        )

        return PaymentOutput(
            orderId = orderId,
            paidAmount = order.payableAmount(),
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
                val result = reserveStockForProduct(productId, productItems)
                drafts += result.drafts
                subtotal += result.subtotal
            }

        return ReservationResult(drafts = drafts, subtotal = subtotal)
    }

    @DistributedLock(
        prefix = PRODUCT_LOCK_PREFIX,
        key = "#productId",
        waitTime = PRODUCT_LOCK_WAIT_SECONDS,
        leaseTime = PRODUCT_LOCK_LEASE_SECONDS,
        failureMessage = "상품 재고 정리 중입니다. 잠시 후 다시 시도해주세요.",
    )
    private fun reserveStockForProduct(
        productId: Long,
        productItems: List<CreateOrderItemCommand>,
    ): ProductReservation {
        val aggregate = productRepository.findById(productId)
            ?: throw ProductNotFoundException(productId.toString())

        val drafts = mutableListOf<OrderItemDraft>()
        var subtotal = Money.ZERO

        productItems.forEach { itemCommand ->
            val productItemId = itemCommand.productItemId
                ?: throw ProductItemNotFoundException(productId.toString(), "null")
            val currentItem = aggregate.items.firstOrNull { it.id == productItemId }
                ?: throw ProductItemNotFoundException(productId.toString(), productItemId.toString())

            if (!currentItem.isActive()) {
                throw ProductItemInactiveException(productId.toString(), productItemId.toString())
            }

            val requested = StockQuantity.of(itemCommand.quantity.toLong())
            if (!currentItem.stock.isGreaterOrEqual(requested)) {
                throw InsufficientStockException(productId.toString(), productItemId.toString())
            }

            val consumed = productRepository.consumeStock(productItemId, itemCommand.quantity.toLong())
            if (!consumed) {
                logger.warn(
                    "Failed to consume stock. productId={}, productItemId={}, requested={}",
                    productId,
                    productItemId,
                    itemCommand.quantity,
                )
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

        return ProductReservation(drafts = drafts, subtotal = subtotal)
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

    private fun Order.toOutput(): Output =
        Output(
            orderId = requireId(),
            userId = userId,
            status = status,
            recipientName = recipientName,
            orderedAt = orderedAt,
            paidAt = paidAt,
            orderMonth = orderMonth,
            totalAmount = totalAmount,
            discountAmount = discountAmount,
            payableAmount = payableAmount(),
            imageUrl = imageUrl,
            memo = memo,
            items = items.map { it.toOutput() },
            discounts = discounts.map { it.toOutput() },
        )

    private fun OrderItem.toOutput(): Output.Item =
        Output.Item(
            orderItemId = requireId(),
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
            discountId = requireId(),
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

    private data class ProductReservation(
        val drafts: List<OrderItemDraft>,
        val subtotal: Money,
    )

    private data class CouponResult(
        val coupon: Coupon,
        val discountAmount: Money,
    )

    companion object {
        private const val PRODUCT_LOCK_PREFIX = "lock:product:"
        private const val PRODUCT_LOCK_WAIT_SECONDS = 2L
        private const val PRODUCT_LOCK_LEASE_SECONDS = 5L
    }
}
