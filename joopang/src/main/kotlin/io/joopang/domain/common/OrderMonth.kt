package io.joopang.domain.common

import java.time.YearMonth
import java.time.format.DateTimeFormatter

data class OrderMonth(val value: YearMonth) {

    fun format(): String = FORMATTER.format(value)

    override fun toString(): String = format()

    companion object {
        private val FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM")

        fun parse(value: String): OrderMonth = OrderMonth(YearMonth.parse(value, FORMATTER))

        fun from(year: Int, month: Int): OrderMonth = OrderMonth(YearMonth.of(year, month))
    }
}
