package io.joopang.application.coupon

import io.joopang.domain.common.Email
import io.joopang.domain.common.Money
import io.joopang.domain.common.PasswordHash
import io.joopang.domain.coupon.Coupon
import io.joopang.domain.coupon.CouponStatus
import io.joopang.domain.coupon.CouponTemplate
import io.joopang.domain.coupon.CouponTemplateStatus
import io.joopang.domain.coupon.CouponType
import io.joopang.domain.user.User
import io.joopang.infrastructure.coupon.CouponLockManagerImpl
import io.joopang.infrastructure.coupon.CouponRepository
import io.joopang.infrastructure.coupon.CouponTemplateRepository
import io.joopang.infrastructure.user.UserRepository
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

class CouponUseCaseTest : DescribeSpec({

    describe("issueCoupon") {
        it("limits issuance under concurrent requests") {
            val templateId = UUID.randomUUID()
            val users = (0 until 10).map { UUID.randomUUID() }

            val templateRepository = InMemoryCouponTemplateRepository(
                CouponTemplate(
                    id = templateId,
                    title = "선착순 5명 10% 할인",
                    type = CouponType.PERCENTAGE,
                    value = BigDecimal("0.10"),
                    status = CouponTemplateStatus.ACTIVE,
                    minAmount = Money.of(10_000L),
                    maxDiscountAmount = Money.of(20_000L),
                    totalQuantity = 5,
                    issuedQuantity = 0,
                    limitQuantity = 1,
                    startAt = Instant.now().minusSeconds(3600),
                    endAt = Instant.now().plusSeconds(3600),
                ),
            )

            val couponRepository = InMemoryCouponRepository()
            val userRepository = InMemoryUserRepository(users)
            val useCase = CouponUseCase(
                couponRepository = couponRepository,
                couponTemplateRepository = templateRepository,
                userRepository = userRepository,
                couponLockManager = CouponLockManagerImpl(),
            )

            val executor = Executors.newFixedThreadPool(10)
            val tasks = users.map { userId ->
                Callable {
                    runCatching {
                        useCase.issueCoupon(
                            CouponUseCase.IssueCouponCommand(
                                couponTemplateId = templateId,
                                userId = userId,
                            ),
                        )
                    }
                }
            }

            val futures = executor.invokeAll(tasks)
            executor.shutdown()

            val successes = futures.mapNotNull { it.get().getOrNull() }
            successes.shouldHaveSize(5)

            val remaining = templateRepository.current().remainingQuantity()
            remaining.shouldBeExactly(0)

            couponRepository.count().shouldBeExactly(5)
            couponRepository.all().map { it.userId }.distinct().size.shouldBeExactly(5)
        }

        it("prevents double issuance for the same user") {
            val templateId = UUID.randomUUID()
            val userId = UUID.randomUUID()

            val templateRepository = InMemoryCouponTemplateRepository(
                CouponTemplate(
                    id = templateId,
                    title = "1인 1개 20%",
                    type = CouponType.PERCENTAGE,
                    value = BigDecimal("0.20"),
                    status = CouponTemplateStatus.ACTIVE,
                    minAmount = Money.of(5_000L),
                    maxDiscountAmount = Money.of(10_000L),
                    totalQuantity = 10,
                    issuedQuantity = 0,
                    limitQuantity = 1,
                    startAt = Instant.now().minusSeconds(3600),
                    endAt = Instant.now().plusSeconds(3600),
                ),
            )

            val couponRepository = InMemoryCouponRepository()
            val userRepository = InMemoryUserRepository(listOf(userId))
            val useCase = CouponUseCase(
                couponRepository = couponRepository,
                couponTemplateRepository = templateRepository,
                userRepository = userRepository,
                couponLockManager = CouponLockManagerImpl(),
            )

            useCase.issueCoupon(
                CouponUseCase.IssueCouponCommand(templateId, userId),
            )

            shouldThrowExactly<IllegalStateException> {
                useCase.issueCoupon(
                    CouponUseCase.IssueCouponCommand(templateId, userId),
                )
            }
        }
    }

    describe("getUserCoupons") {
        it("expires outdated coupons on access") {
            val userId = UUID.randomUUID()
            val couponId = UUID.randomUUID()

            val templateRepository = InMemoryCouponTemplateRepository(
                CouponTemplate(
                    id = UUID.randomUUID(),
                    title = "즉시 할인",
                    type = CouponType.AMOUNT,
                    value = BigDecimal("3000"),
                    status = CouponTemplateStatus.ACTIVE,
                    minAmount = Money.of(1_000L),
                    maxDiscountAmount = null,
                    totalQuantity = 100,
                    issuedQuantity = 0,
                    limitQuantity = 5,
                    startAt = Instant.now().minusSeconds(3600),
                    endAt = Instant.now().plusSeconds(3600),
                ),
            )

            val couponRepository = InMemoryCouponRepository(
                coupons = listOf(
                    Coupon(
                        id = couponId,
                        userId = userId,
                        couponTemplateId = null,
                        type = CouponType.AMOUNT,
                        value = BigDecimal("3000"),
                        status = CouponStatus.AVAILABLE,
                        issuedAt = Instant.now().minusSeconds(7200),
                        usedAt = null,
                        expiredAt = Instant.now().minusSeconds(10),
                        orderId = null,
                    ),
                ),
            )
            val userRepository = InMemoryUserRepository(listOf(userId))
            val useCase = CouponUseCase(
                couponRepository = couponRepository,
                couponTemplateRepository = templateRepository,
                userRepository = userRepository,
                couponLockManager = CouponLockManagerImpl(),
            )

            val results = useCase.getUserCoupons(userId)
            results.shouldHaveSize(1)
            results.first().status.shouldBe(CouponStatus.EXPIRED)
            couponRepository.findById(couponId)?.status.shouldBe(CouponStatus.EXPIRED)
        }
    }
})

