package io.joopang.services.coupon.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "coupons")
data class Coupon(
    @Id
    @Column(columnDefinition = "BINARY(16)")
    var id: UUID = UUID(0L, 0L),

    @Column(name = "user_id", columnDefinition = "BINARY(16)", nullable = false)
    var userId: UUID = UUID(0L, 0L),

    @Column(name = "coupon_template_id", columnDefinition = "BINARY(16)")
    var couponTemplateId: UUID? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var type: CouponType = CouponType.AMOUNT,

    @Column(nullable = false, precision = 19, scale = 4)
    var value: BigDecimal = BigDecimal.ZERO,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: CouponStatus = CouponStatus.AVAILABLE,

    @Column(name = "issued_at", nullable = false)
    var issuedAt: Instant = Instant.EPOCH,

    @Column(name = "used_at")
    var usedAt: Instant? = null,

    @Column(name = "expired_at")
    var expiredAt: Instant? = null,

    @Column(name = "order_id", columnDefinition = "BINARY(16)")
    var orderId: UUID? = null,
) {

    init {
        require(value >= BigDecimal.ZERO) { "Coupon value cannot be negative" }
    }

    fun isUsed(): Boolean = status == CouponStatus.USED

    fun isExpired(referenceTime: Instant = Instant.now()): Boolean =
        status == CouponStatus.EXPIRED || expiredAt?.let { referenceTime.isAfter(it) } ?: false

    fun isAvailable(referenceTime: Instant = Instant.now()): Boolean =
        status == CouponStatus.AVAILABLE && !isExpired(referenceTime)

    fun markUsed(orderId: UUID, usedAt: Instant = Instant.now()): Coupon =
        copy(
            status = CouponStatus.USED,
            usedAt = usedAt,
            orderId = orderId,
        )

    fun expire(): Coupon =
        if (status == CouponStatus.EXPIRED) this else copy(status = CouponStatus.EXPIRED)
}
