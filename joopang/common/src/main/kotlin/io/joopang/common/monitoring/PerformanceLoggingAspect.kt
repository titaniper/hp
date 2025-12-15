package io.joopang.services.common.monitoring

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Around advice that records execution time of methods annotated with [TrackPerformance].
 * Useful for selective deep-dive profiling without touching business logic.
 */
@Aspect
@Component
class PerformanceLoggingAspect {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Around("@annotation(trackPerformance)")
    fun logExecutionTime(joinPoint: ProceedingJoinPoint, trackPerformance: TrackPerformance): Any? {
        val start = System.currentTimeMillis()
        return try {
            joinPoint.proceed()
        } finally {
            val duration = System.currentTimeMillis() - start
            val label = trackPerformance.label.ifBlank { joinPoint.signature.toShortString() }
            logger.info("Performance [{}] completed in {} ms", label, duration)
        }
    }
}
