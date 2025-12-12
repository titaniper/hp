package io.joopang.services.order.application

import io.joopang.services.common.domain.Email
import io.joopang.services.common.domain.Money
import io.joopang.services.coupon.contract.CouponSnapshot
import io.joopang.services.coupon.contract.CouponStatus
import io.joopang.services.coupon.contract.CouponType
import io.joopang.services.order.application.coupon.InMemoryCouponClient
import io.joopang.services.order.domain.OrderDiscountType
import io.joopang.services.product.domain.ProductWithItems
import io.joopang.services.product.domain.StockQuantity
import io.joopang.services.product.infrastructure.ProductRepository
import io.joopang.services.user.domain.User
import io.joopang.services.user.infrastructure.UserRepository
import io.joopang.support.IntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Import
import org.springframework.test.annotation.DirtiesContext
import java.math.BigDecimal
import java.time.Instant

@SpringBootTest
@Import(OrderServiceTest.TestConfig::class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class OrderServiceTest @Autowired constructor(
    private val orderService: OrderService,
    private val userRepository: UserRepository,
    private val productRepository: ProductRepository,
    private val couponClient: InMemoryCouponClient,
    private val dataTransmissionService: CapturingOrderDataTransmissionService,
) : IntegrationTestSupport() {

    private var userId: Long = 0
    private var couponId: Long = 0

    private val productId = 400L
    private val productItemId = 500L

    @BeforeEach
    fun setUp() {
        userId = createUserWithBalance()
        couponClient.reset()
        couponId = issueTestCoupon()
        resetProductStock()
        dataTransmissionService.clear()
    }

    @Test
    fun `create order applies coupon discount`() {
        val result = orderService.createOrder(
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

        assertThat(result.discountAmount).isGreaterThan(Money.ZERO)
        assertThat(result.discounts).hasSize(1)
        assertThat(result.discounts.single().type).isEqualTo(OrderDiscountType.COUPON)
    }

    @Test
    fun `process payment deducts balance and sends data`() {
        val order = orderService.createOrder(
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
                orderId = order.orderId,
                userId = userId,
            ),
        )

        assertThat(result.status).isEqualTo(OrderService.PaymentStatus.SUCCESS)
        assertThat(dataTransmissionService.sentPayloads).hasSize(1)
    }

    private fun createUserWithBalance(): Long {
        return inTransaction {
            val baseUser = userRepository.findAll().first()
            val uniqueUser = baseUser.copy(
                id = null,
                email = Email("order-${System.nanoTime()}@joopang.com"),
                balance = Money.of(1_000_000L),
            )
            userRepository.save(uniqueUser).id!!
        }
    }

    private fun issueTestCoupon(): Long {
        val couponId = System.currentTimeMillis()
        couponClient.registerCoupon(
            CouponSnapshot(
                id = couponId,
                userId = userId,
                couponTemplateId = 1L,
                type = CouponType.PERCENTAGE,
                status = CouponStatus.AVAILABLE,
                value = BigDecimal("0.10"),
                issuedAt = Instant.now(),
                usedAt = null,
                expiredAt = Instant.now().plusSeconds(3600),
            ),
        )
        return couponId
    }

    private fun resetProductStock() {
        inTransaction {
            val aggregate = productRepository.findById(productId)!!
            val updatedItems = aggregate.items.map { item ->
                if (item.id == productItemId) {
                    item.copy(stock = StockQuantity.of(5))
                } else {
                    item
                }
            }
            productRepository.update(ProductWithItems(aggregate.product, updatedItems))
        }
    }

    @TestConfiguration
    class TestConfig {
        @Bean
        @Primary
        fun capturingOrderDataTransmissionService(): CapturingOrderDataTransmissionService =
            CapturingOrderDataTransmissionService()
    }

    class CapturingOrderDataTransmissionService : OrderDataTransmissionService {
        val sentPayloads = mutableListOf<OrderDataPayload>()
        val queuedPayloads = mutableListOf<OrderDataPayload>()

        override fun send(payload: OrderDataPayload) {
            sentPayloads += payload
        }

        override fun addToRetryQueue(payload: OrderDataPayload) {
            queuedPayloads += payload
        }

        fun clear() {
            sentPayloads.clear()
            queuedPayloads.clear()
        }
    }
}
