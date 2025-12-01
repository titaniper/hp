package io.joopang

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class JoopangApplication

fun main(args: Array<String>) {
    runApplication<JoopangApplication>(*args)
}
