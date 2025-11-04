package io.joopang.domain.coupon

import java.util.UUID

interface CouponTemplateRepository {
    fun findById(templateId: UUID): CouponTemplate?
    fun save(template: CouponTemplate): CouponTemplate
}
