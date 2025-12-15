package io.joopang.common.lock

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Pointcut
import org.aspectj.lang.reflect.MethodSignature
import org.redisson.api.RedissonClient
import org.springframework.core.DefaultParameterNameDiscoverer
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.expression.ExpressionParser
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.StandardEvaluationContext

@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE)
class DistributedLockAspect(
    private val redissonClient: RedissonClient,
) {
    private val parser: ExpressionParser = SpelExpressionParser()
    private val nameDiscoverer = DefaultParameterNameDiscoverer()

    @Pointcut("@annotation(io.joopang.common.lock.DistributedLock)")
    fun annotatedWithDistributedLock() = Unit

    @Around("annotatedWithDistributedLock() && @annotation(lock)")
    fun around(joinPoint: ProceedingJoinPoint, lock: DistributedLock): Any? {
        val expressionValue = evaluateKey(joinPoint, lock.key)
        val key = lock.prefix + expressionValue
        val rLock = redissonClient.getLock(key)

        val acquired = try {
            rLock.tryLock(lock.waitTime, lock.leaseTime, lock.timeUnit)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException(lock.failureMessage, e)
        }

        if (!acquired) {
            throw IllegalStateException(lock.failureMessage)
        }

        return try {
            joinPoint.proceed()
        } finally {
            if (rLock.isHeldByCurrentThread) {
                rLock.unlock()
            }
        }
    }

    private fun evaluateKey(joinPoint: ProceedingJoinPoint, expression: String): String {
        if (expression.isBlank()) {
            throw IllegalArgumentException("Lock key expression must not be blank")
        }

        val methodSignature = joinPoint.signature as MethodSignature
        val parameterNames = nameDiscoverer.getParameterNames(methodSignature.method)
            ?: throw IllegalStateException("Cannot resolve parameter names for ${methodSignature.method}")

        val context = StandardEvaluationContext()
        joinPoint.args.forEachIndexed { index, arg ->
            context.setVariable(parameterNames[index], arg)
        }

        val parsed = parser.parseExpression(expression)
        val value = parsed.getValue(context, Any::class.java)
            ?: throw IllegalStateException("Failed to evaluate lock key expression: $expression")
        return value.toString()
    }
}
