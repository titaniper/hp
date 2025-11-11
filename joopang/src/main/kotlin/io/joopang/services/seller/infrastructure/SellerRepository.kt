package io.joopang.services.seller.infrastructure

import io.joopang.services.seller.domain.Seller
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Repository
@Transactional(readOnly = true)
open class SellerRepository(
    @PersistenceContext private val entityManager: EntityManager,
) {

    open fun findAll(): List<Seller> =
        entityManager.createQuery("select s from Seller s", Seller::class.java)
            .resultList

    open fun findById(id: UUID): Seller? =
        entityManager.find(Seller::class.java, id)

    @Transactional
    open fun save(seller: Seller): Seller =
        entityManager.merge(seller)
}
