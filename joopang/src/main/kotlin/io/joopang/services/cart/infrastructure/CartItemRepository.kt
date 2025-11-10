package io.joopang.services.cart.infrastructure

import io.joopang.services.cart.domain.CartItem
import io.joopang.services.cart.infrastructure.jpa.CartItemEntity
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Repository
@Transactional(readOnly = true)
open class CartItemRepository(
    @PersistenceContext private val entityManager: EntityManager,
) {

    open fun findByUserId(userId: UUID): List<CartItem> =
        entityManager.createQuery(
            "select c from CartItemEntity c where c.userId = :userId",
            CartItemEntity::class.java,
        )
            .setParameter("userId", userId)
            .resultList
            .map(CartItemEntity::toDomain)

    open fun findById(cartItemId: UUID): CartItem? =
        entityManager.find(CartItemEntity::class.java, cartItemId)?.toDomain()

    open fun findByUserIdAndProductItemId(userId: UUID, productItemId: UUID): CartItem? =
        entityManager.createQuery(
            "select c from CartItemEntity c where c.userId = :userId and c.productItemId = :productItemId",
            CartItemEntity::class.java,
        )
            .setParameter("userId", userId)
            .setParameter("productItemId", productItemId)
            .resultList
            .firstOrNull()
            ?.toDomain()

    @Transactional
    open fun save(cartItem: CartItem): CartItem =
        entityManager.merge(CartItemEntity.from(cartItem)).toDomain()

    @Transactional
    open fun delete(cartItemId: UUID) {
        val entity = entityManager.find(CartItemEntity::class.java, cartItemId) ?: return
        entityManager.remove(entity)
    }

    @Transactional
    open fun deleteByUserId(userId: UUID) {
        entityManager.createQuery("delete from CartItemEntity c where c.userId = :userId")
            .setParameter("userId", userId)
            .executeUpdate()
    }
}
