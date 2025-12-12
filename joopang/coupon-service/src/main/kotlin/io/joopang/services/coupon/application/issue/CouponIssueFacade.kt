package io.joopang.services.coupon.application.issue

import io.joopang.services.coupon.application.CouponService
import io.joopang.services.coupon.infrastructure.redis.CouponTemplateAvailabilityCache
import io.joopang.services.user.client.UserClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class CouponIssueFacade(
    private val userClient: UserClient,
    private val couponTemplateAvailabilityCache: CouponTemplateAvailabilityCache,
    private val couponIssueCoordinator: CouponIssueCoordinator,
    private val couponService: CouponService,
    @Value("\${coupon.issue.async-enabled:true}") private val asyncEnabled: Boolean,
) {

    /**
     * Application 계층에서 사용자/템플릿 검증과 동기·비동기 분기를 수행해 하위 서비스 로직을 보호한다.
     */
    fun requestIssue(command: CouponService.IssueCouponCommand): Result {
        userClient.ensureUserExists(command.userId)
        val userId = command.userId

        val template = couponTemplateAvailabilityCache.getOrLoad(command.couponTemplateId)
            ?: throw IllegalStateException("쿠폰 템플릿을 찾을 수 없습니다")
        val templateId = template.templateId

        if (!template.canIssue()) {
            throw IllegalStateException("쿠폰이 모두 소진되었거나 발급 기간이 아닙니다")
        }

        if (!asyncEnabled) {
            val output = couponService.issueCoupon(command)
            return Result.Sync(output)
        }

        val queueResult = couponIssueCoordinator.enqueue(
            CouponIssueCoordinator.IssueRequest(
                couponTemplateId = templateId,
                userId = userId,
            ),
        )
        return Result.Async(queueResult)
    }

    sealed interface Result {
        data class Sync(val output: CouponService.IssueCouponOutput) : Result
        data class Async(val queueResult: CouponIssueQueueResult) : Result
    }
}
