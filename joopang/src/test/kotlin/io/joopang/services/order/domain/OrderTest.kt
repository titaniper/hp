package io.joopang.services.order.domain

import io.joopang.services.common.domain.Money
import io.joopang.services.common.domain.OrderMonth
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant

class OrderTest {

    private fun order(total: Money = Money.of(10_000L), discount: Money = Money.of(1_000L)) =
        Order(
            userId = 1L,
            imageUrl = null,
            status = OrderStatus.PENDING,
            recipientName = "고객",
            orderMonth = OrderMonth.from(2024, 4),
            totalAmount = total,
            discountAmount = discount,
            orderedAt = Instant.now(),
        )

    @Test
    fun `payable amount subtracts discount`() {
        val order = order()

        assertThat(order.payableAmount()).isEqualTo(Money.of(9_000L))
    }

    @Test
    fun `mark paid updates status`() {
        val order = order()

        val paid = order.markPaid(Instant.now())

        assertThat(paid.status).isEqualTo(OrderStatus.PAID)
        assertThat(paid.paidAt).isNotNull()
    }

    @Test
    fun `discount cannot exceed total`() {
        assertThrows(IllegalArgumentException::class.java) {
            order(total = Money.of(1_000L), discount = Money.of(2_000L))
        }
    }
}
