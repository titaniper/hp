package io.joopang.services.product.domain

import io.joopang.services.common.domain.Money
import io.joopang.services.common.domain.Percentage
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "products")
class Product(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "BIGINT")
    var id: Long = 0,

    @Column(nullable = false)
    var name: String = "",

    @Column(nullable = false, unique = true, length = 191)
    var code: ProductCode = ProductCode("DEFAULT"),

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    @Column(columnDefinition = "TEXT")
    var content: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: ProductStatus = ProductStatus.ON_SALE,

    @Column(name = "seller_id", columnDefinition = "BIGINT", nullable = false)
    var sellerId: Long = 0,

    @Column(name = "category_id", columnDefinition = "BIGINT", nullable = false)
    var categoryId: Long = 0,

    @Column(name = "price_amount", precision = 19, scale = 2, nullable = false)
    var price: Money = Money.ZERO,

    @Column(name = "discount_rate", precision = 5, scale = 2)
    var discountRate: Percentage? = null,

    @Column(nullable = false)
    var version: Int = 0,

    @Column(name = "view_count", nullable = false)
    var viewCount: Int = 0,

    @Column(name = "sales_count", nullable = false)
    var salesCount: Int = 0,

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDate = LocalDate.now(),
) {

    init {
        require(name.isNotBlank()) { "Product name must not be blank" }
        require(version >= 0) { "Product version must be non-negative" }
        require(viewCount >= 0) { "View count must be non-negative" }
        require(salesCount >= 0) { "Sales count must be non-negative" }
    }

    fun discountedPrice(): Money =
        discountRate?.let {
            val discount = price.toBigDecimal()
                .multiply(it.toDecimalFraction(scale = 4))
            Money.of(price.toBigDecimal().subtract(discount))
        } ?: price

    fun hasDiscount(): Boolean = discountRate != null

    fun copy(
        id: Long = this.id,
        name: String = this.name,
        code: ProductCode = this.code,
        description: String? = this.description,
        content: String? = this.content,
        status: ProductStatus = this.status,
        sellerId: Long = this.sellerId,
        categoryId: Long = this.categoryId,
        price: Money = this.price,
        discountRate: Percentage? = this.discountRate,
        version: Int = this.version,
        viewCount: Int = this.viewCount,
        salesCount: Int = this.salesCount,
        createdAt: LocalDate = this.createdAt,
    ): Product =
        Product(
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
            createdAt = createdAt,
        )
}
