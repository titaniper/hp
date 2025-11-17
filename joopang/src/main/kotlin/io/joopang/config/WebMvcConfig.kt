package io.joopang.config

import io.joopang.services.common.monitoring.RequestPerformanceInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Registers common MVC infrastructure such as performance interceptors.
 */
@Configuration
class WebMvcConfig(
    private val requestPerformanceInterceptor: RequestPerformanceInterceptor,
) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(requestPerformanceInterceptor)
    }
}
