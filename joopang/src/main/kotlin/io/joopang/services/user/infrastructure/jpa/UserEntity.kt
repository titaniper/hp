package io.joopang.services.user.infrastructure.jpa

import io.joopang.services.common.domain.Email
import io.joopang.services.common.domain.Money
import io.joopang.services.common.domain.PasswordHash
import io.joopang.services.user.domain.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "users")
class UserEntity(
    @Id
    @Column(columnDefinition = "BINARY(16)")
    var id: UUID,

    @Column(nullable = false, unique = true, length = 191)
    var email: Email,

    @Column(name = "password_hash", nullable = false)
    var password: PasswordHash,

    @Column(name = "first_name")
    var firstName: String?,

    @Column(name = "last_name")
    var lastName: String?,

    @Column(name = "balance_amount", precision = 19, scale = 2, nullable = false)
    var balance: Money,
) {
    fun toDomain(): User = User(
        id = id,
        email = email,
        password = password,
        firstName = firstName,
        lastName = lastName,
        balance = balance,
    )

    companion object {
        fun from(domain: User): UserEntity = UserEntity(
            id = domain.id,
            email = domain.email,
            password = domain.password,
            firstName = domain.firstName,
            lastName = domain.lastName,
            balance = domain.balance,
        )
    }
}
