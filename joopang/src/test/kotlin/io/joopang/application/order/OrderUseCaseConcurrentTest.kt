package io.joopang.application.order

import io.joopang.application.order.OrderUseCase.CreateOrderCommand
import io.joopang.application.order.OrderUseCase.CreateOrderItemCommand
import io.joopang.domain.common.Email
import io.joopang.domain.common.Money
import io.joopang.domain.common.PasswordHash
import io.joopang.domain.coupon.Coupon
import io.joopang.domain.coupon.CouponRepository
import io.joopang.domain.coupon.CouponStatus
import io.joopang.domain.coupon.CouponType
import io.joopang.domain.order.OrderRepository
import io.joopang.domain.product.Product
import io.joopang.domain.product.ProductCode
import io.joopang.domain.product.ProductItem
import io.joopang.domain.product.ProductItemCode
import io.joopang.domain.product.ProductItemStatus
import io.joopang.domain.product.ProductRepository
import io.joopang.domain.product.ProductStatus
import io.joopang.domain.product.ProductWithItems
import io.joopang.domain.product.StockQuantity
import io.joopang.domain.user.User
import io.joopang.domain.user.UserRepository
import io.joopang.infrastructure.product.ProductLockManagerImpl
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class OrderUseCaseConcurrentTest : DescribeSpec({

    describe("createOrder concurrency") {
        it("reserves stock without overselling for concurrent orders") {
            val productId = UUID.randomUUID()
            val productItemId = UUID.randomUUID()
            val buyers = (0 until 10).map { UUID.randomUUID() }

            val productRepository = TestProductRepository(
                productId = productId,
                productItemId = productItemId,
                initialStock = 5,
            )
            val orderRepository = TestOrderRepository()
            val userRepository = TestUserRepository(buyers)
            val couponRepository = TestCouponRepository()
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

            val productRepository = TestProductRepository(
                productId = productId,
                productItemId = productItemId,
                initialStock = 3,
            )
            val orderRepository = TestOrderRepository()
            val userRepository = TestUserRepository(listOf(userId))
            val couponRepository = TestCouponRepository()
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

private class TestProductRepository(
    private val productId: UUID,
    private val productItemId: UUID,
    initialStock: Int,
) : ProductRepository {

    private val store = ConcurrentHashMap<UUID, ProductWithItems>()

    init {
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
        store[productId] = ProductWithItems(product, listOf(item))
    }

    override fun findAll(condition: io.joopang.domain.product.ProductSearchCondition): List<ProductWithItems> =
        store.values.toList()

    override fun findTopSelling(startDateInclusive: java.time.LocalDate, limit: Int): List<ProductWithItems> = emptyList()

    override fun findById(productId: UUID): ProductWithItems? = store[productId]

    override fun save(aggregate: ProductWithItems): ProductWithItems {
        store[aggregate.product.id] = aggregate
        return aggregate
    }

    override fun update(aggregate: ProductWithItems): ProductWithItems {
        store[aggregate.product.id] = aggregate
        return aggregate
    }

    fun remainingStock(productId: UUID, productItemId: UUID): StockQuantity {
        val aggregate = store[productId] ?: return StockQuantity.ZERO
        return aggregate.items.first { it.id == productItemId }.stock
    }
}

private class TestOrderRepository : OrderRepository {

    private val store = ConcurrentHashMap<UUID, OrderAggregate>()
    private val sequence = AtomicInteger(1)

    override fun nextIdentity(): UUID = UUID.nameUUIDFromBytes(sequence.getAndIncrement().toString().toByteArray())

    override fun save(aggregate: OrderAggregate): OrderAggregate {
        store[aggregate.order.id] = aggregate
        return aggregate
    }

    override fun findById(orderId: UUID): OrderAggregate? = store[orderId]

    override fun findAll(): List<OrderAggregate> = store.values.toList()

    override fun update(aggregate: OrderAggregate): OrderAggregate {
        store[aggregate.order.id] = aggregate
        return aggregate
    }
}

private class TestUserRepository(userIds: List<UUID>) : UserRepository {

    private val store = ConcurrentHashMap<UUID, User>()

    init {
        userIds.forEach { id ->
            store[id] = User(
                id = id,
                email = Email("$id@joopang.com"),
                password = PasswordHash("password$id"),
                firstName = "User",
                lastName = id.toString().take(4),
                balance = Money.of(1_000_000L),
            )
        }
    }

    override fun findById(userId: UUID): User? = store[userId]

    override fun save(user: User): User {
        store[user.id] = user
        return user
    }

    override fun findAll(): List<User> = store.values.toList()
}

private class TestCouponRepository : CouponRepository {

    override fun findById(couponId: UUID): Coupon? = null

    override fun findUserCoupons(userId: UUID): List<Coupon> = emptyList()

    override fun findUserCoupon(userId: UUID, couponId: UUID): Coupon? = null

    override fun findUserCouponByTemplate(userId: UUID, couponTemplateId: UUID): Coupon? = null

    override fun save(coupon: Coupon): Coupon = coupon

    override fun markUsed(couponId: UUID, orderId: UUID, usedAt: Instant): Coupon =
        Coupon(
            id = couponId,
            userId = UUID.randomUUID(),
            couponTemplateId = null,
            type = CouponType.AMOUNT,
            value = java.math.BigDecimal.ZERO,
            status = CouponStatus.USED,
            issuedAt = usedAt,
            usedAt = usedAt,
            expiredAt = null,
            orderId = orderId,
        )
}

private class TestDataTransmissionService : OrderDataTransmissionService {
    override fun send(payload: OrderDataPayload) {}
    override fun addToRetryQueue(payload: OrderDataPayload) {}
}
