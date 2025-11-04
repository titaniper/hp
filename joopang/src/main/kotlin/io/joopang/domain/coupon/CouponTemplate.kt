package io.joopang.domain.coupon

import io.joopang.domain.common.Money
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class CouponTemplate(
    val id: UUID,
    val title: String,
    val type: CouponType,
    val value: BigDecimal,
    val status: CouponTemplateStatus,
    val minAmount: Money? = null,
    val maxDiscountAmount: Money? = null,
    val totalQuantity: Int,
    val issuedQuantity: Int,
    val limitQuantity: Int,
    val startAt: Instant?,
    val endAt: Instant?,
) {

    init {
        require(title.isNotBlank()) { "Coupon template title must not be blank" }
        require(totalQuantity >= 0) { "Total quantity cannot be negative" }
        require(issuedQuantity >= 0) { "Issued quantity cannot be negative" }
        require(issuedQuantity <= totalQuantity) { "Issued quantity cannot exceed total quantity" }
        require(limitQuantity >= 0) { "Limit quantity cannot be negative" }
        require(value >= BigDecimal.ZERO) { "Coupon value cannot be negative" }
    }

    fun isActive(at: Instant = Instant.now()): Boolean =
        status == CouponTemplateStatus.ACTIVE &&
            (startAt == null || !at.isBefore(startAt)) &&
            (endAt == null || !at.isAfter(endAt))

    fun remainingQuantity(): Int = totalQuantity - issuedQuantity

    fun canIssue(at: Instant = Instant.now()): Boolean =
        isActive(at) && remainingQuantity() > 0

    fun canIssueForUser(currentUserIssuedCount: Int): Boolean =
        limitQuantity <= 0 || currentUserIssuedCount < limitQuantity

    fun issue(): CouponTemplate {
        require(remainingQuantity() > 0) { "No coupons remaining for template $id" }
        return copy(issuedQuantity = issuedQuantity + 1)
    }
}
