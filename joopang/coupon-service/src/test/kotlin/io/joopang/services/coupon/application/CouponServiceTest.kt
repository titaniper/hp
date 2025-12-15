package io.joopang.services.coupon.application

import io.joopang.services.common.domain.Money
import io.joopang.services.common.domain.requireId
import io.joopang.services.coupon.contract.CouponStatus
import io.joopang.services.coupon.contract.CouponType
import io.joopang.services.coupon.domain.Coupon
import io.joopang.services.coupon.domain.CouponTemplate
import io.joopang.services.coupon.domain.CouponTemplateStatus
import io.joopang.services.coupon.infrastructure.CouponRepository
import io.joopang.services.coupon.infrastructure.CouponTemplateRepository
import io.joopang.support.IntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CouponServiceTest @Autowired constructor(
    private val couponService: CouponService,
    private val couponRepository: CouponRepository,
    private val couponTemplateRepository: CouponTemplateRepository,
) : IntegrationTestSupport() {

    private var templateId: Long = 0
    private var userId: Long = 0

    @BeforeEach
    fun setUp() {
        templateId = createTemplate()
        userId = createUser()
    }

    @Test
    fun `issue coupon stores new user coupon`() {
        val result = couponService.issueCoupon(
            CouponService.IssueCouponCommand(
                couponTemplateId = templateId,
                userId = userId,
            ),
        )

        assertThat(result.coupon.id).isNotNull()
        val stored = inTransaction { couponRepository.findById(result.coupon.id).orElse(null) }
        assertThat(stored).isNotNull
    }

    @Test
    fun `getUserCoupons marks expired coupons`() {
        val expiredCoupon = inTransaction {
            couponRepository.save(
                Coupon(
                    userId = userId,
                    couponTemplateId = templateId,
                    type = couponTemplateRepository.findById(templateId).orElseThrow().type,
                    value = BigDecimal("0.10"),
                    status = CouponStatus.AVAILABLE,
                    issuedAt = Instant.now().minus(10, ChronoUnit.DAYS),
                    expiredAt = Instant.now().minus(1, ChronoUnit.DAYS),
                ),
            )
        }
        val expiredCouponId = expiredCoupon.requireId()

        val results = couponService.getUserCoupons(userId)

        val updated = inTransaction { couponRepository.findById(expiredCouponId).orElseThrow() }
        assertThat(updated.status).isEqualTo(CouponStatus.EXPIRED)
        assertThat(results.first { it.id == expiredCouponId }.status)
            .isEqualTo(CouponStatus.EXPIRED)
    }

    private fun createTemplate(): Long {
        val template = CouponTemplate(
            title = "테스트 쿠폰",
            type = CouponType.AMOUNT,
            value = BigDecimal("5000"),
            status = CouponTemplateStatus.ACTIVE,
            minAmount = Money.of(10_000L),
            maxDiscountAmount = null,
            totalQuantity = 10,
            issuedQuantity = 0,
            limitQuantity = 2,
            startAt = Instant.now().minusSeconds(60),
            endAt = Instant.now().plusSeconds(3600),
        )
        return inTransaction { couponTemplateRepository.save(template).id!! }
    }

    private fun createUser(): Long = System.nanoTime()
}
