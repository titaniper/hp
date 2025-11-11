package io.joopang.services.product.domain

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
@IdClass(DailySaleId::class)
data class DailySale(
    @Id
    @Column(name = "product_id", columnDefinition = "BINARY(16)")
    var productId: UUID = UUID(0L, 0L),

    @Id
    @Column(name = "sale_date")
    var date: LocalDate = LocalDate.MIN,

    @Column(nullable = false)
    var quantity: Int = 0,
)

data class DailySaleId(
    var productId: UUID? = null,
    var date: LocalDate? = null,
) : Serializable
