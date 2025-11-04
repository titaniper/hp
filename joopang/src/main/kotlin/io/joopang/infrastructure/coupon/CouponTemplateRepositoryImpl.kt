package io.joopang.infrastructure.coupon

import io.joopang.domain.common.Money
import io.joopang.domain.coupon.CouponTemplate
import io.joopang.domain.coupon.CouponTemplateRepository
import io.joopang.domain.coupon.CouponTemplateStatus
import io.joopang.domain.coupon.CouponType
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Repository
class CouponTemplateRepositoryImpl : CouponTemplateRepository {

    private val store = ConcurrentHashMap<UUID, CouponTemplate>()

    init {
        seed()
    }

    override fun findById(templateId: UUID): CouponTemplate? = store[templateId]

    override fun save(template: CouponTemplate): CouponTemplate {
        store[template.id] = template
        return template
    }

    private fun seed() {
        val percentageTemplateId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
        store[percentageTemplateId] = CouponTemplate(
            id = percentageTemplateId,
            title = "신규 가입 10% 할인",
            type = CouponType.PERCENTAGE,
            value = BigDecimal("0.10"),
            status = CouponTemplateStatus.ACTIVE,
            minAmount = Money.of(50_000L),
            maxDiscountAmount = Money.of(30_000L),
            totalQuantity = 1_000,
            issuedQuantity = 150,
            limitQuantity = 1,
            startAt = Instant.now().minus(10, ChronoUnit.DAYS),
            endAt = Instant.now().plus(20, ChronoUnit.DAYS),
        )

        val amountTemplateId = UUID.fromString("bbbbbbbb-cccc-dddd-eeee-ffffffffffff")
        store[amountTemplateId] = CouponTemplate(
            id = amountTemplateId,
            title = "5,000원 즉시 할인",
            type = CouponType.AMOUNT,
            value = BigDecimal("5000"),
            status = CouponTemplateStatus.ACTIVE,
            minAmount = Money.of(20_000L),
            maxDiscountAmount = null,
            totalQuantity = 5_000,
            issuedQuantity = 2_345,
            limitQuantity = 2,
            startAt = Instant.now().minus(5, ChronoUnit.DAYS),
            endAt = Instant.now().plus(30, ChronoUnit.DAYS),
        )
    }
}
