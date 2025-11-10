package io.joopang.services.user.infrastructure

import io.joopang.services.common.domain.Email
import io.joopang.services.common.domain.Money
import io.joopang.services.common.domain.PasswordHash
import io.joopang.services.user.domain.User
import org.springframework.stereotype.Repository
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Repository
open class UserRepository {

    private val store = ConcurrentHashMap<UUID, User>()

    init {
        seed()
    }

    open fun findById(userId: UUID): User? = store[userId]

    open fun save(user: User): User {
        store[user.id] = user
        return user
    }

    open fun findAll(): List<User> = store.values.toList()

    private fun seed() {
        val customerId = UUID.fromString("aaaaaaaa-1111-2222-3333-444444444444")
        store[customerId] = User(
            id = customerId,
            email = Email("customer@joopang.com"),
            password = PasswordHash("hashedpassword"),
            firstName = "Joo",
            lastName = "Pang",
            balance = Money.of(500_000L),
        )

        val vipId = UUID.fromString("bbbbbbbb-1111-2222-3333-444444444444")
        store[vipId] = User(
            id = vipId,
            email = Email("vip@joopang.com"),
            password = PasswordHash("viphashed"),
            firstName = "Vip",
            lastName = "Customer",
            balance = Money.of(1_000_000L),
        )
    }
}
