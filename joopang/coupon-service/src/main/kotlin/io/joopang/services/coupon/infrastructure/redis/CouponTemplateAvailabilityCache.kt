package io.joopang.services.coupon.infrastructure.redis

import io.joopang.services.common.domain.requireId
import io.joopang.services.coupon.domain.CouponTemplate
import io.joopang.services.coupon.domain.CouponTemplateStatus
import io.joopang.services.coupon.infrastructure.CouponTemplateRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.HashOperations
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

@Component
class CouponTemplateAvailabilityCache(
    private val redisTemplate: StringRedisTemplate,
    private val couponTemplateRepository: CouponTemplateRepository,
    @Value("\${coupon.template.cache.ttl-seconds:30}") private val ttlSeconds: Long,
) {

    private val hashOps: HashOperations<String, String, String> by lazy { redisTemplate.opsForHash() }

    fun getOrLoad(templateId: Long): CouponTemplateAvailability? {
        val cached = readFromCache(templateId)
        if (cached != null) {
            return cached
        }
        return refresh(templateId)
    }

    fun refresh(templateId: Long): CouponTemplateAvailability? {
        val template = couponTemplateRepository.findByIdOrNull(templateId) ?: return null
        return saveSnapshot(template)
    }

    fun incrementIssuedQuantity(templateId: Long) {
        val key = cacheKey(templateId)
        if (redisTemplate.hasKey(key) == true) {
            hashOps.increment(key, ISSUED_QUANTITY_FIELD, 1L)
            redisTemplate.expire(key, Duration.ofSeconds(ttlSeconds))
        }
    }

    fun saveSnapshot(template: CouponTemplate): CouponTemplateAvailability {
        val availability = CouponTemplateAvailability.from(template)
        writeToCache(availability)
        return availability
    }

    private fun readFromCache(templateId: Long): CouponTemplateAvailability? {
        val key = cacheKey(templateId)
        val entries = hashOps.entries(key)
        if (entries.isNullOrEmpty()) {
            return null
        }
        val status = entries[STATUS_FIELD]?.let { runCatching { CouponTemplateStatus.valueOf(it) }.getOrNull() } ?: return null
        return CouponTemplateAvailability(
            templateId = templateId,
            status = status,
            totalQuantity = entries[TOTAL_QUANTITY_FIELD]?.toIntOrNull() ?: 0,
            issuedQuantity = entries[ISSUED_QUANTITY_FIELD]?.toIntOrNull() ?: 0,
            limitQuantity = entries[LIMIT_PER_USER_FIELD]?.toIntOrNull() ?: 0,
            startAt = entries[START_AT_FIELD]?.toLongOrNull()?.let(Instant::ofEpochMilli),
            endAt = entries[END_AT_FIELD]?.toLongOrNull()?.let(Instant::ofEpochMilli),
        )
    }

    private fun writeToCache(availability: CouponTemplateAvailability) {
        val key = cacheKey(availability.templateId)
        val payload = mapOf(
            STATUS_FIELD to availability.status.name,
            TOTAL_QUANTITY_FIELD to availability.totalQuantity.toString(),
            ISSUED_QUANTITY_FIELD to availability.issuedQuantity.toString(),
            LIMIT_PER_USER_FIELD to availability.limitQuantity.toString(),
            START_AT_FIELD to (availability.startAt?.toEpochMilli()?.toString() ?: ""),
            END_AT_FIELD to (availability.endAt?.toEpochMilli()?.toString() ?: ""),
        )
        hashOps.putAll(key, payload)
        redisTemplate.expire(key, Duration.ofSeconds(ttlSeconds))
    }

    private fun cacheKey(templateId: Long): String = "$TEMPLATE_CACHE_KEY_PREFIX:$templateId"

    companion object {
        private const val TEMPLATE_CACHE_KEY_PREFIX = "coupon:template"
        private const val STATUS_FIELD = "status"
        private const val TOTAL_QUANTITY_FIELD = "totalQuantity"
        private const val ISSUED_QUANTITY_FIELD = "issuedQuantity"
        private const val LIMIT_PER_USER_FIELD = "limitQuantity"
        private const val START_AT_FIELD = "startAt"
        private const val END_AT_FIELD = "endAt"
    }
}

data class CouponTemplateAvailability(
    val templateId: Long,
    val status: CouponTemplateStatus,
    val totalQuantity: Int,
    val issuedQuantity: Int,
    val limitQuantity: Int,
    val startAt: Instant?,
    val endAt: Instant?,
) {
    fun remainingQuantity(): Int = (totalQuantity - issuedQuantity).coerceAtLeast(0)

    fun canIssue(now: Instant = Instant.now()): Boolean =
        status == CouponTemplateStatus.ACTIVE &&
            remainingQuantity() > 0 &&
            (startAt == null || !now.isBefore(startAt)) &&
            (endAt == null || !now.isAfter(endAt))

    fun canIssueForUser(currentIssuedCount: Int): Boolean =
        limitQuantity <= 0 || currentIssuedCount < limitQuantity

    companion object {
        fun from(template: CouponTemplate): CouponTemplateAvailability =
            CouponTemplateAvailability(
                templateId = template.requireId(),
                status = template.status,
                totalQuantity = template.totalQuantity,
                issuedQuantity = template.issuedQuantity,
                limitQuantity = template.limitQuantity,
                startAt = template.startAt,
                endAt = template.endAt,
            )
    }
}
