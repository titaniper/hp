package io.joopang.domain.user

import io.joopang.domain.common.Email
import io.joopang.domain.common.Money
import io.joopang.domain.common.PasswordHash
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
