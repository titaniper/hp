package io.joopang

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.kafka.annotation.EnableKafka

@SpringBootApplication
@EnableKafka
@ConfigurationPropertiesScan("io.joopang.services")
class CouponServiceApplication

fun main(args: Array<String>) {
    runApplication<CouponServiceApplication>(*args)
}
