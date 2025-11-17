package io.joopang.services.product.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable
import java.time.LocalDate

@Entity
@Table(name = "product_daily_sales")
@IdClass(DailySaleId::class)
class DailySale(
    @Id
    @Column(name = "product_id", columnDefinition = "BIGINT")
    var productId: Long = 0,

    @Id
    @Column(name = "sale_date")
    var date: LocalDate = LocalDate.MIN,

    @Column(nullable = false)
    var quantity: Int = 0,
) {

    @Suppress("unused")
    constructor() : this(
        productId = 0,
        date = LocalDate.MIN,
        quantity = 0,
    )
}

data class DailySaleId(
    var productId: Long? = null,
    var date: LocalDate? = null,
) : Serializable
