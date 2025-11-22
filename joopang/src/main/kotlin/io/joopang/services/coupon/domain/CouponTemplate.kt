package io.joopang.services.coupon.domain

import io.joopang.services.common.domain.BaseEntity
import io.joopang.services.common.domain.Money
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "coupon_templates")
class CouponTemplate(
    id: Long? = null,
    @Column(nullable = false)
    var title: String = "TEMPLATE",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var type: CouponType = CouponType.AMOUNT,

    @Column(nullable = false, precision = 19, scale = 4)
    var value: BigDecimal = BigDecimal.ZERO,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: CouponTemplateStatus = CouponTemplateStatus.DRAFT,

    @Column(name = "min_amount", precision = 19, scale = 2)
    var minAmount: Money? = null,

    @Column(name = "max_discount_amount", precision = 19, scale = 2)
    var maxDiscountAmount: Money? = null,

    @Column(name = "total_quantity", nullable = false)
    var totalQuantity: Int = 0,

    @Column(name = "issued_quantity", nullable = false)
    var issuedQuantity: Int = 0,

    @Column(name = "limit_quantity", nullable = false)
    var limitQuantity: Int = 0,

    @Column(name = "start_at")
    var startAt: Instant? = null,

    @Column(name = "end_at")
    var endAt: Instant? = null,
) : BaseEntity(id) {

    init {
        require(title.isNotBlank()) { "Coupon template title must not be blank" }
        require(totalQuantity >= 0) { "Total quantity cannot be negative" }
        require(issuedQuantity >= 0) { "Issued quantity cannot be negative" }
        require(issuedQuantity <= totalQuantity) { "Issued quantity cannot exceed total quantity" }
        require(limitQuantity >= 0) { "Limit quantity cannot be negative" }
        require(value >= BigDecimal.ZERO) { "Coupon value cannot be negative" }
    }

    fun isActive(at: Instant = Instant.now()): Boolean =
        status == CouponTemplateStatus.ACTIVE &&
            (startAt == null || !at.isBefore(startAt)) &&
            (endAt == null || !at.isAfter(endAt))

    fun remainingQuantity(): Int = totalQuantity - issuedQuantity

    fun canIssue(at: Instant = Instant.now()): Boolean =
        isActive(at) && remainingQuantity() > 0

    fun canIssueForUser(currentUserIssuedCount: Int): Boolean =
        limitQuantity <= 0 || currentUserIssuedCount < limitQuantity

    fun copy(
        id: Long? = this.id,
        title: String = this.title,
        type: CouponType = this.type,
        value: BigDecimal = this.value,
        status: CouponTemplateStatus = this.status,
        minAmount: Money? = this.minAmount,
        maxDiscountAmount: Money? = this.maxDiscountAmount,
        totalQuantity: Int = this.totalQuantity,
        issuedQuantity: Int = this.issuedQuantity,
        limitQuantity: Int = this.limitQuantity,
        startAt: Instant? = this.startAt,
        endAt: Instant? = this.endAt,
    ): CouponTemplate =
        CouponTemplate(
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

    fun issue(): CouponTemplate {
        require(remainingQuantity() > 0) { "No coupons remaining for template $id" }
        issuedQuantity += 1
        return this
    }

    @Suppress("unused")
    constructor() : this(
        id = null,
        title = "TEMPLATE",
        type = CouponType.AMOUNT,
        value = BigDecimal.ZERO,
        status = CouponTemplateStatus.DRAFT,
        minAmount = null,
        maxDiscountAmount = null,
        totalQuantity = 0,
        issuedQuantity = 0,
        limitQuantity = 0,
        startAt = null,
        endAt = null,
    )
}