private class InMemoryCouponTemplateRepository(initial: CouponTemplate) : CouponTemplateRepository() {

    private val templateRef = AtomicReference(initial)

    override fun findById(templateId: UUID): CouponTemplate? =
        templateRef.get().takeIf { it.id == templateId }

    override fun save(template: CouponTemplate): CouponTemplate {
        templateRef.updateAndGet { existing ->
            require(existing.id == template.id) { "Template id mismatch" }
            template
        }
        return template
    }

    fun current(): CouponTemplate = templateRef.get()
}

private class InMemoryCouponRepository(
    coupons: List<Coupon> = emptyList(),
) : CouponRepository() {

    private val store = ConcurrentHashMap<UUID, Coupon>(coupons.associateBy { it.id })

    override fun findById(couponId: UUID): Coupon? = store[couponId]

    override fun findUserCoupons(userId: UUID): List<Coupon> =
        store.values.filter { it.userId == userId }

    override fun findUserCoupon(userId: UUID, couponId: UUID): Coupon? =
        store[couponId]?.takeIf { it.userId == userId }

    override fun findUserCouponByTemplate(userId: UUID, couponTemplateId: UUID): Coupon? =
        store.values.firstOrNull { it.userId == userId && it.couponTemplateId == couponTemplateId }

    override fun save(coupon: Coupon): Coupon {
        store[coupon.id] = coupon
        return coupon
    }

    override fun markUsed(couponId: UUID, orderId: UUID, usedAt: Instant): Coupon {
        val existing = store[couponId] ?: throw IllegalArgumentException("coupon not found")
        val updated = existing.copy(status = CouponStatus.USED, orderId = orderId, usedAt = usedAt)
        store[couponId] = updated
        return updated
    }

    fun count(): Int = store.size
    fun all(): List<Coupon> = store.values.toList()
}

private class InMemoryUserRepository(userIds: List<UUID>) : UserRepository() {

    private val store = ConcurrentHashMap<UUID, User>()

    init {
        userIds.forEach { id ->
            store[id] = User(
                id = id,
                email = Email("$id@joopang.com"),
                password = PasswordHash("password$id"),
                firstName = "User",
                lastName = id.toString().take(4),
                balance = Money.of(100_000L),
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
