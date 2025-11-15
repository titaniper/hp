package io.joopang.services.coupon.infrastructure

import io.joopang.services.coupon.domain.Coupon
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Repository

@Repository
class CouponRepository(
    @PersistenceContext private val entityManager: EntityManager,
) {

    open fun findById(couponId: Long): Coupon? =
        entityManager.find(Coupon::class.java, couponId)

    open fun findUserCoupons(userId: Long): List<Coupon> =
        entityManager.createQuery(
            "select c from Coupon c where c.userId = :userId",
            Coupon::class.java,
        )
            .setParameter("userId", userId)
            .resultList

    open fun findUserCoupon(userId: Long, couponId: Long): Coupon? =
        entityManager.createQuery(
            "select c from Coupon c where c.id = :couponId and c.userId = :userId",
            Coupon::class.java,
        )
            .setParameter("couponId", couponId)
            .setParameter("userId", userId)
            .resultList
            .firstOrNull()

    open fun findUserCouponByTemplate(userId: Long, couponTemplateId: Long): Coupon? =
        entityManager.createQuery(
            "select c from Coupon c where c.userId = :userId and c.couponTemplateId = :templateId",
            Coupon::class.java,
        )
            .setParameter("userId", userId)
            .setParameter("templateId", couponTemplateId)
            .resultList
            .firstOrNull()

    fun save(coupon: Coupon): Coupon =
        entityManager.merge(coupon)
}
