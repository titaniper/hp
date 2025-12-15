package io.joopang.services.product.application

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "joopang.jobs.popular-products-cache-warmup")
data class PopularProductsWarmupProperties(
    val enabled: Boolean = true,
    val refreshIntervalMs: Long = 55_000,
    val targets: List<WarmupTarget> = listOf(WarmupTarget()),
) {
    data class WarmupTarget(
        val days: Long = 3,
        val limit: Int = 5,
    )
}
