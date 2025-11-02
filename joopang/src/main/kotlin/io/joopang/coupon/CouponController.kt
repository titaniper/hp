package io.joopang.api.coupon

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.util.UUID

@RestController
@RequestMapping("/api")
class CouponController {
    @PostMapping("/coupons/{couponId}/issue")
    fun issueCoupon(
        @PathVariable couponId: UUID,
        @RequestBody request: CouponIssueRequestDto,
    ): CouponIssueResponseDto {
        val definition = MOCK_COUPON_DEFINITIONS[couponId] ?: DEFAULT_COUPON_DEFINITION
        return CouponIssueResponseDto(
            couponId = UUID.randomUUID(),
            couponTemplateId = definition.couponTemplateId,
            userId = request.userId,
            title = definition.title,
            type = definition.type,
            value = definition.value,
            status = CouponStatusDto.AVAILABLE,
            issuedAt = "2024-03-15T09:00:00Z",
            usedAt = null,
            expiredAt = definition.expiresAt,
            orderId = null,
            remainingQuantity = definition.remainingQuantityAfterIssue(),
        )
    }

    @GetMapping("/users/{userId}/coupons")
    fun getUserCoupons(@PathVariable userId: UUID): UserCouponsResponseDto =
        MOCK_USER_COUPONS_RESPONSE.copy(userId = userId)
}

private data class CouponDefinition(
    val couponTemplateId: UUID?,
    val title: String,
    val type: CouponTypeDto,
    val value: BigDecimal,
    val expiresAt: String,
    val totalQuantity: Int,
    val issuedQuantity: Int,
) {
    fun remainingQuantity(): Int = (totalQuantity - issuedQuantity).coerceAtLeast(0)
    fun remainingQuantityAfterIssue(): Int = (totalQuantity - (issuedQuantity + 1)).coerceAtLeast(0)
}

private val COUPON_ID_FLASH_SALE = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
private val COUPON_ID_FREE_SHIPPING = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")

private val MOCK_COUPON_DEFINITIONS = mapOf(
    COUPON_ID_FLASH_SALE to CouponDefinition(
        couponTemplateId = UUID.fromString("10101010-2020-3030-4040-505050505050"),
        title = "10% 할인 쿠폰",
        type = CouponTypeDto.PERCENTAGE,
        value = BigDecimal("0.10"),
        expiresAt = "2024-12-31T23:59:59Z",
        totalQuantity = 1000,
        issuedQuantity = 508,
    ),
    COUPON_ID_FREE_SHIPPING to CouponDefinition(
        couponTemplateId = UUID.fromString("60606060-7070-8080-9090-a0a0a0a0a0a0"),
        title = "배송비 무료 쿠폰",
        type = CouponTypeDto.GIFT,
        value = BigDecimal.ZERO,
        expiresAt = "2024-06-30T23:59:59Z",
        totalQuantity = 500,
        issuedQuantity = 500,
    ),
)

private val DEFAULT_COUPON_DEFINITION = CouponDefinition(
    couponTemplateId = UUID.fromString("bbbbcccc-dddd-eeee-ffff-000011112222"),
    title = "신규 가입 5% 할인",
    type = CouponTypeDto.PERCENTAGE,
    value = BigDecimal("0.05"),
    expiresAt = "2024-09-30T23:59:59Z",
    totalQuantity = 2000,
    issuedQuantity = 960,
)

private val MOCK_USER_COUPONS_RESPONSE = UserCouponsResponseDto(
    userId = UUID.fromString("aaaaaaaa-1111-2222-3333-444444444444"),
    coupons = listOf(
        UserCouponDto(
            couponId = UUID.fromString("11111111-1111-1111-1111-111111111111"),
            couponTemplateId = UUID.fromString("10101010-2020-3030-4040-505050505050"),
            title = "10% 할인 쿠폰",
            type = CouponTypeDto.PERCENTAGE,
            value = BigDecimal("0.10"),
            status = CouponStatusDto.AVAILABLE,
            issuedAt = "2024-03-01T08:30:00Z",
            usedAt = null,
            expiredAt = "2024-12-31T23:59:59Z",
            orderId = null,
        ),
        UserCouponDto(
            couponId = UUID.fromString("22222222-2222-2222-2222-222222222222"),
            couponTemplateId = UUID.fromString("60606060-7070-8080-9090-a0a0a0a0a0a0"),
            title = "배송비 무료 쿠폰",
            type = CouponTypeDto.GIFT,
            value = BigDecimal.ZERO,
            status = CouponStatusDto.USED,
            issuedAt = "2024-02-10T10:15:00Z",
            usedAt = "2024-02-18T12:04:00Z",
            expiredAt = "2024-06-30T23:59:59Z",
            orderId = UUID.fromString("99999999-9999-9999-9999-999999999999"),
        ),
        UserCouponDto(
            couponId = UUID.fromString("33333333-3333-3333-3333-333333333333"),
            couponTemplateId = UUID.fromString("bbbbcccc-dddd-eeee-ffff-000011112222"),
            title = "신규 가입 5% 할인",
            type = CouponTypeDto.PERCENTAGE,
            value = BigDecimal("0.05"),
            status = CouponStatusDto.EXPIRED,
            issuedAt = "2023-01-01T00:00:00Z",
            usedAt = null,
            expiredAt = "2023-12-31T23:59:59Z",
            orderId = null,
        ),
    ),
)
