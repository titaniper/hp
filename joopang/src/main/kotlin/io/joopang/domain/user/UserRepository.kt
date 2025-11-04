package io.joopang.domain.user

import java.util.UUID

interface UserRepository {
    fun findById(userId: UUID): User?
    fun save(user: User): User
    fun findAll(): List<User>
}
