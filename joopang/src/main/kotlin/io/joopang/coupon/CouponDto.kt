package io.joopang.api.coupon

import java.math.BigDecimal
import java.util.UUID

data class CouponIssueRequestDto(
    val userId: UUID,
)

data class CouponIssueResponseDto(
    val couponId: UUID,
    val couponTemplateId: UUID?,
    val userId: UUID,
    val title: String,
    val type: CouponTypeDto,
    val value: BigDecimal,
    val status: CouponStatusDto,
    val issuedAt: String,
    val usedAt: String?,
    val expiredAt: String,
    val orderId: UUID?,
    val remainingQuantity: Int,
)

data class UserCouponsResponseDto(
    val userId: UUID,
    val coupons: List<UserCouponDto>,
)

data class UserCouponDto(
    val couponId: UUID,
    val couponTemplateId: UUID?,
    val title: String,
    val type: CouponTypeDto,
    val value: BigDecimal,
    val status: CouponStatusDto,
    val issuedAt: String,
    val usedAt: String?,
    val expiredAt: String,
    val orderId: UUID?,
)

enum class CouponStatusDto {
    AVAILABLE,
    USED,
    EXPIRED,
}

enum class CouponTypeDto {
    PERCENTAGE,
    AMOUNT,
    GIFT,
}
