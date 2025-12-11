package io.joopang.services.user.client

import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate

@Profile("!test")
@Component
class HttpUserClient(
    restTemplateBuilder: RestTemplateBuilder,
    properties: UserServiceProperties,
) : UserClient {

    private val restTemplate: RestTemplate = restTemplateBuilder
        .rootUri(properties.baseUrl.trimEnd('/'))
        .build()

    override fun ensureUserExists(userId: Long) {
        try {
            restTemplate.getForEntity("/internal/users/{userId}", Void::class.java, userId)
        } catch (ex: HttpClientErrorException) {
            if (ex.statusCode == HttpStatus.NOT_FOUND) {
                throw UserVerificationException("User $userId not found")
            }
            throw UserVerificationException("User verification failed: ${ex.message}")
        }
    }
}
