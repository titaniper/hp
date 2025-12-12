package io.joopang.services.common.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class QuantityTest {

    @Test
    fun `cannot create negative quantity`() {
        assertThrows(IllegalArgumentException::class.java) {
            Quantity(-1)
        }
    }

    @Test
    fun `addition increments value`() {
        val sum = Quantity(2) + Quantity(3)

        assertThat(sum.value).isEqualTo(5)
    }

    @Test
    fun `subtraction rejects negative result`() {
        assertThrows(IllegalArgumentException::class.java) {
            Quantity(1) - Quantity(2)
        }
    }
}
