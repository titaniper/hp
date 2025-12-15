package io.joopang.services.coupon.application

import io.joopang.common.lock.DistributedLock
import io.joopang.services.common.domain.requireId
import io.joopang.services.coupon.contract.CouponStatus
import io.joopang.services.coupon.contract.CouponType
import io.joopang.services.coupon.domain.Coupon
import io.joopang.services.coupon.infrastructure.CouponRepository
import io.joopang.services.coupon.infrastructure.CouponTemplateRepository
import io.joopang.services.coupon.infrastructure.redis.CouponTemplateAvailabilityCache
import io.joopang.services.user.client.UserClient
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class CouponService(
    private val couponRepository: CouponRepository,
    private val couponTemplateRepository: CouponTemplateRepository,
    private val couponTemplateAvailabilityCache: CouponTemplateAvailabilityCache,
    private val userClient: UserClient,
) {

    @DistributedLock(
        prefix = COUPON_LOCK_PREFIX,
        key = "#command.couponTemplateId",
        waitTime = LOCK_WAIT_SECONDS,
        leaseTime = LOCK_LEASE_SECONDS,
        failureMessage = "쿠폰 발급 요청이 많습니다. 잠시 후 다시 시도해주세요.",
    )
    @Transactional
    /**
     * Domain 서비스는 락/트랜잭션 경계 안에서 발급 규칙을 검증하고 실제 쿠폰 엔티티를 저장한다.
     */
    fun issueCoupon(command: IssueCouponCommand): IssueCouponOutput =
        issueCouponInternal(command)

    @Transactional
    fun issueCouponWithoutLock(command: IssueCouponCommand): IssueCouponOutput =
        issueCouponInternal(command)

    private fun issueCouponInternal(command: IssueCouponCommand): IssueCouponOutput {
        userClient.ensureUserExists(command.userId)
        val userId = command.userId

        val template = couponTemplateRepository.findByIdForUpdate(command.couponTemplateId)
            ?: throw IllegalStateException("쿠폰 템플릿을 찾을 수 없습니다")
        val templateId = template.requireId()

        val now = Instant.now()
        if (!template.canIssue(now)) {
            throw IllegalStateException("쿠폰이 모두 소진되었거나 발급 기간이 아닙니다")
        }

        val userCoupons = couponRepository.findAllByUserId(userId)
        val issuedCount = userCoupons.count { it.couponTemplateId == templateId }
        if (!template.canIssueForUser(issuedCount)) {
            throw IllegalStateException("해당 쿠폰 템플릿은 이미 최대 발급 수량에 도달했습니다")
        }

        val updated = couponTemplateRepository.incrementIssuedQuantity(templateId) > 0
        if (!updated) {
            throw IllegalStateException("쿠폰이 모두 소진되었거나 발급 기간이 아닙니다")
        }
        template.issue()
        couponTemplateAvailabilityCache.saveSnapshot(template)

        val expiry = template.endAt ?: now.plus(7, ChronoUnit.DAYS)
        val coupon = Coupon(
            userId = userId,
            couponTemplateId = templateId,
            type = template.type,
            value = template.value,
            status = CouponStatus.AVAILABLE,
            issuedAt = now,
            usedAt = null,
            expiredAt = expiry,
            orderId = null,
        )
        val savedCoupon = couponRepository.save(coupon)

        return IssueCouponOutput(
            coupon = savedCoupon.toOutput(),
            remainingQuantity = template.remainingQuantity(),
        )
    }

    @Transactional
    fun getUserCoupons(userId: Long): List<Output> {
        userClient.ensureUserExists(userId)
        val persistedUserId = userId

        val now = Instant.now()
        return couponRepository.findAllByUserId(persistedUserId)
            .map { coupon ->
                val shouldExpire = coupon.status == CouponStatus.AVAILABLE &&
                    coupon.expiredAt?.let { now.isAfter(it) } == true
                val current = if (shouldExpire) {
                    val expired = coupon.expire()
                    couponRepository.save(expired)
                } else {
                    coupon
                }
                current
            }
            .map { it.toOutput() }
    }

    private fun Coupon.toOutput(): Output =
        Output(
            id = requireId(),
            couponTemplateId = couponTemplateId,
            type = type,
            value = value,
            status = status,
            issuedAt = issuedAt,
            expiredAt = expiredAt,
            usedAt = usedAt,
            orderId = orderId,
        )

    data class IssueCouponCommand(
        val couponTemplateId: Long,
        val userId: Long,
    )

    data class IssueCouponOutput(
        val coupon: Output,
        val remainingQuantity: Int,
    )

    data class Output(
        val id: Long,
        val couponTemplateId: Long?,
        val type: CouponType,
        val value: BigDecimal,
        val status: CouponStatus,
        val issuedAt: Instant,
        val expiredAt: Instant?,
        val usedAt: Instant?,
        val orderId: Long?,
    )

    companion object {
        private const val COUPON_LOCK_PREFIX = "lock:coupon-template:"
        private const val LOCK_WAIT_SECONDS = 2L
        private const val LOCK_LEASE_SECONDS = 5L
    }
}
