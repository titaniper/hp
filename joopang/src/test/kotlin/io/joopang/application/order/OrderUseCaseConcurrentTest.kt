package io.joopang.application.order

import io.joopang.application.order.OrderUseCase.CreateOrderCommand
import io.joopang.application.order.OrderUseCase.CreateOrderItemCommand
import io.joopang.domain.common.Email
import io.joopang.domain.common.Money
import io.joopang.domain.common.PasswordHash
import io.joopang.domain.product.Product
import io.joopang.domain.product.ProductCode
import io.joopang.domain.product.ProductItem
import io.joopang.domain.product.ProductItemCode
import io.joopang.domain.product.ProductItemStatus
import io.joopang.domain.product.ProductStatus
import io.joopang.domain.product.ProductWithItems
import io.joopang.domain.product.StockQuantity
import io.joopang.domain.user.User
import io.joopang.infrastructure.coupon.CouponRepository
import io.joopang.infrastructure.product.ProductLockManagerImpl
import io.joopang.infrastructure.order.OrderRepository
import io.joopang.infrastructure.product.ProductRepository
import io.joopang.infrastructure.user.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class OrderUseCaseConcurrentTest : DescribeSpec({

    describe("createOrder concurrency") {
        it("reserves stock without overselling for concurrent orders") {
            val productId = UUID.randomUUID()
            val productItemId = UUID.randomUUID()
            val buyers = (0 until 10).map { UUID.randomUUID() }

            val productRepository = ProductRepository().apply {
                seedProduct(
                    productId = productId,
                    productItemId = productItemId,
                    initialStock = 5,
                )
            }
            val orderRepository = OrderRepository()
            val userRepository = UserRepository().apply { seedUsers(buyers) }
            val couponRepository = CouponRepository()
            val useCase = OrderUseCase(
                orderRepository = orderRepository,
                productRepository = productRepository,
                userRepository = userRepository,
                couponRepository = couponRepository,
                dataTransmissionService = TestDataTransmissionService(),
                productLockManager = ProductLockManagerImpl(),
            )

            val executor = Executors.newFixedThreadPool(10)
            val tasks = buyers.map { userId ->
                Callable {
                    runCatching {
                        useCase.createOrder(
                            CreateOrderCommand(
                                userId = userId,
                                recipientName = "tester",
                                items = listOf(
                                    CreateOrderItemCommand(
                                        productId = productId,
                                        productItemId = productItemId,
                                        quantity = 1,
                                    ),
                                ),
                            ),
                        )
                    }
                }
            }

            val results = executor.invokeAll(tasks).map { it.get() }
            executor.shutdown()

            val successes = results.mapNotNull { it.getOrNull() }
            successes.shouldHaveSize(5)

            productRepository.remainingStock(productId, productItemId).shouldBe(StockQuantity.ZERO)
        }
    }

    describe("processPayment") {
        it("completes payment without changing reserved stock") {
            val productId = UUID.randomUUID()
            val productItemId = UUID.randomUUID()
            val userId = UUID.randomUUID()

            val productRepository = ProductRepository().apply {
                seedProduct(
                    productId = productId,
                    productItemId = productItemId,
                    initialStock = 3,
                )
            }
            val orderRepository = OrderRepository()
            val userRepository = UserRepository().apply { seedUsers(listOf(userId)) }
            val couponRepository = CouponRepository()
            val dataService = TestDataTransmissionService()
            val useCase = OrderUseCase(
                orderRepository = orderRepository,
                productRepository = productRepository,
                userRepository = userRepository,
                couponRepository = couponRepository,
                dataTransmissionService = dataService,
                productLockManager = ProductLockManagerImpl(),
            )

            val aggregate = useCase.createOrder(
                CreateOrderCommand(
                    userId = userId,
                    recipientName = "tester",
                    items = listOf(
                        CreateOrderItemCommand(
                            productId = productId,
                            productItemId = productItemId,
                            quantity = 2,
                        ),
                    ),
                ),
            )

            val result = useCase.processPayment(
                OrderUseCase.ProcessPaymentCommand(
                    orderId = aggregate.order.id,
                    userId = userId,
                ),
            )

            result.status.shouldBe(OrderUseCase.PaymentStatus.SUCCESS)
            productRepository.remainingStock(productId, productItemId)
                .shouldBe(StockQuantity.of(1L))
        }
    }
})

private fun ProductRepository.seedProduct(
    productId: UUID,
    productItemId: UUID,
    initialStock: Int,
) {
    val product = Product(
        id = productId,
        name = "테스트 상품",
        code = ProductCode("TEST-${productId.toString().take(6)}"),
        description = null,
        content = null,
        status = ProductStatus.ON_SALE,
        sellerId = UUID.randomUUID(),
        categoryId = UUID.randomUUID(),
        price = Money.of(10_000L),
        discountRate = null,
        version = 1,
    )
    val item = ProductItem(
        id = productItemId,
        productId = productId,
        name = "단일 옵션",
        unitPrice = Money.of(10_000L),
        description = null,
        stock = StockQuantity.of(initialStock.toLong()),
        status = ProductItemStatus.ACTIVE,
        code = ProductItemCode("ITEM-${productItemId.toString().take(6)}"),
    )
    save(ProductWithItems(product, listOf(item)))
}

private fun ProductRepository.remainingStock(productId: UUID, productItemId: UUID): StockQuantity =
    findById(productId)
        ?.items
        ?.firstOrNull { it.id == productItemId }
        ?.stock
        ?: StockQuantity.ZERO

private fun UserRepository.seedUsers(userIds: List<UUID>) {
    userIds.forEach { id ->
        save(
            User(
                id = id,
                email = Email("$id@joopang.com"),
                password = PasswordHash("password$id"),
                firstName = "User",
                lastName = id.toString().take(4),
                balance = Money.of(1_000_000L),
            ),
        )
    }
}

private class TestDataTransmissionService : OrderDataTransmissionService {
    override fun send(payload: OrderDataPayload) {}
    override fun addToRetryQueue(payload: OrderDataPayload) {}
}
