package io.joopang.config

import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties
import org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy

@Configuration(proxyBeanMethods = false)
class LazyDataSourceConfig {

    @Bean
    fun hikariDataSource(
        dataSourceProperties: DataSourceProperties,
        jdbcConnectionDetailsProvider: ObjectProvider<JdbcConnectionDetails>,
    ): HikariDataSource {
        val connectionDetails = jdbcConnectionDetailsProvider.getIfAvailable()
        if (connectionDetails != null) {
            return HikariDataSource().apply {
                jdbcUrl = connectionDetails.jdbcUrl
                username = connectionDetails.username
                password = connectionDetails.password
                driverClassName = connectionDetails.driverClassName
            }
        }

        return dataSourceProperties.initializeDataSourceBuilder()
            .type(HikariDataSource::class.java)
            .build()
    }

    @Bean
    @Primary
    fun lazyConnectionDataSourceProxy(
        @Qualifier("hikariDataSource") targetDataSource: DataSource,
    ): LazyConnectionDataSourceProxy = LazyConnectionDataSourceProxy(targetDataSource)
}
