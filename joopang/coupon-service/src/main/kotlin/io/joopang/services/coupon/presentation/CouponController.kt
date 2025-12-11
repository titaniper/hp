package io.joopang.services.coupon.presentation

import io.joopang.services.coupon.application.CouponService
import io.joopang.services.coupon.application.issue.CouponIssueFacade
import io.joopang.services.coupon.application.issue.CouponIssueQueueResult
import io.joopang.services.coupon.contract.CouponStatus
import io.joopang.services.coupon.contract.CouponType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.Instant

@RestController
@RequestMapping("/api")
class CouponController(
    private val couponService: CouponService,
    private val couponIssueFacade: CouponIssueFacade,
) {

    @PostMapping("/coupons/{templateId}/issue")
    /**
     * Presentation 계층은 HTTP 파라미터만 명세하고 비즈니스 플로우는 CouponIssueFacade에 위임한다.
     */
    fun issueCoupon(
        @PathVariable("templateId") couponTemplateId: Long,
        @RequestBody request: CouponIssueRequest,
    ): CouponIssueResponse {
        val command = CouponService.IssueCouponCommand(
            couponTemplateId = couponTemplateId,
            userId = request.userId,
        )
        return when (val result = couponIssueFacade.requestIssue(command)) {
            is CouponIssueFacade.Result.Sync -> result.output.toResponse()
            is CouponIssueFacade.Result.Async -> result.queueResult.toResponse()
        }
    }

    @GetMapping("/users/{userId}/coupons")
    fun getUserCoupons(
        @PathVariable userId: Long,
    ): List<UserCouponResponse> =
        couponService
            .getUserCoupons(userId)
            .map { it.toResponse() }

    private fun CouponService.IssueCouponOutput.toResponse(): CouponIssueResponse =
        CouponIssueResponse(
            userCouponId = coupon.id,
            couponTemplateId = coupon.couponTemplateId,
            type = coupon.type,
            value = coupon.value,
            status = coupon.status,
            issuedAt = coupon.issuedAt,
            expiredAt = coupon.expiredAt,
            remainingQuantity = remainingQuantity,
            queueStatus = CouponIssueQueueStatus.COMPLETED,
        )

    private fun CouponIssueQueueResult.toResponse(): CouponIssueResponse =
        CouponIssueResponse(
            couponTemplateId = couponTemplateId,
            requestId = requestId,
            queueRank = queueRank,
            estimatedWaitMillis = estimatedWaitMillis,
            queueStatus = CouponIssueQueueStatus.QUEUED,
        )

    private fun CouponService.Output.toResponse(): UserCouponResponse =
        UserCouponResponse(
            couponId = id,
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
    val userId: Long,
)

data class CouponIssueResponse(
    val userCouponId: Long? = null,
    val couponTemplateId: Long? = null,
    val type: CouponType? = null,
    val value: BigDecimal? = null,
    val status: CouponStatus? = null,
    val issuedAt: Instant? = null,
    val expiredAt: Instant? = null,
    val remainingQuantity: Int? = null,
    val requestId: String? = null,
    val queueRank: Long? = null,
    val estimatedWaitMillis: Long? = null,
    val queueStatus: CouponIssueQueueStatus,
)

enum class CouponIssueQueueStatus {
    QUEUED,
    COMPLETED,
}

data class UserCouponResponse(
    val couponId: Long,
    val couponTemplateId: Long?,
    val type: CouponType,
    val value: BigDecimal,
    val status: CouponStatus,
    val issuedAt: Instant,
    val expiredAt: Instant?,
    val usedAt: Instant?,
    val orderId: Long?,
)
