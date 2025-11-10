package io.joopang.services.product.domain

import io.joopang.services.common.domain.Money
import io.joopang.services.common.domain.Percentage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class ProductTest {

    private fun product(discountRate: Percentage? = Percentage.of(10.0)) =
        Product(
            id = UUID.randomUUID(),
            name = "상품",
            code = ProductCode("CODE"),
            description = "desc",
            content = null,
            status = ProductStatus.ON_SALE,
            sellerId = UUID.randomUUID(),
            categoryId = UUID.randomUUID(),
            price = Money.of(10_000L),
            discountRate = discountRate,
            version = 1,
        )

    @Test
    fun `discounted price applies percentage`() {
        val product = product()

        assertThat(product.discountedPrice()).isEqualTo(Money.of(9_000L))
        assertThat(product.hasDiscount()).isTrue()
    }

    @Test
    fun `no discount returns original price`() {
        val product = product(discountRate = null)

        assertThat(product.discountedPrice()).isEqualTo(product.price)
        assertThat(product.hasDiscount()).isFalse()
    }
}
