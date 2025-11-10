package io.joopang.services.coupon.infrastructure

import io.joopang.services.coupon.domain.CouponTemplate
import io.joopang.services.coupon.infrastructure.jpa.CouponTemplateEntity
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Repository
@Transactional(readOnly = true)
open class CouponTemplateRepository(
    @PersistenceContext private val entityManager: EntityManager,
) {

    open fun findById(templateId: UUID): CouponTemplate? =
        entityManager.find(CouponTemplateEntity::class.java, templateId)?.toDomain()

    @Transactional
    open fun save(template: CouponTemplate): CouponTemplate =
        entityManager.merge(CouponTemplateEntity.from(template)).toDomain()
}
