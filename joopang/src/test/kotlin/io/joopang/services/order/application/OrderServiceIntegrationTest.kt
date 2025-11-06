package io.joopang.services.order.application

import io.joopang.services.common.domain.Money
import io.joopang.services.order.infrastructure.OrderRepository
import io.joopang.services.product.domain.ProductItem
import io.joopang.services.product.domain.ProductWithItems
import io.joopang.services.product.domain.StockQuantity
import io.joopang.services.product.infrastructure.ProductRepository
import io.joopang.services.user.infrastructure.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class OrderServiceIntegrationTest @Autowired constructor(
    private val orderService: OrderService,
    private val productRepository: ProductRepository,
    private val userRepository: UserRepository,
    private val orderRepository: OrderRepository,
) {

    private val userId = UUID.fromString("aaaaaaaa-1111-2222-3333-444444444444")
    private val productId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val productItemId = UUID.fromString("21111111-1111-1111-1111-111111111111")

    @BeforeEach
    fun prepareFixtures() {
        val user = userRepository.findById(userId)!!
        userRepository.save(user.copy(balance = Money.of(10_000_000L)))

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

    @Test
    fun `concurrent orders do not oversell stock`() {
        val threads = 10
        val executor = Executors.newFixedThreadPool(threads)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threads)

        val successes = java.util.Collections.synchronizedList(mutableListOf<UUID>())
        val failures = java.util.Collections.synchronizedList(mutableListOf<Throwable>())

        repeat(threads) {
            executor.execute {
                try {
                    startLatch.await()
                    val aggregate = orderService.createOrder(
                        OrderService.CreateOrderCommand(
                            userId = userId,
                            recipientName = "동시주문",
                            items = listOf(
                                OrderService.CreateOrderItemCommand(
                                    productId = productId,
                                    productItemId = productItemId,
                                    quantity = 1,
                                ),
                            ),
                        ),
                    )
                    successes += aggregate.order.id
                } catch (ex: Exception) {
                    failures += ex
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        doneLatch.await(5, TimeUnit.SECONDS)
        executor.shutdownNow()

        val refreshed = productRepository.findById(productId)!!
        val remaining = refreshed.items.first { it.id == productItemId }.stock

        assertThat(successes).hasSize(5)
        assertThat(remaining.toBigDecimal()).isEqualByComparingTo("0")
        assertThat(orderRepository.findAll()).hasSize(5)
        assertThat(failures).isNotEmpty()
    }
}
