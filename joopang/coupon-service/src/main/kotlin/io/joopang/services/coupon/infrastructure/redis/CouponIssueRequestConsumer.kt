package io.joopang.services.coupon.infrastructure.redis

import io.joopang.services.coupon.application.CouponService
import io.joopang.services.coupon.application.issue.CouponIssueCoordinator
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.stream.Consumer
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.connection.stream.RecordId
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.connection.stream.StreamRecords
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.StreamOperations
import org.springframework.stereotype.Component
import org.springframework.data.redis.stream.StreamListener
import org.springframework.data.redis.stream.StreamMessageListenerContainer
import java.time.Duration
import java.util.UUID

@Component
class CouponIssueRequestConsumer(
    redisConnectionFactory: RedisConnectionFactory,
    private val redisTemplate: StringRedisTemplate,
    private val couponService: CouponService,
    private val couponIssueCoordinator: CouponIssueCoordinator,
    @Value("\${coupon.issue.worker.enabled:true}") private val workerEnabled: Boolean,
    @Value("\${coupon.issue.worker.poll-timeout-millis:500}") private val pollTimeoutMillis: Long,
    @Value("\${coupon.issue.stream.request-key:coupon:request-stream}") private val requestStreamKey: String,
    @Value("\${coupon.issue.stream.issue-key:coupon:issue-stream}") private val issueStreamKey: String,
    @Value("\${coupon.issue.stream.group:coupon-issue-group}") private val streamGroup: String,
    @Value("\${coupon.issue.stream.consumer:coupon-issue-consumer}") private val consumerName: String,
) : InitializingBean, AutoCloseable {

    private val log = LoggerFactory.getLogger(javaClass)

    private val streamOps: StreamOperations<String, String, String> by lazy { redisTemplate.opsForStream() }

    private val container: StreamMessageListenerContainer<String, MapRecord<String, String, String>> =
        StreamMessageListenerContainer.create(
            redisConnectionFactory,
            StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                .builder()
                .pollTimeout(Duration.ofMillis(pollTimeoutMillis))
                .build(),
        )

    private var started = false

    override fun afterPropertiesSet() {
        if (!workerEnabled) {
            log.info("Coupon issue worker disabled via configuration")
            return
        }
        createGroupIfNecessary()
        val consumerId = "$consumerName-${UUID.randomUUID()}"
        container.receive(
            Consumer.from(streamGroup, consumerId),
            StreamOffset.create(requestStreamKey, ReadOffset.lastConsumed()),
            StreamListener<String, MapRecord<String, String, String>> { record ->
                handleRecord(record)
            },
        )
        container.start()
        started = true
        log.info("Coupon issue worker started. consumer={} group={} stream={} pollTimeout={}ms", consumerId, streamGroup, requestStreamKey, pollTimeoutMillis)
    }

    private fun createGroupIfNecessary() {
        // Worker는 Redis Stream 컨슈머 그룹 기반으로 돌아가므로,
        // 그룹/스트림이 없으면 초기화 단계에서 만들어 둔다.
        if (!redisTemplate.hasKey(requestStreamKey)) {
            streamOps.add(
                StreamRecords.mapBacked<String, String, String>(mapOf("bootstrap" to "1"))
                    .withStreamKey(requestStreamKey),
            )
        }
        kotlin.runCatching {
            streamOps.createGroup(requestStreamKey, ReadOffset.latest(), streamGroup)
        }.onFailure { throwable ->
            if (!throwable.message.orEmpty().contains("BUSYGROUP")) {
                throw throwable
            }
        }
    }

    private fun handleRecord(record: MapRecord<String, String, String>) {
        // Stream 엔트리에는 queue enqueue 시 기록했던 requestId/templateId/userId가 들어있다.
        val requestId = record.value[CouponIssueCoordinator.REQUEST_ID_FIELD]
        val templateId = record.value[CouponIssueCoordinator.TEMPLATE_ID_FIELD]?.toLongOrNull()
        val userId = record.value[CouponIssueCoordinator.USER_ID_FIELD]?.toLongOrNull()

        if (requestId == null || templateId == null || userId == null) {
            ack(record.id)
            return
        }

        try {
            // Redis 큐에서 직렬화된 요청이므로 별도의 분산락 없이 DB 비관적 락으로만 발급을 수행한다.
            val output = couponService.issueCouponWithoutLock(
                CouponService.IssueCouponCommand(
                    couponTemplateId = templateId,
                    userId = userId,
                ),
            )
            publishResult(
                requestId = requestId,
                recordId = record.id,
                status = "SUCCESS",
                payload = mapOf(
                    "couponId" to output.coupon.id.toString(),
                    "templateId" to (output.coupon.couponTemplateId?.toString() ?: ""),
                    "remainingQuantity" to output.remainingQuantity.toString(),
                ),
            )
        } catch (ex: Exception) {
            log.warn("Failed to process coupon issue request. requestId={}", requestId, ex)
            publishResult(
                requestId = requestId,
                recordId = record.id,
                status = "FAILED",
                payload = mapOf(
                    "error" to ex.message.orEmpty(),
                    "templateId" to templateId.toString(),
                ),
            )
        } finally {
            // 처리 성공/실패와 관계없이 대기열과 Stream 메시지를 반드시 정리해 순서 보장을 유지한다.
            couponIssueCoordinator.complete(templateId, requestId)
            ack(record.id)
        }
    }

    private fun publishResult(
        requestId: String,
        recordId: RecordId,
        status: String,
        payload: Map<String, String>,
    ) {
        val body = buildMap {
            put(CouponIssueCoordinator.REQUEST_ID_FIELD, requestId)
            put("status", status)
            put("recordId", recordId.toString())
            putAll(payload)
        }
        streamOps.add(
            StreamRecords.mapBacked<String, String, String>(body)
                .withStreamKey(issueStreamKey),
        )
    }

    private fun ack(recordId: RecordId) {
        streamOps.acknowledge(requestStreamKey, streamGroup, recordId)
        streamOps.delete(requestStreamKey, recordId)
    }

    override fun close() {
        if (started) {
            container.stop()
        }
    }
}
