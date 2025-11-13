package io.joopang.services.product.infrastructure

import io.joopang.services.common.domain.Money
import io.joopang.services.product.domain.DailySale
import io.joopang.services.product.domain.Product
import io.joopang.services.product.domain.ProductItem
import io.joopang.services.product.domain.ProductSort
import io.joopang.services.product.domain.ProductWithItems
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
        entityManager.createQuery("select p from Product p", Product::class.java)
            .resultList
            .map { product -> ProductWithItems(product, findItems(product.id)) }

    open fun findById(productId: UUID): ProductWithItems? {
        val product = entityManager.find(Product::class.java, productId) ?: return null
        return ProductWithItems(product, findItems(productId))
    }

    open fun findProducts(categoryId: UUID?, sort: ProductSort): List<ProductWithItems> {
        val jpql = buildString {
            append("select p from Product p")
            if (categoryId != null) {
                append(" where p.categoryId = :categoryId")
            }
            append(" order by ")
            append(
                when (sort) {
                    ProductSort.NEWEST -> "p.createdAt desc"
                    ProductSort.SALES -> "p.salesCount desc"
                    ProductSort.PRICE_ASC -> "p.price asc"
                    ProductSort.PRICE_DESC -> "p.price desc"
                },
            )
        }

        val query = entityManager.createQuery(jpql, Product::class.java)
        if (categoryId != null) {
            query.setParameter("categoryId", categoryId)
        }

        val products = query.resultList
        if (products.isEmpty()) {
            return emptyList()
        }

        val itemsByProductId = findItemsByProductIds(products.map { it.id })
        return products.map { product ->
            ProductWithItems(product, itemsByProductId[product.id].orEmpty())
        }
    }

    open fun findProductsByIds(productIds: List<UUID>): List<ProductWithItems> {
        if (productIds.isEmpty()) {
            return emptyList()
        }

        val products = entityManager.createQuery(
            "select p from Product p where p.id in :ids",
            Product::class.java,
        )
            .setParameter("ids", productIds)
            .resultList

        val itemsByProductId = findItemsByProductIds(productIds)
        return products.map { product ->
            ProductWithItems(product, itemsByProductId[product.id].orEmpty())
        }
    }

    open fun findProductCreatedAt(productId: UUID): LocalDate? =
        entityManager.find(Product::class.java, productId)?.createdAt

    open fun findDailySales(productId: UUID): List<DailySale> =
        entityManager.createQuery(
            "select s from DailySale s where s.productId = :productId order by s.date",
            DailySale::class.java,
        )
            .setParameter("productId", productId)
            .resultList

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
    open fun save(aggregate: ProductWithItems): ProductWithItems {
        entityManager.persist(aggregate.product)
        aggregate.items.forEach(entityManager::persist)
        entityManager.flush()
        return ProductWithItems(aggregate.product, findItems(aggregate.product.id))
    }

    @Transactional
    open fun update(aggregate: ProductWithItems): ProductWithItems {
        val existing = entityManager.find(Product::class.java, aggregate.product.id)
            ?: throw IllegalArgumentException("Product with id ${aggregate.product.id} not found")

        entityManager.merge(aggregate.product)
        deleteItemsByProductId(existing.id)
        aggregate.items.forEach(entityManager::persist)
        entityManager.flush()

        return ProductWithItems(aggregate.product, findItems(existing.id))
    }

    data class PopularProductRow(
        val productId: UUID,
        val name: String,
        val salesCount: Long,
        val revenue: Money,
    )

    private fun findItems(productId: UUID): List<ProductItem> =
        entityManager.createQuery(
            "select i from ProductItem i where i.productId = :productId",
            ProductItem::class.java,
        )
            .setParameter("productId", productId)
            .resultList

    private fun findItemsByProductIds(productIds: List<UUID>): Map<UUID, List<ProductItem>> {
        if (productIds.isEmpty()) {
            return emptyMap()
        }

        return entityManager.createQuery(
            "select i from ProductItem i where i.productId in :productIds",
            ProductItem::class.java,
        )
            .setParameter("productIds", productIds)
            .resultList
            .groupBy { it.productId }
    }

    private fun deleteItemsByProductId(productId: UUID) {
        entityManager.createQuery("delete from ProductItem i where i.productId = :productId")
            .setParameter("productId", productId)
            .executeUpdate()
    }

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
