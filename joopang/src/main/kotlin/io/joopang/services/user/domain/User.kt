package io.joopang.services.user.domain

import io.joopang.services.common.domain.Email
import io.joopang.services.common.domain.Money
import io.joopang.services.common.domain.PasswordHash
import java.util.UUID

data class User(
    val id: UUID,
    val email: Email,
    val password: PasswordHash,
    val firstName: String?,
    val lastName: String?,
    val balance: Money = Money.ZERO,
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
}
