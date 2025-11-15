package io.joopang.support

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ActiveProfiles
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@ActiveProfiles("test")
abstract class IntegrationTestSupport {
    @Autowired
    protected lateinit var transactionTemplate: TransactionTemplate

    protected fun <T> inTransaction(block: () -> T): T =
        transactionTemplate.execute { block() }!!

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
