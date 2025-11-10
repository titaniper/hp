package io.joopang.services.product.infrastructure.jpa

import io.joopang.services.product.infrastructure.DailySale
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "product_daily_sales")
@IdClass(ProductDailySaleId::class)
class ProductDailySaleEntity(
    @Id
    @Column(name = "product_id", columnDefinition = "BINARY(16)")
    var productId: UUID,

    @Id
    @Column(name = "sale_date")
    var saleDate: LocalDate,

    @Column(nullable = false)
    var quantity: Int,
) {
    fun toDomain(): DailySale = DailySale(saleDate, quantity)

    companion object {
        fun from(productId: UUID, sale: DailySale): ProductDailySaleEntity =
            ProductDailySaleEntity(
                productId = productId,
                saleDate = sale.date,
                quantity = sale.quantity,
            )
    }
}

data class ProductDailySaleId(
    var productId: UUID? = null,
    var saleDate: LocalDate? = null,
) : Serializable
