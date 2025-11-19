package io.joopang.services.coupon.application

import io.joopang.services.coupon.domain.Coupon
import io.joopang.services.coupon.domain.CouponStatus
import io.joopang.services.coupon.domain.CouponType
import io.joopang.services.coupon.infrastructure.CouponRepository
import io.joopang.services.coupon.infrastructure.CouponTemplateRepository
import io.joopang.services.user.domain.UserNotFoundException
import io.joopang.services.user.infrastructure.UserRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
@Transactional(readOnly = true)
class CouponService(
    private val couponRepository: CouponRepository,
    private val couponTemplateRepository: CouponTemplateRepository,
    private val userRepository: UserRepository,
    private val couponLockManager: CouponLockManager,
) {

    @Transactional
    fun issueCoupon(command: IssueCouponCommand): IssueCouponOutput {
        val user = userRepository.findByIdOrNull(command.userId)
            ?: throw UserNotFoundException(command.userId.toString())
        val userId = user.id

        return couponLockManager.withTemplateLock(command.couponTemplateId) {
            val template = couponTemplateRepository.findByIdForUpdate(command.couponTemplateId)
                ?: throw IllegalStateException("쿠폰 템플릿을 찾을 수 없습니다")
            val templateId = template.id

            val now = Instant.now()
            if (!template.canIssue(now)) {
                throw IllegalStateException("쿠폰이 모두 소진되었거나 발급 기간이 아닙니다")
            }

            val existingCoupon = couponRepository.findByUserIdAndCouponTemplateId(userId, templateId)
                ?.takeIf { it.status == CouponStatus.AVAILABLE }
            if (existingCoupon != null) {
                throw IllegalStateException("이미 발급받은 쿠폰입니다")
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

            IssueCouponOutput(
                coupon = savedCoupon.toOutput(),
                remainingQuantity = template.remainingQuantity(),
            )
        }
    }

    @Transactional
    fun getUserCoupons(userId: Long): List<Output> {
        val user = userRepository.findByIdOrNull(userId)
            ?: throw UserNotFoundException(userId.toString())
        val persistedUserId = user.id

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
            id = id,
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
}
