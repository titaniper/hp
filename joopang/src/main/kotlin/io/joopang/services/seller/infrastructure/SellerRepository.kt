package io.joopang.services.seller.infrastructure

import io.joopang.services.seller.domain.Seller
import io.joopang.services.seller.infrastructure.jpa.SellerEntity
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
        entityManager.createQuery("select s from SellerEntity s", SellerEntity::class.java)
            .resultList
            .map(SellerEntity::toDomain)

    open fun findById(id: UUID): Seller? =
        entityManager.find(SellerEntity::class.java, id)?.toDomain()

    @Transactional
    open fun save(seller: Seller): Seller =
        entityManager.merge(SellerEntity.from(seller)).toDomain()
}
