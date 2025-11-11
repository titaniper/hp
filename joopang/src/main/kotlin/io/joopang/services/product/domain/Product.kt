package io.joopang.services.product.domain

import io.joopang.services.common.domain.Money
import io.joopang.services.common.domain.Percentage
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "products")
data class Product(
    @Id
    @Column(columnDefinition = "BINARY(16)")
    var id: UUID = UUID(0L, 0L),

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

    @Column(name = "seller_id", columnDefinition = "BINARY(16)", nullable = false)
    var sellerId: UUID = UUID(0L, 0L),

    @Column(name = "category_id", columnDefinition = "BINARY(16)", nullable = false)
    var categoryId: UUID = UUID(0L, 0L),

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
}
