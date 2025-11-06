package io.joopang.services.cart.presentation

import io.joopang.services.cart.application.CartService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.util.UUID

@RestController
@RequestMapping("/api/carts")
class CartController(
    private val cartService: CartService,
) {

    @GetMapping("/current")
    fun getCurrentCart(
        @RequestParam userId: UUID,
    ): CartResponse =
        cartService
            .getCart(userId)
            .toResponse()

    @PutMapping("/current/items")
    fun upsertCartItem(
        @RequestBody request: UpsertCartItemRequest,
    ): CartResponse {
        val view = if (request.cartItemId != null) {
            cartService.updateItemQuantity(
                CartService.UpdateCartItemQuantityCommand(
                    userId = request.userId,
                    cartItemId = request.cartItemId,
                    quantity = request.quantity,
                ),
            )
        } else {
            val productId = request.productId ?: throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "productId is required when adding an item",
            )
            val productItemId = request.productItemId ?: throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "productItemId is required when adding an item",
            )
            cartService.addItem(
                CartService.AddCartItemCommand(
                    userId = request.userId,
                    productId = productId,
                    productItemId = productItemId,
                    quantity = request.quantity,
                ),
            )
        }

        return view.toResponse()
    }

    @DeleteMapping("/current/items/{cartItemId}")
    fun deleteCartItem(
        @PathVariable cartItemId: UUID,
        @RequestParam userId: UUID,
    ): CartResponse =
        cartService
            .removeItem(
                CartService.RemoveCartItemCommand(
                    userId = userId,
                    cartItemId = cartItemId,
                ),
            )
            .toResponse()

    @PostMapping("/merge")
    fun mergeCart(
        @RequestBody request: MergeCartRequest,
    ): CartResponse =
        cartService
            .mergeCarts(
                CartService.MergeCartCommand(
                    sourceUserId = request.sourceUserId,
                    targetUserId = request.targetUserId,
                ),
            )
            .toResponse()

    private fun CartService.CartView.toResponse(): CartResponse =
        CartResponse(
            userId = userId,
            items = items.map { it.toResponseItem() },
            totals = CartTotalsResponse(
                subtotal = totals.subtotal.toBigDecimal(),
                tax = totals.tax.toBigDecimal(),
                shipping = totals.shipping.toBigDecimal(),
                total = totals.total.toBigDecimal(),
            ),
        )

    private fun CartService.CartItemView.toResponseItem(): CartItemResponse =
        CartItemResponse(
            cartItemId = cartItemId,
            productId = productId,
            productItemId = productItemId,
            productName = productName,
            productItemName = productItemName,
            quantity = quantity.value,
            unitPrice = unitPrice?.toBigDecimal(),
            subtotal = subtotal?.toBigDecimal(),
            available = available,
        )
}

data class UpsertCartItemRequest(
    val userId: UUID,
    val productId: UUID?,
    val productItemId: UUID?,
    val quantity: Int,
    val cartItemId: UUID?,
)

data class MergeCartRequest(
    val sourceUserId: UUID,
    val targetUserId: UUID,
)

data class CartResponse(
    val userId: UUID,
    val items: List<CartItemResponse>,
    val totals: CartTotalsResponse,
)

data class CartItemResponse(
    val cartItemId: UUID,
    val productId: UUID,
    val productItemId: UUID,
    val productName: String?,
    val productItemName: String?,
    val quantity: Int,
    val unitPrice: BigDecimal?,
    val subtotal: BigDecimal?,
    val available: Boolean,
)

data class CartTotalsResponse(
    val subtotal: BigDecimal,
    val tax: BigDecimal,
    val shipping: BigDecimal,
    val total: BigDecimal,
)
