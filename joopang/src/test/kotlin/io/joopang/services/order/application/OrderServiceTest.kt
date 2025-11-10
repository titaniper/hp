package io.joopang.services.order.application

import io.joopang.services.common.domain.Money
import io.joopang.services.order.infrastructure.OrderRepository
import io.joopang.services.product.infrastructure.ProductRepository
import io.joopang.services.user.infrastructure.UserRepository
import io.joopang.services.coupon.infrastructure.CouponRepository
import io.joopang.services.coupon.infrastructure.CouponTemplateRepository
import io.joopang.services.coupon.application.CouponService
import io.joopang.services.coupon.infrastructure.CouponLockManagerImpl
import io.joopang.services.product.infrastructure.ProductLockManagerImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class OrderServiceTest {

    private lateinit var orderRepository: OrderRepository
    private lateinit var productRepository: ProductRepository
    private lateinit var userRepository: UserRepository
    private lateinit var couponRepository: CouponRepository
    private lateinit var couponTemplateRepository: CouponTemplateRepository
    private lateinit var couponService: CouponService
    private lateinit var orderService: OrderService
    private lateinit var dataTransmissionService: CapturingOrderDataTransmissionService

    private val userId = UUID.fromString("aaaaaaaa-1111-2222-3333-444444444444")
    private val productId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val productItemId = UUID.fromString("21111111-1111-1111-1111-111111111111")
    private val couponId = UUID.fromString("10101010-2020-3030-4040-505050505050")

    @BeforeEach
    fun setUp() {
        orderRepository = OrderRepository()
        productRepository = ProductRepository()
        userRepository = UserRepository()
        couponRepository = CouponRepository()
        couponTemplateRepository = CouponTemplateRepository()
        couponService = CouponService(
            couponRepository,
            couponTemplateRepository,
            userRepository,
            CouponLockManagerImpl(),
        )
        dataTransmissionService = CapturingOrderDataTransmissionService()
        orderService = OrderService(
            orderRepository,
            productRepository,
            userRepository,
            couponRepository,
            dataTransmissionService,
            ProductLockManagerImpl(),
        )
    }

    @Test
    fun `create order applies coupon discount`() {
        val aggregate = orderService.createOrder(
            OrderService.CreateOrderCommand(
                userId = userId,
                recipientName = "고객",
                items = listOf(
                    OrderService.CreateOrderItemCommand(
                        productId = productId,
                        productItemId = productItemId,
                        quantity = 1,
                    ),
                ),
                couponId = couponId,
            ),
        )

        assertThat(aggregate.order.discountAmount).isGreaterThan(Money.ZERO)
        assertThat(aggregate.discounts).hasSize(1)
    }

    @Test
    fun `process payment deducts balance and sends data`() {
        val aggregate = orderService.createOrder(
            OrderService.CreateOrderCommand(
                userId = userId,
                recipientName = "고객",
                items = listOf(
                    OrderService.CreateOrderItemCommand(
                        productId = productId,
                        productItemId = productItemId,
                        quantity = 1,
                    ),
                ),
            ),
        )

        val result = orderService.processPayment(
            OrderService.ProcessPaymentCommand(
                orderId = aggregate.order.id,
                userId = userId,
            ),
        )

        assertThat(result.status).isEqualTo(OrderService.PaymentStatus.SUCCESS)
        assertThat(dataTransmissionService.sentPayloads).hasSize(1)
    }

    private class CapturingOrderDataTransmissionService : OrderDataTransmissionService {
        val sentPayloads = mutableListOf<OrderDataPayload>()
        val queuedPayloads = mutableListOf<OrderDataPayload>()

        override fun send(payload: OrderDataPayload) {
            sentPayloads += payload
        }

        override fun addToRetryQueue(payload: OrderDataPayload) {
            queuedPayloads += payload
        }
    }
}
