package io.joopang.services.cart.infrastructure

import io.joopang.services.cart.domain.CartItem
import org.springframework.stereotype.Repository
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

@Repository
open class CartItemRepository {

    private val itemsById = ConcurrentHashMap<UUID, CartItem>()
    private val idsByUser = ConcurrentHashMap<UUID, MutableSet<UUID>>()

    open fun findByUserId(userId: UUID): List<CartItem> =
        idsByUser[userId]
            ?.mapNotNull { itemsById[it] }
            ?: emptyList()

    open fun findById(cartItemId: UUID): CartItem? = itemsById[cartItemId]

    open fun findByUserIdAndProductItemId(userId: UUID, productItemId: UUID): CartItem? =
        findByUserId(userId).firstOrNull { it.productItemId == productItemId }

    open fun save(cartItem: CartItem): CartItem {
        itemsById[cartItem.id] = cartItem
        idsByUser.computeIfAbsent(cartItem.userId) { CopyOnWriteArraySet() }
            .add(cartItem.id)
        return cartItem
    }

    open fun delete(cartItemId: UUID) {
        val removed = itemsById.remove(cartItemId) ?: return
        idsByUser[removed.userId]?.let { ids ->
            ids.remove(cartItemId)
            if (ids.isEmpty()) {
                idsByUser.remove(removed.userId)
            }
        }
    }

    open fun deleteByUserId(userId: UUID) {
        val ids = idsByUser.remove(userId) ?: return
        ids.forEach { itemsById.remove(it) }
    }
}
