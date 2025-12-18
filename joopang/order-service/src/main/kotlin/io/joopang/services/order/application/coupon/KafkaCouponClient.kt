package io.joopang.services.order.application.coupon

import io.joopang.services.coupon.contract.CouponCommand
import io.joopang.services.coupon.contract.CouponCommandResult
import io.joopang.services.coupon.contract.CouponCommandType
import io.joopang.services.coupon.contract.CouponSnapshot
import io.joopang.services.coupon.contract.InvalidCouponException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@Profile("!test")
@Component
class KafkaCouponClient(
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    @Value("\${kafka.topics.coupon-command:coupon-command}") private val commandTopic: String,
    @Value("\${kafka.topics.coupon-command-reply:coupon-command-reply}") private val replyTopic: String,
    @Value("\${kafka.coupon-client.timeout-millis:3000}") private val timeoutMillis: Long,
) : CouponClient {

    private val logger = LoggerFactory.getLogger(KafkaCouponClient::class.java)
    private val pending = ConcurrentHashMap<String, CompletableFuture<CouponCommandResult>>()

    override fun getCouponForOrder(couponId: Long, userId: Long): CouponSnapshot {
        val command = CouponCommand(
            type = CouponCommandType.VALIDATE,
            couponId = couponId,
            userId = userId,
        )
        val result = execute(command)
        val coupon = result.coupon
        if (!result.success || coupon == null) {
            throw InvalidCouponException(result.errorMessage ?: "Coupon validation failed")
        }
        return coupon
    }

    @KafkaListener(
        topics = ["\${kafka.topics.coupon-command-reply:coupon-command-reply}"],
        groupId = "\${kafka.coupon-client.group-id:order-coupon-client}",
    )
    fun handleReply(result: CouponCommandResult) {
        pending.remove(result.requestId)?.complete(result)
            ?: logger.warn("응답을 처리할 pending 요청을 찾을 수 없습니다. requestId={}", result.requestId)
    }

    private fun execute(command: CouponCommand): CouponCommandResult {
        val future = CompletableFuture<CouponCommandResult>()
        pending[command.requestId] = future
        kafkaTemplate.send(commandTopic, command.requestId, command)
        return try {
            future.orTimeout(timeoutMillis, TimeUnit.MILLISECONDS).join()
        } catch (ex: Exception) {
            pending.remove(command.requestId)
            throw InvalidCouponException("쿠폰 서비스 응답 시간 초과", ex)
        }
    }
}
