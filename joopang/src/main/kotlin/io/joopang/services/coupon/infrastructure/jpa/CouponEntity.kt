package io.joopang.services.coupon.infrastructure.jpa

import io.joopang.services.coupon.domain.Coupon
import io.joopang.services.coupon.domain.CouponStatus
import io.joopang.services.coupon.domain.CouponType
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
class CouponEntity(
    @Id
    @Column(columnDefinition = "BINARY(16)")
    var id: UUID,

    @Column(name = "user_id", columnDefinition = "BINARY(16)", nullable = false)
    var userId: UUID,

    @Column(name = "coupon_template_id", columnDefinition = "BINARY(16)")
    var couponTemplateId: UUID?,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var type: CouponType,

    @Column(nullable = false, precision = 19, scale = 4)
    var value: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: CouponStatus,

    @Column(name = "issued_at", nullable = false)
    var issuedAt: Instant,

    @Column(name = "used_at")
    var usedAt: Instant?,

    @Column(name = "expired_at")
    var expiredAt: Instant?,

    @Column(name = "order_id", columnDefinition = "BINARY(16)")
    var orderId: UUID?,
) {
    fun toDomain(): Coupon = Coupon(
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

    companion object {
        fun from(domain: Coupon): CouponEntity = CouponEntity(
            id = domain.id,
            userId = domain.userId,
            couponTemplateId = domain.couponTemplateId,
            type = domain.type,
            value = domain.value,
            status = domain.status,
            issuedAt = domain.issuedAt,
            usedAt = domain.usedAt,
            expiredAt = domain.expiredAt,
            orderId = domain.orderId,
        )
    }
}
