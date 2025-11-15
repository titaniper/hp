package io.joopang.services.user.domain

import io.joopang.services.common.domain.Email
import io.joopang.services.common.domain.Money
import io.joopang.services.common.domain.PasswordHash
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "users")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "BIGINT")
    var id: Long = 0,

    @Column(nullable = false, unique = true, length = 191)
    var email: Email = Email("default@example.com"),

    @Column(name = "password_hash", nullable = false)
    var password: PasswordHash = PasswordHash("password"),

    @Column(name = "first_name")
    var firstName: String? = null,

    @Column(name = "last_name")
    var lastName: String? = null,

    @Column(name = "balance_amount", precision = 19, scale = 2, nullable = false)
    var balance: Money = Money.ZERO,
) {

    init {
        require(balance >= Money.ZERO) { "Balance cannot be negative" }
    }

    fun fullName(): String? =
        listOfNotNull(firstName, lastName).takeIf { it.isNotEmpty() }?.joinToString(" ")

    fun hasEnoughBalance(amount: Money): Boolean = balance >= amount

    fun deduct(amount: Money): User {
        require(hasEnoughBalance(amount)) {
            "Insufficient balance: current ${balance.toBigDecimal()}, required ${amount.toBigDecimal()}"
        }
        return copy(balance = balance - amount)
    }

    fun copy(
        id: Long = this.id,
        email: Email = this.email,
        password: PasswordHash = this.password,
        firstName: String? = this.firstName,
        lastName: String? = this.lastName,
        balance: Money = this.balance,
    ): User =
        User(
            id = id,
            email = email,
            password = password,
            firstName = firstName,
            lastName = lastName,
            balance = balance,
        )
}
