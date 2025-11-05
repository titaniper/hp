package io.joopang.application.coupon

import io.joopang.application.coupon.CouponLockManager
import io.joopang.domain.coupon.Coupon
import io.joopang.domain.coupon.CouponStatus
import io.joopang.domain.coupon.CouponType
import io.joopang.domain.user.UserNotFoundException
import io.joopang.infrastructure.coupon.CouponRepository
import io.joopang.infrastructure.coupon.CouponTemplateRepository
import io.joopang.infrastructure.user.UserRepository
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class CouponUseCase(
    private val couponRepository: CouponRepository,
    private val couponTemplateRepository: CouponTemplateRepository,
    private val userRepository: UserRepository,
    private val couponLockManager: CouponLockManager,
) {

    fun issueCoupon(command: IssueCouponCommand): CouponIssueResult {
        val user = userRepository.findById(command.userId)
            ?: throw UserNotFoundException(command.userId.toString())

        return couponLockManager.withTemplateLock(command.couponTemplateId) {
            val template = couponTemplateRepository.findById(command.couponTemplateId)
                ?: throw IllegalStateException("쿠폰 템플릿을 찾을 수 없습니다")

            val now = Instant.now()
            if (!template.canIssue(now)) {
                throw IllegalStateException("쿠폰이 모두 소진되었거나 발급 기간이 아닙니다")
            }

            val existingCoupon = couponRepository.findUserCouponByTemplate(user.id, template.id)
                ?.takeIf { it.status == CouponStatus.AVAILABLE }
            if (existingCoupon != null) {
                throw IllegalStateException("이미 발급받은 쿠폰입니다")
            }

            val userCoupons = couponRepository.findUserCoupons(user.id)
            val issuedCount = userCoupons.count { it.couponTemplateId == template.id }
            if (!template.canIssueForUser(issuedCount)) {
                throw IllegalStateException("해당 쿠폰 템플릿은 이미 최대 발급 수량에 도달했습니다")
            }

            val updatedTemplate = template.issue()
            couponTemplateRepository.save(updatedTemplate)

            val expiry = template.endAt ?: now.plus(7, ChronoUnit.DAYS)
            val coupon = Coupon(
                id = UUID.randomUUID(),
                userId = user.id,
                couponTemplateId = template.id,
                type = template.type,
                value = template.value,
                status = CouponStatus.AVAILABLE,
                issuedAt = now,
                usedAt = null,
                expiredAt = expiry,
                orderId = null,
            )
            val savedCoupon = couponRepository.save(coupon)

            CouponIssueResult(
                userCouponId = savedCoupon.id,
                couponTemplateId = savedCoupon.couponTemplateId,
                type = savedCoupon.type,
                value = savedCoupon.value,
                status = savedCoupon.status,
                issuedAt = savedCoupon.issuedAt,
                expiredAt = savedCoupon.expiredAt,
                remainingQuantity = updatedTemplate.remainingQuantity(),
            )
        }
    }

    fun getUserCoupons(userId: UUID): List<UserCouponResult> {
        val user = userRepository.findById(userId)
            ?: throw UserNotFoundException(userId.toString())

        val now = Instant.now()
        return couponRepository.findUserCoupons(user.id)
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
            .map { it.toResult() }
    }

    private fun Coupon.toResult(): UserCouponResult =
        UserCouponResult(
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

    data class IssueCouponCommand(
        val couponTemplateId: UUID,
        val userId: UUID,
    )

    data class CouponIssueResult(
        val userCouponId: UUID,
        val couponTemplateId: UUID?,
        val type: CouponType,
        val value: java.math.BigDecimal,
        val status: CouponStatus,
        val issuedAt: Instant,
        val expiredAt: Instant?,
        val remainingQuantity: Int,
    )

    data class UserCouponResult(
        val couponId: UUID,
        val couponTemplateId: UUID?,
        val type: CouponType,
        val value: java.math.BigDecimal,
        val status: CouponStatus,
        val issuedAt: Instant,
        val expiredAt: Instant?,
        val usedAt: Instant?,
        val orderId: UUID?,
    )
}
