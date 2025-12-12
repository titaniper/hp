package io.joopang.services.user.client

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Profile("test")
@Component
class StubUserClient : UserClient {
    override fun ensureUserExists(userId: Long) {
        // Tests assume 사용자는 항상 존재한다고 가정
    }
}
