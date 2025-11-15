package io.joopang.services.coupon.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(
    name = "coupons",
    indexes = [
        Index(
            name = "idx_coupons_user_template",
            columnList = "user_id, coupon_template_id",
        ),
    ],
)
class Coupon(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "BIGINT")
    var id: Long = 0,

    @Column(name = "user_id", columnDefinition = "BIGINT", nullable = false)
    var userId: Long = 0,

    @Column(name = "coupon_template_id", columnDefinition = "BIGINT")
    var couponTemplateId: Long? = null,

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

    @Column(name = "order_id", columnDefinition = "BIGINT")
    var orderId: Long? = null,
) {

    init {
        require(value >= BigDecimal.ZERO) { "Coupon value cannot be negative" }
    }

    fun isUsed(): Boolean = status == CouponStatus.USED

    fun isExpired(referenceTime: Instant = Instant.now()): Boolean =
        status == CouponStatus.EXPIRED || expiredAt?.let { referenceTime.isAfter(it) } ?: false

    fun isAvailable(referenceTime: Instant = Instant.now()): Boolean =
        status == CouponStatus.AVAILABLE && !isExpired(referenceTime)

    fun markUsed(orderId: Long, usedAt: Instant = Instant.now()): Coupon =
        copy(
            status = CouponStatus.USED,
            usedAt = usedAt,
            orderId = orderId,
        )

    fun expire(): Coupon =
        if (status == CouponStatus.EXPIRED) this else copy(status = CouponStatus.EXPIRED)

    fun copy(
        id: Long = this.id,
        userId: Long = this.userId,
        couponTemplateId: Long? = this.couponTemplateId,
        type: CouponType = this.type,
        value: BigDecimal = this.value,
        status: CouponStatus = this.status,
        issuedAt: Instant = this.issuedAt,
        usedAt: Instant? = this.usedAt,
        expiredAt: Instant? = this.expiredAt,
        orderId: Long? = this.orderId,
    ): Coupon =
        Coupon(
            id = id,
            userId = userId,
            couponTemplateId = couponTemplateId,
            type = type,
            value = value,
            status = status,
            issuedAt = issuedAt,
            usedAt = usedAt,
            expiredAt = expiredAt,
            orderId = orderId,
        )

    @Suppress("unused")
    constructor() : this(
        id = 0,
        userId = 0,
        couponTemplateId = null,
        type = CouponType.AMOUNT,
        value = BigDecimal.ZERO,
        status = CouponStatus.AVAILABLE,
        issuedAt = Instant.EPOCH,
        usedAt = null,
        expiredAt = null,
        orderId = null,
    )
}
