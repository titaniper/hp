package io.joopang.services.order.domain

import io.joopang.services.common.domain.Money
import io.joopang.services.common.domain.Quantity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.UUID

class OrderItemTest {

    private fun item(quantity: Int = 2): OrderItem =
        OrderItem(
            id = UUID.randomUUID(),
            orderId = UUID.randomUUID(),
            productId = UUID.randomUUID(),
            productItemId = UUID.randomUUID(),
            productName = "상품",
            quantity = Quantity(quantity),
            unitPrice = Money.of(5_000L),
            subtotal = Money.of(10_000L),
        )

    @Test
    fun `expected subtotal matches`() {
        val item = item()

        assertThat(item.expectedSubtotal()).isEqualTo(Money.of(10_000L))
    }

    @Test
    fun `refundable quantity subtracts refunded`() {
        val item = item().copy(refundedQuantity = Quantity(1))

        assertThat(item.refundableQuantity().value).isEqualTo(1)
    }

    @Test
    fun `subtotal mismatch rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            item().copy(subtotal = Money.of(1_000L))
        }
    }
}
