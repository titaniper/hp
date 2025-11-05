package io.joopang.services.coupon.presentation

import io.joopang.services.coupon.application.CouponService
import io.joopang.services.coupon.domain.CouponStatus
import io.joopang.services.coupon.domain.CouponType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api")
class CouponController(
    private val couponService: CouponService,
) {

    @PostMapping("/coupons/{templateId}/issue")
    fun issueCoupon(
        @PathVariable("templateId") couponTemplateId: UUID,
        @RequestBody request: CouponIssueRequest,
    ): CouponIssueResponse =
        couponService
            .issueCoupon(
                CouponService.IssueCouponCommand(
                    couponTemplateId = couponTemplateId,
                    userId = request.userId,
                ),
            )
            .toResponse()

    @GetMapping("/users/{userId}/coupons")
    fun getUserCoupons(
        @PathVariable userId: UUID,
    ): List<UserCouponResponse> =
        couponService
            .getUserCoupons(userId)
            .map { it.toResponse() }

    private fun CouponService.CouponIssueResult.toResponse(): CouponIssueResponse =
        CouponIssueResponse(
            userCouponId = userCouponId,
            couponTemplateId = couponTemplateId,
            type = type,
            value = value,
            status = status,
            issuedAt = issuedAt,
            expiredAt = expiredAt,
            remainingQuantity = remainingQuantity,
        )

    private fun CouponService.UserCouponResult.toResponse(): UserCouponResponse =
        UserCouponResponse(
            couponId = couponId,
            couponTemplateId = couponTemplateId,
            type = type,
            value = value,
            status = status,
            issuedAt = issuedAt,
            expiredAt = expiredAt,
            usedAt = usedAt,
            orderId = orderId,
        )
}

data class CouponIssueRequest(
    val userId: UUID,
)

data class CouponIssueResponse(
    val userCouponId: UUID,
    val couponTemplateId: UUID?,
    val type: CouponType,
    val value: BigDecimal,
    val status: CouponStatus,
    val issuedAt: Instant,
    val expiredAt: Instant?,
    val remainingQuantity: Int,
)

data class UserCouponResponse(
    val couponId: UUID,
    val couponTemplateId: UUID?,
    val type: CouponType,
    val value: BigDecimal,
    val status: CouponStatus,
    val issuedAt: Instant,
    val expiredAt: Instant?,
    val usedAt: Instant?,
    val orderId: UUID?,
)
