package io.joopang.services.coupon.domain

import io.joopang.services.common.domain.Money
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class CouponTemplateTest {

    private fun activeTemplate(issued: Int = 0, total: Int = 10): CouponTemplate =
        CouponTemplate(
            title = "테스트 쿠폰",
            type = CouponType.AMOUNT,
            value = BigDecimal("1000"),
            status = CouponTemplateStatus.ACTIVE,
            minAmount = Money.of(10_000L),
            maxDiscountAmount = null,
            totalQuantity = total,
            issuedQuantity = issued,
            limitQuantity = 1,
            startAt = Instant.now().minusSeconds(60),
            endAt = Instant.now().plusSeconds(60),
        )

    @Test
    fun `active template can issue`() {
        val template = activeTemplate()

        assertThat(template.canIssue()).isTrue()
    }

    @Test
    fun `issue increments counter`() {
        val template = activeTemplate(issued = 2)

        val updated = template.issue()

        assertThat(updated.issuedQuantity).isEqualTo(3)
    }

    @Test
    fun `issue fails when no quantity`() {
        val template = activeTemplate(issued = 10, total = 10)

        assertThrows(IllegalArgumentException::class.java) {
            template.issue()
        }
    }
}
