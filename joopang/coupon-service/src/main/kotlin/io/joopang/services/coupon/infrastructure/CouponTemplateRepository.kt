package io.joopang.services.coupon.infrastructure

import io.joopang.services.coupon.domain.CouponTemplate
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface CouponTemplateRepository : JpaRepository<CouponTemplate, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ct from CouponTemplate ct where ct.id = :templateId")
    fun findByIdForUpdate(@Param("templateId") templateId: Long): CouponTemplate?

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "update CouponTemplate ct " +
            "set ct.issuedQuantity = ct.issuedQuantity + 1 " +
            "where ct.id = :templateId and ct.issuedQuantity < ct.totalQuantity",
    )
    fun incrementIssuedQuantity(@Param("templateId") templateId: Long): Int
}
