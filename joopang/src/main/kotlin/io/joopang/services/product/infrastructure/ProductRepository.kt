package io.joopang.services.product.infrastructure

import io.joopang.services.product.domain.ProductWithItems
import io.joopang.services.product.infrastructure.jpa.ProductDailySaleEntity
import io.joopang.services.product.infrastructure.jpa.ProductEntity
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

@Repository
@Transactional(readOnly = true)
open class ProductRepository(
    @PersistenceContext private val entityManager: EntityManager,
) {

    open fun findAll(): List<ProductWithItems> =
        entityManager.createQuery("select p from ProductEntity p", ProductEntity::class.java)
            .resultList
            .map(ProductEntity::toAggregate)

    open fun findById(productId: UUID): ProductWithItems? =
        entityManager.find(ProductEntity::class.java, productId)?.toAggregate()

    open fun findProductCreatedAt(productId: UUID): LocalDate? =
        entityManager.find(ProductEntity::class.java, productId)?.createdAt

    open fun findDailySales(productId: UUID): List<DailySale> =
        entityManager.createQuery(
            "select s from ProductDailySaleEntity s where s.productId = :productId order by s.saleDate",
            ProductDailySaleEntity::class.java,
        )
            .setParameter("productId", productId)
            .resultList
            .map(ProductDailySaleEntity::toDomain)

    @Transactional
    open fun save(aggregate: ProductWithItems): ProductWithItems =
        entityManager.merge(ProductEntity.fromAggregate(aggregate)).toAggregate()

    @Transactional
    open fun update(aggregate: ProductWithItems): ProductWithItems {
        val existing = entityManager.find(ProductEntity::class.java, aggregate.product.id)
            ?: throw IllegalArgumentException("Product with id ${aggregate.product.id} not found")
        existing.updateFrom(aggregate)
        return existing.toAggregate()
    }
}
