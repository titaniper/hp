package io.joopang.services.metrics.domain

import java.time.LocalDate

data class ProductMetricsDaily(
    val metricDate: LocalDate,
    val productId: Long,
    val views: Int = 0,
    val sales: Int = 0,
) {

    init {
        require(views >= 0) { "Views cannot be negative" }
        require(sales >= 0) { "Sales cannot be negative" }
    }
}
