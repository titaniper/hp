package io.joopang.services.cart.infrastructure

import io.joopang.services.cart.domain.CartItem
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Repository

@Repository
class CartItemRepository(
    @PersistenceContext private val entityManager: EntityManager,
) {

    fun findByUserId(userId: Long): List<CartItem> =
        entityManager.createQuery(
            "select c from CartItem c where c.userId = :userId",
            CartItem::class.java,
        )
            .setParameter("userId", userId)
            .resultList

    fun findById(cartItemId: Long): CartItem? =
        entityManager.find(CartItem::class.java, cartItemId)

    fun findByUserIdAndProductItemId(userId: Long, productItemId: Long): CartItem? =
        entityManager.createQuery(
            "select c from CartItem c where c.userId = :userId and c.productItemId = :productItemId",
            CartItem::class.java,
        )
            .setParameter("userId", userId)
            .setParameter("productItemId", productItemId)
            .resultList
            .firstOrNull()

    fun save(cartItem: CartItem): CartItem =
        entityManager.merge(cartItem)

    fun delete(cartItemId: Long) {
        val entity = entityManager.find(CartItem::class.java, cartItemId) ?: return
        entityManager.remove(entity)
    }

    fun deleteByUserId(userId: Long) {
        entityManager.createQuery("delete from CartItem c where c.userId = :userId")
            .setParameter("userId", userId)
            .executeUpdate()
    }
}
