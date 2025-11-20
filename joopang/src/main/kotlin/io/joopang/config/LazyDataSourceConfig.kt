package io.joopang.config

import com.zaxxer.hikari.HikariDataSource
import jakarta.persistence.EntityManagerFactory
import javax.sql.DataSource
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean

@Configuration(proxyBeanMethods = false)
@EnableJpaRepositories(basePackages = ["io.joopang"])
class LazyDataSourceConfig {

    @Bean
    fun hikariDataSource(dataSourceProperties: DataSourceProperties): HikariDataSource =
        dataSourceProperties.initializeDataSourceBuilder()
            .type(HikariDataSource::class.java)
            .build()

    @Bean
    @Primary
    fun lazyConnectionDataSourceProxy(hikariDataSource: HikariDataSource): LazyConnectionDataSourceProxy =
        LazyConnectionDataSourceProxy(hikariDataSource)

    @Bean
    @Primary
    fun entityManagerFactory(
        @Qualifier("lazyConnectionDataSourceProxy") dataSource: DataSource,
        builder: EntityManagerFactoryBuilder,
    ): LocalContainerEntityManagerFactoryBean =
        builder
            .dataSource(dataSource)
            .packages("io.joopang")
            .persistenceUnit("main")
            .build()
}
