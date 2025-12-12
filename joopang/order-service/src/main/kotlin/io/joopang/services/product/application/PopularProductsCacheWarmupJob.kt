package io.joopang.services.product.application

import io.joopang.services.product.infrastructure.ProductRankingRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@EnableConfigurationProperties(PopularProductsWarmupProperties::class)
@ConditionalOnProperty(
    prefix = "joopang.jobs.popular-products-cache-warmup",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class PopularProductsCacheWarmupJob(
    private val productService: ProductService,
    private val productRankingRepository: ProductRankingRepository,
    private val properties: PopularProductsWarmupProperties,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${joopang.jobs.popular-products-cache-warmup.refresh-interval-ms:111155000}")
    fun refreshPopularProductsCache() {
        properties.targets
            .distinct()
            .forEach { target ->
                runCatching {
                    productRankingRepository.refreshPopularProductsCache(target.days)
                    productService.getTopProducts(target.days, target.limit)
                }
                    .onSuccess {
                        logger.debug(
                            "Popular products cache warmed for days={}, limit={}",
                            target.days,
                            target.limit,
                        )
                    }
                    .onFailure { ex ->
                        logger.warn(
                            "Failed to warm popular products cache for days={}, limit={}",
                            target.days,
                            target.limit,
                            ex,
                        )
                    }
            }
    }
}
