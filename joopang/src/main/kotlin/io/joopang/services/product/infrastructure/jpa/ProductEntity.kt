package io.joopang.services.product.infrastructure.jpa

import io.joopang.services.common.domain.Money
import io.joopang.services.common.domain.Percentage
import io.joopang.services.product.domain.Product
import io.joopang.services.product.domain.ProductCode
import io.joopang.services.product.domain.ProductItem
import io.joopang.services.product.domain.ProductItemCode
import io.joopang.services.product.domain.ProductItemStatus
import io.joopang.services.product.domain.ProductStatus
import io.joopang.services.product.domain.ProductWithItems
import io.joopang.services.product.domain.StockQuantity
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "products")
class ProductEntity(
    @Id
    @Column(columnDefinition = "BINARY(16)")
    var id: UUID,

    @Column(nullable = false)
    var name: String,

    @Column(nullable = false, unique = true, length = 191)
    var code: ProductCode,

    @Column(columnDefinition = "TEXT")
    var description: String?,

    @Column(columnDefinition = "TEXT")
    var content: String?,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: ProductStatus,

    @Column(name = "seller_id", columnDefinition = "BINARY(16)", nullable = false)
    var sellerId: UUID,

    @Column(name = "category_id", columnDefinition = "BINARY(16)", nullable = false)
    var categoryId: UUID,

    @Column(name = "price_amount", precision = 19, scale = 2, nullable = false)
    var price: Money,

    @Column(name = "discount_rate", precision = 5, scale = 2)
    var discountRate: Percentage?,

    @Column(nullable = false)
    var version: Int,

    @Column(name = "view_count", nullable = false)
    var viewCount: Int = 0,

    @Column(name = "sales_count", nullable = false)
    var salesCount: Int = 0,

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDate = LocalDate.now(),

    @OneToMany(mappedBy = "product", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    var items: MutableList<ProductItemEntity> = mutableListOf(),
) {

    fun toAggregate(): ProductWithItems = ProductWithItems(
        product = Product(
            id = id,
            name = name,
            code = code,
            description = description,
            content = content,
            status = status,
            sellerId = sellerId,
            categoryId = categoryId,
            price = price,
            discountRate = discountRate,
            version = version,
            viewCount = viewCount,
            salesCount = salesCount,
        ),
        items = items.map(ProductItemEntity::toDomain),
    )

    fun updateFrom(aggregate: ProductWithItems) {
        name = aggregate.product.name
        code = aggregate.product.code
        description = aggregate.product.description
        content = aggregate.product.content
        status = aggregate.product.status
        sellerId = aggregate.product.sellerId
        categoryId = aggregate.product.categoryId
        price = aggregate.product.price
        discountRate = aggregate.product.discountRate
        version = aggregate.product.version
        viewCount = aggregate.product.viewCount
        salesCount = aggregate.product.salesCount
        items.clear()
        aggregate.items.forEach { item ->
            items.add(ProductItemEntity.from(item, this))
        }
    }

    companion object {
        fun fromAggregate(aggregate: ProductWithItems, createdAt: LocalDate = LocalDate.now()): ProductEntity {
            val entity = ProductEntity(
                id = aggregate.product.id,
                name = aggregate.product.name,
                code = aggregate.product.code,
                description = aggregate.product.description,
                content = aggregate.product.content,
                status = aggregate.product.status,
                sellerId = aggregate.product.sellerId,
                categoryId = aggregate.product.categoryId,
                price = aggregate.product.price,
                discountRate = aggregate.product.discountRate,
                version = aggregate.product.version,
                viewCount = aggregate.product.viewCount,
                salesCount = aggregate.product.salesCount,
                createdAt = createdAt,
            )
            entity.items = aggregate.items
                .map { ProductItemEntity.from(it, entity) }
                .toMutableList()
            return entity
        }
    }
}

@Entity
@Table(name = "product_items")
class ProductItemEntity(
    @Id
    @Column(columnDefinition = "BINARY(16)")
    var id: UUID,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", columnDefinition = "BINARY(16)", nullable = false)
    var product: ProductEntity,

    @Column(nullable = false)
    var name: String,

    @Column(name = "unit_price", precision = 19, scale = 2, nullable = false)
    var unitPrice: Money,

    @Column(columnDefinition = "TEXT")
    var description: String?,

    @Column(name = "stock", precision = 19, scale = 2, nullable = false)
    var stock: StockQuantity,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: ProductItemStatus,

    @Column(nullable = false, unique = true, length = 191)
    var code: ProductItemCode,
) {
    fun toDomain(): ProductItem = ProductItem(
        id = id,
        productId = product.id,
        name = name,
        unitPrice = unitPrice,
        description = description,
        stock = stock,
        status = status,
        code = code,
    )

    companion object {
        fun from(domain: ProductItem, product: ProductEntity): ProductItemEntity = ProductItemEntity(
            id = domain.id,
            product = product,
            name = domain.name,
            unitPrice = domain.unitPrice,
            description = domain.description,
            stock = domain.stock,
            status = domain.status,
            code = domain.code,
        )
    }
}
