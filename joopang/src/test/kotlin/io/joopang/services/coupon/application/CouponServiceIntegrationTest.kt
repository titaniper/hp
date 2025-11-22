package io.joopang.services.coupon.application

import io.joopang.services.common.domain.Email
import io.joopang.services.common.domain.Money
import io.joopang.services.coupon.domain.CouponTemplate
import io.joopang.services.coupon.domain.CouponTemplateStatus
import io.joopang.services.coupon.domain.CouponType
import io.joopang.services.coupon.infrastructure.CouponRepository
import io.joopang.services.coupon.infrastructure.CouponTemplateRepository
import io.joopang.services.user.infrastructure.UserRepository
import org.springframework.data.repository.findByIdOrNull
import io.joopang.support.IntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import java.math.BigDecimal
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CouponServiceIntegrationTest @Autowired constructor(
    private val couponService: CouponService,
    private val couponRepository: CouponRepository,
    private val couponTemplateRepository: CouponTemplateRepository,
    private val userRepository: UserRepository,
) : IntegrationTestSupport() {

    private var templateId: Long = 0

    @BeforeEach
    fun setupTemplate() {
        val template = CouponTemplate(
            title = "한정 수량",
            type = CouponType.AMOUNT,
            value = BigDecimal("1000"),
            status = CouponTemplateStatus.ACTIVE,
            minAmount = Money.of(10_000L),
            maxDiscountAmount = null,
            totalQuantity = 5,
            issuedQuantity = 0,
            limitQuantity = 1,
            startAt = Instant.now().minusSeconds(60),
            endAt = Instant.now().plusSeconds(300),
        )
        templateId = inTransaction { couponTemplateRepository.save(template).id!! }
    }

    @Test
    fun `concurrent issuance respects remaining quantity`() {
        val threads = 10
        val executor = Executors.newFixedThreadPool(threads)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threads)

        val successes = java.util.Collections.synchronizedList(mutableListOf<Long>())
        val failures = java.util.Collections.synchronizedList(mutableListOf<Throwable>())

        repeat(threads) { index ->
            executor.execute {
                val user = inTransaction {
                    val baseUser = userRepository.findAll().first()
                    userRepository.save(
                        baseUser.copy(
                            id = null,
                            email = Email("coupon-${System.nanoTime()}@joopang.com"),
                        ),
                    )
                }
                try {
                    startLatch.await()
                    val result = couponService.issueCoupon(
                        CouponService.IssueCouponCommand(
                            couponTemplateId = templateId,
                            userId = user.id!!,
                        ),
                    )
                    successes += result.coupon.id
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

        val template = couponTemplateRepository.findByIdOrNull(templateId)!!
        assertThat(successes).hasSize(5)
        successes.forEach { couponId ->
            assertThat(couponRepository.findByIdOrNull(couponId)).isNotNull
        }
        assertThat(template.issuedQuantity).isEqualTo(5)
        assertThat(failures).isNotEmpty()
    }
}
