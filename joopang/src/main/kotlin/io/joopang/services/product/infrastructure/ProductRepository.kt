package io.joopang.services.product.infrastructure

import io.joopang.services.common.domain.Money
import io.joopang.services.product.domain.ProductWithItems
import io.joopang.services.product.infrastructure.jpa.ProductDailySaleEntity
import io.joopang.services.product.infrastructure.jpa.ProductEntity
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.ByteBuffer
import java.sql.Timestamp
import java.time.Instant
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

    open fun findPopularProductsSince(
        since: Instant,
        limit: Int,
    ): List<PopularProductRow> {
        val sql = """
            SELECT
                p.id AS product_id,
                p.name AS product_name,
                SUM(oi.quantity) AS sales_count,
                SUM(oi.subtotal) AS revenue
            FROM products p
            JOIN order_items oi ON p.id = oi.product_id
            JOIN orders o ON oi.order_id = o.id
            WHERE o.status = 'PAID' AND o.paid_at >= :paidSince
            GROUP BY p.id, p.name
            ORDER BY sales_count DESC
        """.trimIndent()

        @Suppress("UNCHECKED_CAST")
        val rows = entityManager.createNativeQuery(sql)
            .setParameter("paidSince", Timestamp.from(since))
            .setMaxResults(limit)
            .resultList as List<Array<Any>>

        return rows.map { columns ->
            PopularProductRow(
                productId = columns[0].toUuid(),
                name = columns[1].toString(),
                salesCount = columns[2].toLongValue(),
                revenue = Money.of(columns[3] as BigDecimal),
            )
        }
    }

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

    data class PopularProductRow(
        val productId: UUID,
        val name: String,
        val salesCount: Long,
        val revenue: Money,
    )

    private fun Any.toLongValue(): Long = when (this) {
        is Long -> this
        is Int -> this.toLong()
        is BigInteger -> this.toLong()
        is BigDecimal -> this.toLong()
        else -> error("Unsupported numeric type: ${this::class.simpleName}")
    }

    private fun Any.toUuid(): UUID = when (this) {
        is UUID -> this
        is String -> UUID.fromString(this)
        is ByteArray -> {
            val buffer = ByteBuffer.wrap(this)
            UUID(buffer.long, buffer.long)
        }
        else -> error("Unsupported UUID column type: ${this::class.simpleName}")
    }
}
