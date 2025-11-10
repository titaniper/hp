package io.joopang.services.coupon.infrastructure

import io.joopang.services.coupon.domain.Coupon
import io.joopang.services.coupon.infrastructure.jpa.CouponEntity
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Repository
@Transactional(readOnly = true)
open class CouponRepository(
    @PersistenceContext private val entityManager: EntityManager,
) {

    open fun findById(couponId: UUID): Coupon? =
        entityManager.find(CouponEntity::class.java, couponId)?.toDomain()

    open fun findUserCoupons(userId: UUID): List<Coupon> =
        entityManager.createQuery(
            "select c from CouponEntity c where c.userId = :userId",
            CouponEntity::class.java,
        )
            .setParameter("userId", userId)
            .resultList
            .map(CouponEntity::toDomain)

    open fun findUserCoupon(userId: UUID, couponId: UUID): Coupon? =
        entityManager.createQuery(
            "select c from CouponEntity c where c.id = :couponId and c.userId = :userId",
            CouponEntity::class.java,
        )
            .setParameter("couponId", couponId)
            .setParameter("userId", userId)
            .resultList
            .firstOrNull()
            ?.toDomain()

    open fun findUserCouponByTemplate(userId: UUID, couponTemplateId: UUID): Coupon? =
        entityManager.createQuery(
            "select c from CouponEntity c where c.userId = :userId and c.couponTemplateId = :templateId",
            CouponEntity::class.java,
        )
            .setParameter("userId", userId)
            .setParameter("templateId", couponTemplateId)
            .resultList
            .firstOrNull()
            ?.toDomain()

    @Transactional
    open fun save(coupon: Coupon): Coupon =
        entityManager.merge(CouponEntity.from(coupon)).toDomain()
}
