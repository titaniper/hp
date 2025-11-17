package io.joopang.services.coupon.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class CouponTest {

    private val coupon = Coupon(
        userId = 1L,
        couponTemplateId = 2L,
        type = CouponType.AMOUNT,
        value = BigDecimal("1000"),
        issuedAt = Instant.now().minusSeconds(60),
        expiredAt = Instant.now().plusSeconds(60),
    )

    @Test
    fun `available coupon reports non expired`() {
        assertThat(coupon.isAvailable()).isTrue()
        assertThat(coupon.isUsed()).isFalse()
    }

    @Test
    fun `mark used changes status`() {
        val orderId = 999L

        val used = coupon.markUsed(orderId)

        assertThat(used.isUsed()).isTrue()
        assertThat(used.orderId).isEqualTo(orderId)
    }

    @Test
    fun `expire marks coupon as expired`() {
        val expired = coupon.expire()

        assertThat(expired.isExpired()).isTrue()
    }
}
