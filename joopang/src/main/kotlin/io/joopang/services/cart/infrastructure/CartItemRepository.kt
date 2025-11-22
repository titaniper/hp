package io.joopang.services.cart.infrastructure

import io.joopang.services.cart.domain.CartItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface CartItemRepository : JpaRepository<CartItem, Long> {
    fun findAllByUserId(userId: Long): List<CartItem>
    fun findByUserIdAndProductItemId(userId: Long, productItemId: Long): CartItem?
    fun deleteByUserId(userId: Long)
}
