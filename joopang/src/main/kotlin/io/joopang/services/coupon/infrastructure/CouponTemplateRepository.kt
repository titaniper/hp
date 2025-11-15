package io.joopang.services.coupon.infrastructure

import io.joopang.services.coupon.domain.CouponTemplate
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
@Transactional(readOnly = true)
open class CouponTemplateRepository(
    @PersistenceContext private val entityManager: EntityManager,
) {

    open fun findById(templateId: Long): CouponTemplate? =
        entityManager.find(CouponTemplate::class.java, templateId)

    @Transactional
    open fun save(template: CouponTemplate): CouponTemplate =
        entityManager.merge(template)

    @Transactional
    open fun incrementIssuedQuantity(templateId: Long): Boolean =
        entityManager.createNativeQuery(
            """
                update coupon_templates
                set issued_quantity = issued_quantity + 1
                where id = :templateId and issued_quantity < total_quantity
            """.trimIndent(),
        )
            .setParameter("templateId", templateId)
            .executeUpdate() == 1
}
