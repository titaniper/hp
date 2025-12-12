package io.joopang.services.common.monitoring

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import kotlin.math.max

/**
 * Captures controller-level performance, distinguishing between handler execution
 * and total time (including view rendering). The `RequestTimingFilter` already logs
 * coarse timings; this interceptor adds handler-specific insight.
 */
@Component
class RequestPerformanceInterceptor : HandlerInterceptor {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        request.setAttribute(START_TIME, System.currentTimeMillis())
        return true
    }

    override fun afterCompletion(request: HttpServletRequest, response: HttpServletResponse, handler: Any, ex: Exception?) {
        val start = request.getAttribute(START_TIME) as? Long ?: return
        val duration = max(0, System.currentTimeMillis() - start)
        logger.trace("Handler {} served {} {} in {} ms", handler::class.java.simpleName, request.method, request.requestURI, duration)
    }

    companion object {
        private const val START_TIME = "_request_start_time"
    }
}
