package io.joopang.services.coupon.application

import io.joopang.services.coupon.contract.CouponCommand
import io.joopang.services.coupon.contract.CouponCommandResult
import io.joopang.services.coupon.contract.CouponCommandType
import io.joopang.services.coupon.contract.CouponSnapshot
import io.joopang.services.coupon.contract.CouponStatus
import io.joopang.services.coupon.domain.Coupon
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class CouponCommandHandler(
    private val couponOrderFacade: CouponOrderFacade,
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    @Value("\${kafka.topics.coupon-command-reply:coupon-command-reply}") private val replyTopic: String,
) {

    private val log = LoggerFactory.getLogger(CouponCommandHandler::class.java)

    @KafkaListener(
        topics = ["\${kafka.topics.coupon-command:coupon-command}"],
        groupId = "\${kafka.coupon-command.group-id:coupon-command-worker}",
    )
    fun handle(command: CouponCommand) {
        val result = when (command.type) {
            CouponCommandType.VALIDATE -> handleValidate(command)
            CouponCommandType.MARK_USED -> handleMarkUsed(command)
        }
        kafkaTemplate.send(replyTopic, command.requestId, result)
    }

    private fun handleValidate(command: CouponCommand): CouponCommandResult =
        kotlin.runCatching {
            val coupon = couponOrderFacade.getCouponForOrder(command.couponId, command.userId)
            CouponCommandResult(
                requestId = command.requestId,
                success = true,
                coupon = coupon.toSnapshot(),
            )
        }.getOrElse { ex ->
            log.warn("쿠폰 검증 실패. couponId={}", command.couponId, ex)
            CouponCommandResult(
                requestId = command.requestId,
                success = false,
                errorMessage = ex.message,
            )
        }

    private fun handleMarkUsed(command: CouponCommand): CouponCommandResult =
        kotlin.runCatching {
            couponOrderFacade.markCouponUsed(
                couponId = command.couponId,
                userId = command.userId,
                orderId = command.orderId ?: error("orderId is required"),
            )
            CouponCommandResult(
                requestId = command.requestId,
                success = true,
            )
        }.getOrElse { ex ->
            log.warn("쿠폰 사용 처리 실패. couponId={}", command.couponId, ex)
            CouponCommandResult(
                requestId = command.requestId,
                success = false,
                errorMessage = ex.message,
            )
        }

    private fun Coupon.toSnapshot(): CouponSnapshot =
        CouponSnapshot(
            id = requireNotNull(id),
            userId = userId,
            couponTemplateId = couponTemplateId,
            type = type,
            status = status,
            value = value,
            issuedAt = issuedAt,
            usedAt = usedAt,
            expiredAt = expiredAt,
        )
}
