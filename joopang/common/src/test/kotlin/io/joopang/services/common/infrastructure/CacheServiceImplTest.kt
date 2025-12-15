package io.joopang.services.common.infrastructure

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration

class CacheServiceImplTest {

    private val cache = CacheServiceImpl()

    @Test
    fun `stores and retrieves values`() {
        cache.put("key", "value", ttlSeconds = 10)

        assertThat(cache.get("key")).isEqualTo("value")
    }

    @Test
    fun `evicts expired values`() {
        cache.put("expires", "soon", ttlSeconds = 1)

        Thread.sleep(Duration.ofMillis(1100).toMillis())

        assertThat(cache.get("expires")).isNull()
    }
}
