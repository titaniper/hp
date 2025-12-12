package io.joopang.services.coupon.contract

import java.io.Serializable
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class CouponCommandType {
    VALIDATE,
    MARK_USED,
}

data class CouponCommand(
    val requestId: String = UUID.randomUUID().toString(),
    val type: CouponCommandType,
    val couponId: Long,
    val userId: Long,
    val orderId: Long? = null,
) : Serializable

data class CouponCommandResult(
    val requestId: String,
    val success: Boolean,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val coupon: CouponSnapshot? = null,
) : Serializable

data class CouponSnapshot(
    val id: Long,
    val userId: Long,
    val couponTemplateId: Long?,
    val type: CouponType,
    val status: CouponStatus,
    val value: BigDecimal,
    val issuedAt: Instant,
    val usedAt: Instant?,
    val expiredAt: Instant?,
) : Serializable
