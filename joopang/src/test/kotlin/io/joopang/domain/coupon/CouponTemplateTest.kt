package io.joopang.domain.coupon

import io.joopang.domain.common.Money
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.ints.shouldBeExactly
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class CouponTemplateTest : DescribeSpec({

    describe("issue") {
        it("decreases remaining quantity until exhausted") {
            val template = couponTemplate(total = 3, issued = 0)

            val afterFirst = template.issue()
            afterFirst.remainingQuantity().shouldBeExactly(2)

            val afterSecond = afterFirst.issue()
            afterSecond.remainingQuantity().shouldBeExactly(1)

            val afterThird = afterSecond.issue()
            afterThird.remainingQuantity().shouldBeExactly(0)

            shouldThrow<IllegalArgumentException> {
                afterThird.issue()
            }
        }
    }

    describe("canIssue") {
        it("returns false when outside active window") {
            val now = Instant.parse("2024-03-20T00:00:00Z")
            val template = couponTemplate(
                total = 10,
                issued = 0,
                start = Instant.parse("2024-03-21T00:00:00Z"),
                end = Instant.parse("2024-03-25T00:00:00Z"),
            )

            template.canIssue(now).shouldBeFalse()
        }
    }

    describe("canIssueForUser") {
        it("prevents issuing beyond per-user limit") {
            val template = couponTemplate(limit = 2)

            template.canIssueForUser(currentUserIssuedCount = 1).shouldBeTrue()
            template.canIssueForUser(currentUserIssuedCount = 2).shouldBeFalse()
        }
    }
})

private fun couponTemplate(
    total: Int = 10,
    issued: Int = 0,
    limit: Int = 1,
    start: Instant? = Instant.now().minusSeconds(3600),
    end: Instant? = Instant.now().plusSeconds(3600),
): CouponTemplate =
    CouponTemplate(
        id = UUID.randomUUID(),
        title = "테스트 쿠폰",
        type = CouponType.AMOUNT,
        value = BigDecimal("1000"),
        status = CouponTemplateStatus.ACTIVE,
        minAmount = Money.of(10_000L),
        maxDiscountAmount = Money.of(10_000L),
        totalQuantity = total,
        issuedQuantity = issued,
        limitQuantity = limit,
        startAt = start,
        endAt = end,
    )
