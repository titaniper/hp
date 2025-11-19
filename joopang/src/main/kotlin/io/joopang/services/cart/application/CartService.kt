package io.joopang.services.cart.application

import io.joopang.services.cart.domain.CartItem
import io.joopang.services.cart.domain.CartItemNotFoundException
import io.joopang.services.cart.domain.CartPricingCalculator
import io.joopang.services.cart.domain.CartPricingLine
import io.joopang.services.cart.domain.CartTotals
import io.joopang.services.cart.infrastructure.CartItemRepository
import io.joopang.services.common.domain.Money
import io.joopang.services.common.domain.Quantity
import io.joopang.services.product.domain.InsufficientStockException
import io.joopang.services.product.domain.ProductItem
import io.joopang.services.product.domain.ProductItemNotFoundException
import io.joopang.services.product.domain.ProductNotFoundException
import io.joopang.services.product.domain.ProductStatus
import io.joopang.services.product.domain.ProductWithItems
import io.joopang.services.product.domain.StockQuantity
import io.joopang.services.product.infrastructure.ProductRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class CartService(
    private val cartItemRepository: CartItemRepository,
    private val productRepository: ProductRepository,
) {

    fun getCart(userId: Long): Output {
        val items = cartItemRepository.findAllByUserId(userId)
        return buildView(userId, items)
    }

    @Transactional
    fun addItem(command: AddCartItemCommand): Output {
        require(command.quantity > 0) { "Quantity to add must be positive" }

        val aggregate = findProductAggregate(command.productId)
        val productItem = findProductItem(aggregate, command.productItemId)

        validateProductAvailability(aggregate, productItem)

        val existingItem = cartItemRepository.findByUserIdAndProductItemId(command.userId, command.productItemId)
        val existingQuantity = existingItem?.quantity ?: Quantity(0)
        val requestedQuantity = Quantity(command.quantity)
        val totalQuantity = existingQuantity + requestedQuantity

        ensureStockSufficient(
            productId = command.productId,
            productItemId = command.productItemId,
            stock = productItem.stock,
            requested = totalQuantity,
        )

        val updatedItem = existingItem?.copy(quantity = totalQuantity)
            ?: CartItem(
                userId = command.userId,
                productId = command.productId,
                productItemId = command.productItemId,
                quantity = totalQuantity,
            )

        cartItemRepository.save(updatedItem)

        return getCart(command.userId)
    }

    @Transactional
    fun updateItemQuantity(command: UpdateCartItemQuantityCommand): Output {
        val newQuantity = Quantity(command.quantity)
        val existingItem = cartItemRepository.findByIdOrNull(command.cartItemId)
            ?: throw CartItemNotFoundException(command.cartItemId)

        if (existingItem.userId != command.userId) {
            throw IllegalStateException("Cart item ${command.cartItemId} does not belong to user ${command.userId}")
        }

        if (newQuantity.value > 0) {
            val aggregate = findProductAggregate(existingItem.productId)
            val productItem = findProductItem(aggregate, existingItem.productItemId)

            validateProductAvailability(aggregate, productItem)
            ensureStockSufficient(
                productId = existingItem.productId,
                productItemId = existingItem.productItemId,
                stock = productItem.stock,
                requested = newQuantity,
            )
            val updatedItem = existingItem.copy(quantity = newQuantity)
            cartItemRepository.save(updatedItem)
        } else {
            cartItemRepository.deleteById(existingItem.id)
        }

        return getCart(command.userId)
    }

    @Transactional
    fun removeItem(command: RemoveCartItemCommand): Output {
        val existingItem = cartItemRepository.findByIdOrNull(command.cartItemId)
            ?: throw CartItemNotFoundException(command.cartItemId)

        if (existingItem.userId != command.userId) {
            throw IllegalStateException("Cart item ${command.cartItemId} does not belong to user ${command.userId}")
        }

        cartItemRepository.deleteById(existingItem.id)

        return getCart(command.userId)
    }

    @Transactional
    fun mergeCarts(command: MergeCartCommand): Output {
        if (command.sourceUserId == command.targetUserId) {
            return getCart(command.targetUserId)
        }

        val sourceItems = cartItemRepository.findAllByUserId(command.sourceUserId)
        if (sourceItems.isEmpty()) {
            return getCart(command.targetUserId)
        }

        val targetItems = cartItemRepository.findAllByUserId(command.targetUserId)
        val targetByProductItem = targetItems.associateBy { it.productItemId }.toMutableMap()

        sourceItems.forEach { item ->
            val aggregate = findProductAggregate(item.productId)
            val productItem = findProductItem(aggregate, item.productItemId)

            validateProductAvailability(aggregate, productItem)

            val existing = targetByProductItem[item.productItemId]
            val combinedQuantity = (existing?.quantity ?: Quantity(0)) + item.quantity

            ensureStockSufficient(
                productId = item.productId,
                productItemId = item.productItemId,
                stock = productItem.stock,
                requested = combinedQuantity,
            )

            val savedItem = if (existing != null) {
                val updated = existing.copy(quantity = combinedQuantity)
                cartItemRepository.save(updated)
                updated
            } else {
                val newItem = CartItem(
                    userId = command.targetUserId,
                    productId = item.productId,
                    productItemId = item.productItemId,
                    quantity = item.quantity,
                )
                cartItemRepository.save(newItem)
                newItem
            }

            targetByProductItem[item.productItemId] = savedItem
        }

        cartItemRepository.deleteByUserId(command.sourceUserId)

        return getCart(command.targetUserId)
    }

    private fun findProductAggregate(productId: Long): ProductWithItems =
        productRepository.findById(productId)
            ?: throw ProductNotFoundException(productId.toString())

    private fun findProductItem(aggregate: ProductWithItems, productItemId: Long): ProductItem =
        aggregate.items.firstOrNull { it.id == productItemId }
            ?: throw ProductItemNotFoundException(
                productId = aggregate.product.id.toString(),
                itemId = productItemId.toString(),
            )

    private fun validateProductAvailability(
        aggregate: ProductWithItems,
        productItem: ProductItem,
    ) {
        if (aggregate.product.status != ProductStatus.ON_SALE) {
            throw IllegalStateException("Product ${aggregate.product.id} is not on sale")
        }
        if (!productItem.isActive()) {
            throw IllegalStateException("Product item ${productItem.id} is inactive")
        }
    }

    private fun ensureStockSufficient(
        productId: Long,
        productItemId: Long,
        stock: StockQuantity,
        requested: Quantity,
    ) {
        val requestedStock = StockQuantity.of(requested.value.toLong())
        if (!stock.isGreaterOrEqual(requestedStock)) {
            throw InsufficientStockException(productId.toString(), productItemId.toString())
        }
    }

    private fun buildView(userId: Long, items: List<CartItem>): Output {
        if (items.isEmpty()) {
            return Output(
                userId = userId,
                items = emptyList(),
                totals = CartTotals(
                    subtotal = Money.ZERO,
                    tax = Money.ZERO,
                    shipping = Money.ZERO,
                    total = Money.ZERO,
                ),
            )
        }

        val aggregates = if (items.isEmpty()) {
            emptyMap()
        } else {
            productRepository
                .findProductsByIds(items.map { it.productId }.distinct())
                .associateBy { it.product.id }
        }

        val pricingLines = mutableListOf<CartPricingLine>()
        val itemViews = items.map { item ->
            val aggregate = aggregates[item.productId]
            if (aggregate == null) {
                ItemOutput(
                    cartItemId = item.id,
                    productId = item.productId,
                    productItemId = item.productItemId,
                    productName = null,
                    productItemName = null,
                    quantity = item.quantity,
                    unitPrice = null,
                    subtotal = null,
                    available = false,
                    )
            } else {
                val productItem = aggregate.items.firstOrNull { it.id == item.productItemId }
                if (productItem == null) {
                    ItemOutput(
                        cartItemId = item.id,
                        productId = item.productId,
                        productItemId = item.productItemId,
                        productName = aggregate.product.name,
                        productItemName = null,
                        quantity = item.quantity,
                        unitPrice = null,
                        subtotal = null,
                        available = false,
                    )
                } else {
                    val requestedStock = StockQuantity.of(item.quantity.value.toLong())
                    val available = aggregate.product.status == ProductStatus.ON_SALE &&
                        productItem.isActive() &&
                        productItem.stock.isGreaterOrEqual(requestedStock)

                    val unitPrice = productItem.unitPrice
                    val subtotal = unitPrice * item.quantity.value

                    if (available) {
                        pricingLines += CartPricingLine(
                            unitPrice = unitPrice,
                            quantity = item.quantity,
                        )
                    }

                    ItemOutput(
                        cartItemId = item.id,
                        productId = item.productId,
                        productItemId = item.productItemId,
                        productName = aggregate.product.name,
                        productItemName = productItem.name,
                        quantity = item.quantity,
                        unitPrice = unitPrice,
                        subtotal = subtotal,
                        available = available,
                    )
                }
            }
        }

        val totals = CartPricingCalculator.calculate(pricingLines)

        return Output(
            userId = userId,
            items = itemViews,
            totals = totals,
        )
    }

    data class AddCartItemCommand(
        val userId: Long,
        val productId: Long,
        val productItemId: Long,
        val quantity: Int,
    )

    data class UpdateCartItemQuantityCommand(
        val userId: Long,
        val cartItemId: Long,
        val quantity: Int,
    )

    data class RemoveCartItemCommand(
        val userId: Long,
        val cartItemId: Long,
    )

    data class MergeCartCommand(
        val sourceUserId: Long,
        val targetUserId: Long,
    )

    data class Output(
        val userId: Long,
        val items: List<ItemOutput>,
        val totals: CartTotals,
    )

    data class ItemOutput(
        val cartItemId: Long,
        val productId: Long,
        val productItemId: Long,
        val productName: String?,
        val productItemName: String?,
        val quantity: Quantity,
        val unitPrice: Money?,
        val subtotal: Money?,
        val available: Boolean,
    )
}
