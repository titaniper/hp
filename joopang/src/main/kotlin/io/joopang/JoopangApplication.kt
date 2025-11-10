package io.joopang

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class JoopangApplication

fun main(args: Array<String>) {
    runApplication<JoopangApplication>(*args)
}
