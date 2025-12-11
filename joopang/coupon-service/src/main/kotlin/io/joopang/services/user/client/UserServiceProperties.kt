package io.joopang.services.user.client

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "clients.user")
data class UserServiceProperties(
    var baseUrl: String = "http://localhost:8081",
)
