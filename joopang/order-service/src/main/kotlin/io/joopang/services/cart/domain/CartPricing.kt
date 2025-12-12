package io.joopang.services.cart.domain

import io.joopang.services.common.domain.Money
import io.joopang.services.common.domain.Quantity

data class CartPricingLine(
    val unitPrice: Money,
    val quantity: Quantity,
)

data class CartTotals(
    val subtotal: Money,
    val tax: Money,
    val shipping: Money,
    val total: Money,
)

object CartPricingCalculator {

    fun calculate(lines: List<CartPricingLine>): CartTotals {
        if (lines.isEmpty()) {
            return CartTotals(
                subtotal = Money.ZERO,
                tax = Money.ZERO,
                shipping = Money.ZERO,
                total = Money.ZERO,
            )
        }

        val subtotal = lines.fold(Money.ZERO) { acc, line ->
            acc + (line.unitPrice * line.quantity.value)
        }

        val tax = Money.ZERO
        val shipping = Money.ZERO
        val total = subtotal + tax + shipping

        return CartTotals(
            subtotal = subtotal,
            tax = tax,
            shipping = shipping,
            total = total,
        )
    }
}
