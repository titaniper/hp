package io.joopang.services.common.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class PercentageTest {

    @Test
    fun `of double stores two decimal places`() {
        val percentage = Percentage.of(12.345)

        assertThat(percentage.value).isEqualByComparingTo(BigDecimal("12.35"))
    }

    @Test
    fun `toDecimalFraction converts to fraction`() {
        val percentage = Percentage.of(BigDecimal("12.50"))

        val fraction = percentage.toDecimalFraction(4)

        assertThat(fraction).isEqualByComparingTo("0.1250")
    }

    @Test
    fun `rejects values outside range`() {
        assertThrows(IllegalArgumentException::class.java) {
            Percentage.of(BigDecimal("101"))
        }
    }
}
