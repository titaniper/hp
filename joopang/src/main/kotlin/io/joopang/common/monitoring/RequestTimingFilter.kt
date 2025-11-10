package io.joopang.services.common.monitoring

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Measures raw HTTP request processing time (from filter chain start to finish)
 * to provide coarse-grained latency visibility for every endpoint.
 */
@Component
class RequestTimingFilter : OncePerRequestFilter() {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val start = System.currentTimeMillis()
        try {
            filterChain.doFilter(request, response)
        } finally {
            val duration = System.currentTimeMillis() - start
            logger.debug("HTTP {} {} completed in {} ms", request.method, request.requestURI, duration)
        }
    }
}
