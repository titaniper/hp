package io.joopang.services.product.infrastructure

import java.time.LocalDate

data class DailySale(
    val date: LocalDate,
    val quantity: Int,
)
