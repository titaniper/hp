package io.joopang.services.coupon.infrastructure.jpa

import io.joopang.services.common.domain.Money
import io.joopang.services.coupon.domain.CouponTemplate
import io.joopang.services.coupon.domain.CouponTemplateStatus
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
@Table(name = "coupon_templates")
class CouponTemplateEntity(
    @Id
    @Column(columnDefinition = "BINARY(16)")
    var id: UUID,

    @Column(nullable = false)
    var title: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var type: CouponType,

    @Column(nullable = false, precision = 19, scale = 4)
    var value: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: CouponTemplateStatus,

    @Column(name = "min_amount", precision = 19, scale = 2)
    var minAmount: Money?,

    @Column(name = "max_discount_amount", precision = 19, scale = 2)
    var maxDiscountAmount: Money?,

    @Column(name = "total_quantity", nullable = false)
    var totalQuantity: Int,

    @Column(name = "issued_quantity", nullable = false)
    var issuedQuantity: Int,

    @Column(name = "limit_quantity", nullable = false)
    var limitQuantity: Int,

    @Column(name = "start_at")
    var startAt: Instant?,

    @Column(name = "end_at")
    var endAt: Instant?,
) {
    fun toDomain(): CouponTemplate = CouponTemplate(
        id = id,
        title = title,
        type = type,
        value = value,
        status = status,
        minAmount = minAmount,
        maxDiscountAmount = maxDiscountAmount,
        totalQuantity = totalQuantity,
        issuedQuantity = issuedQuantity,
        limitQuantity = limitQuantity,
        startAt = startAt,
        endAt = endAt,
    )

    companion object {
        fun from(domain: CouponTemplate): CouponTemplateEntity = CouponTemplateEntity(
            id = domain.id,
            title = domain.title,
            type = domain.type,
            value = domain.value,
            status = domain.status,
            minAmount = domain.minAmount,
            maxDiscountAmount = domain.maxDiscountAmount,
            totalQuantity = domain.totalQuantity,
            issuedQuantity = domain.issuedQuantity,
            limitQuantity = domain.limitQuantity,
            startAt = domain.startAt,
            endAt = domain.endAt,
        )
    }
}
