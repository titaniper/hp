package io.joopang.services.common.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class EmailTest {

    @Test
    fun `valid email passes`() {
        val email = Email("user@example.com")

        assertThat(email.toString()).isEqualTo("user@example.com")
    }

    @Test
    fun `blank email rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            Email(" ")
        }
    }

    @Test
    fun `invalid format rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            Email("user@invalid")
        }
    }
}
