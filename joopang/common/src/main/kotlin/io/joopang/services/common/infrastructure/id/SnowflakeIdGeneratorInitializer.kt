package io.joopang.services.common.infrastructure.id

import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component

@Component
class SnowflakeIdGeneratorInitializer(
    private val snowflakeIdGenerator: SnowflakeIdGenerator,
) {

    @PostConstruct
    fun registerGenerator() {
        BaseEntityListener.setSnowflakeIdGenerator(snowflakeIdGenerator)
    }
}
