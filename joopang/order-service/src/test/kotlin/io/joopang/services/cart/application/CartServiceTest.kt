package io.joopang.services.cart.application

import io.joopang.services.cart.infrastructure.CartItemRepository
import io.joopang.services.product.domain.InsufficientStockException
import io.joopang.support.IntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CartServiceTest @Autowired constructor(
    private val cartService: CartService,
    private val cartItemRepository: CartItemRepository,
) : IntegrationTestSupport() {

    private val userId = 100L
    private val productId = 400L
    private val productItemId = 500L

    @BeforeEach
    fun cleanCart() {
        inTransaction { cartItemRepository.deleteByUserId(userId) }
    }

    @Test
    fun `add item creates new cart entry`() {
        val view = cartService.addItem(
            CartService.AddCartItemCommand(
                userId = userId,
                productId = productId,
                productItemId = productItemId,
                quantity = 2,
            ),
        )

        val item = view.items.single()
        assertThat(item.quantity.value).isEqualTo(2)
        assertThat(item.available).isTrue()
    }

    @Test
    fun `adding same item accumulates quantity`() {
        cartService.addItem(
            CartService.AddCartItemCommand(userId, productId, productItemId, quantity = 1),
        )

        val view = cartService.addItem(
            CartService.AddCartItemCommand(userId, productId, productItemId, quantity = 3),
        )

        assertThat(view.items.single().quantity.value).isEqualTo(4)
    }

    @Test
    fun `update quantity enforces stock`() {
        val view = cartService.addItem(
            CartService.AddCartItemCommand(userId, productId, productItemId, quantity = 1),
        )

        val cartItemId = view.items.single().cartItemId

        assertThrows(InsufficientStockException::class.java) {
            cartService.updateItemQuantity(
                CartService.UpdateCartItemQuantityCommand(
                    userId = userId,
                    cartItemId = cartItemId,
                    quantity = 10_000,
                ),
            )
        }
    }

    @Test
    fun `merge carts moves items and clears guest`() {
        val guestId = 9999L
        val guestItemId = 501L
        inTransaction { cartItemRepository.deleteByUserId(guestId) }

        cartService.addItem(
            CartService.AddCartItemCommand(guestId, productId, guestItemId, quantity = 1),
        )

        cartService.addItem(
            CartService.AddCartItemCommand(userId, productId, productItemId, quantity = 1),
        )

        val merged = cartService.mergeCarts(
            CartService.MergeCartCommand(
                sourceUserId = guestId,
                targetUserId = userId,
            ),
        )

        assertThat(merged.items).hasSize(2)
        val guestCart = cartService.getCart(guestId)
        assertThat(guestCart.items).isEmpty()
    }
}
