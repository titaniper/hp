package io.joopang.services.common.infrastructure.id

import io.joopang.services.common.domain.BaseEntity
import jakarta.persistence.PrePersist
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class BaseEntityListener {

    companion object {
        private lateinit var snowflakeIdGenerator: SnowflakeIdGenerator

        @JvmStatic
        @Autowired
        fun setSnowflakeIdGenerator(generator: SnowflakeIdGenerator) {
            snowflakeIdGenerator = generator
        }
    }

    @PrePersist
    fun prePersist(entity: BaseEntity) {
        if (entity.id == null) {
            entity.id = snowflakeIdGenerator.nextId()
        }
    }
}
