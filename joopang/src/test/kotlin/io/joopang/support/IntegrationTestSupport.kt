package io.joopang.support

import org.springframework.test.context.ActiveProfiles
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@ActiveProfiles("test")
abstract class IntegrationTestSupport {
    companion object {
        @JvmStatic
        @Container
        @ServiceConnection
        val mysql: MySQLContainer<*> = MySQLContainer("mysql:8.0.36").apply {
            withDatabaseName("joopang")
            withUsername("joopang")
            withPassword("joopang")
        }
    }
}
