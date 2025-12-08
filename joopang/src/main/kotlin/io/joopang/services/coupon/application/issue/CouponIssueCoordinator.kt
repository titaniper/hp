package io.joopang.services.coupon.application.issue

import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.StreamRecords
import org.springframework.data.redis.core.StreamOperations
import org.springframework.data.redis.core.ZSetOperations
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * API 레이어에서 쿠폰 발급 요청을 Redis 큐와 스트림에 적재한다.
 * Lua 대신 Redis 자료구조 조합으로 처리 순서를 보장한다.
 */
@Component
class CouponIssueCoordinator(
    private val redisTemplate: StringRedisTemplate,
    @Value(QUEUE_KEY_PREFIX) private val queueKeyPrefix: String,
    @Value(QUEUE_TTL_SECONDS) private val queueTtlSeconds: Long,
    @Value(AVERAGE_PROCESSING_MILLIS) private val averageProcessingMillis: Long,
    @Value(REQUEST_STREAM_KEY) private val requestStreamKey: String,
) {

    // redisTemplate.opsForZSet(): Redis Sorted Set 명령(ZADD, ZRANK 등)을 호출하기 위한 helper. 실제 데이터는 Redis 서버에 존재한다.
    private val queueOps: ZSetOperations<String, String> by lazy { redisTemplate.opsForZSet() }
    // opsForStream(): Redis Stream 명령(XADD, XREAD 등)을 수행하는 helper 객체.
    private val streamOps: StreamOperations<String, String, String> by lazy { redisTemplate.opsForStream() }

    fun enqueue(command: IssueRequest): CouponIssueQueueResult {
        val requestId = UUID.randomUUID().toString()
        val queueKey = queueKey(command.couponTemplateId)
        val now = Instant.now()

        queueOps.add(queueKey, requestId, now.toEpochMilli().toDouble())
        redisTemplate.expire(queueKey, Duration.ofSeconds(queueTtlSeconds))

        // StreamRecords.mapBacked(...) 는 Redis Stream에 넣을 필드-값 쌍을 만드는 헬퍼다.
        // 결국 "requestId=..., templateId=..., userId=..." 형태로 XADD에 전달된다.
        val record: MapRecord<String, String, String> =
            StreamRecords.mapBacked<String, String, String>(
                mapOf(
                    REQUEST_ID_FIELD to requestId,
                    TEMPLATE_ID_FIELD to command.couponTemplateId.toString(),
                    USER_ID_FIELD to command.userId.toString(),
                    REQUESTED_AT_FIELD to now.toString(),
                ),
            ).withStreamKey(requestStreamKey)
        streamOps.add(record)

        val queueRank = queueOps.rank(queueKey, requestId) ?: 0L
        val estimatedWaitMillis = queueRank * averageProcessingMillis

        return CouponIssueQueueResult(
            requestId = requestId,
            queueRank = queueRank,
            estimatedWaitMillis = estimatedWaitMillis,
            couponTemplateId = command.couponTemplateId,
        )
    }

    fun complete(templateId: Long, requestId: String) {
        queueOps.remove(queueKey(templateId), requestId)
    }

    private fun queueKey(templateId: Long): String = "$queueKeyPrefix:$templateId"

    /**
     * Facade/Service에서 enqueue할 때 전달하는 최소한의 파라미터 집합.
     */
    data class IssueRequest(
        val couponTemplateId: Long,
        val userId: Long,
    )

    companion object {
        const val REQUEST_ID_FIELD = "requestId"
        const val TEMPLATE_ID_FIELD = "templateId"
        const val USER_ID_FIELD = "userId"
        const val REQUESTED_AT_FIELD = "requestedAt"

        // 템플릿별 대기열 Sorted Set 키 prefix (예: coupon:queue-position:{templateId})
        const val QUEUE_KEY_PREFIX = "\${coupon.issue.queue.prefix:coupon:queue-position}"
        // 이벤트 종료 후 자연스럽게 정리되도록 대기열 TTL
        const val QUEUE_TTL_SECONDS = "\${coupon.issue.queue.ttl-seconds:86400}"
        // 평균 처리 속도를 기반으로한 대기 시간 추정치
        const val AVERAGE_PROCESSING_MILLIS = "\${coupon.issue.queue.average-processing-millis:50}"
        // 발급 워커가 소비하는 Stream 키
        const val REQUEST_STREAM_KEY = "\${coupon.issue.stream.request-key:coupon:request-stream}"
    }
}

/**
 * API 응답 DTO에 사용되는 대기열/예상 처리 시간 정보.
 */
data class CouponIssueQueueResult(
    val requestId: String,
    val queueRank: Long,
    val estimatedWaitMillis: Long,
    val couponTemplateId: Long,
)
