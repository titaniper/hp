package io.joopang.services.user.client

interface UserClient {
    fun ensureUserExists(userId: Long)
}
