package io.joopang.services.seller.infrastructure

import io.joopang.services.seller.domain.Seller
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Repository

@Repository
class SellerRepository(
    @PersistenceContext private val entityManager: EntityManager,
) {

    fun findAll(): List<Seller> =
        entityManager.createQuery("select s from Seller s", Seller::class.java)
            .resultList

    fun findById(id: Long): Seller? =
        entityManager.find(Seller::class.java, id)

    fun save(seller: Seller): Seller =
        entityManager.merge(seller)
}
