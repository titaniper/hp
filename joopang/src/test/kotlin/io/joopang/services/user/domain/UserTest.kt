package io.joopang.services.user.domain

import io.joopang.services.common.domain.Email
import io.joopang.services.common.domain.Money
import io.joopang.services.common.domain.PasswordHash
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.UUID

class UserTest {

    private fun user(balance: Money = Money.of(10_000L)) =
        User(
            id = UUID.randomUUID(),
            email = Email("user@joopang.com"),
            password = PasswordHash("hashedpass"),
            firstName = "Joo",
            lastName = "Pang",
            balance = balance,
        )

    @Test
    fun `full name concatenates`() {
        assertThat(user().fullName()).isEqualTo("Joo Pang")
    }

    @Test
    fun `deduct reduces balance`() {
        val user = user()

        val updated = user.deduct(Money.of(3_000L))

        assertThat(updated.balance).isEqualTo(Money.of(7_000L))
    }

    @Test
    fun `deduct fails when insufficient`() {
        val user = user(balance = Money.of(1_000L))

        assertThrows(IllegalArgumentException::class.java) {
            user.deduct(Money.of(2_000L))
        }
    }
}
