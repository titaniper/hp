package io.joopang.services.coupon.application

import io.joopang.services.common.domain.Email
import io.joopang.services.common.domain.Money
import io.joopang.services.common.domain.PasswordHash
import io.joopang.services.coupon.domain.Coupon
import io.joopang.services.coupon.domain.CouponStatus
import io.joopang.services.coupon.infrastructure.CouponLockManagerImpl
import io.joopang.services.coupon.infrastructure.CouponRepository
import io.joopang.services.coupon.infrastructure.CouponTemplateRepository
import io.joopang.services.user.domain.User
import io.joopang.services.user.infrastructure.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class CouponServiceTest {

    private lateinit var couponRepository: CouponRepository
    private lateinit var couponTemplateRepository: CouponTemplateRepository
    private lateinit var userRepository: UserRepository
    private lateinit var couponService: CouponService

    private val templateId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
    private lateinit var userId: UUID

    @BeforeEach
    fun setUp() {
        couponRepository = CouponRepository()
        couponTemplateRepository = CouponTemplateRepository()
        userRepository = UserRepository()

        userId = UUID.randomUUID()
        userRepository.save(
            User(
                id = userId,
                email = Email("tester@joopang.com"),
                password = PasswordHash("hash22222222222"),
                firstName = "Tester",
                lastName = "User",
                balance = Money.of(100_000L),
            ),
        )
        couponService = CouponService(
            couponRepository = couponRepository,
            couponTemplateRepository = couponTemplateRepository,
            userRepository = userRepository,
            couponLockManager = CouponLockManagerImpl(),
        )
    }

    @Test
    fun `issue coupon stores new user coupon`() {
        val result = couponService.issueCoupon(
            CouponService.IssueCouponCommand(
                couponTemplateId = templateId,
                userId = userId,
            ),
        )

        assertThat(result.userCouponId).isNotNull()
        assertThat(couponRepository.findById(result.userCouponId)).isNotNull
    }

    @Test
    fun `getUserCoupons marks expired coupons`() {
        val expiredCoupon = Coupon(
            id = UUID.randomUUID(),
            userId = userId,
            couponTemplateId = templateId,
            type = couponTemplateRepository.findById(templateId)!!.type,
            value = BigDecimal("0.10"),
            status = CouponStatus.AVAILABLE,
            issuedAt = Instant.now().minus(10, ChronoUnit.DAYS),
            expiredAt = Instant.now().minus(1, ChronoUnit.DAYS),
        )
        couponRepository.save(expiredCoupon)

        val results = couponService.getUserCoupons(userId)

        val updated = couponRepository.findById(expiredCoupon.id)!!
        assertThat(updated.status).isEqualTo(CouponStatus.EXPIRED)
        assertThat(results.first { it.couponId == expiredCoupon.id }.status)
            .isEqualTo(CouponStatus.EXPIRED)
    }
}
