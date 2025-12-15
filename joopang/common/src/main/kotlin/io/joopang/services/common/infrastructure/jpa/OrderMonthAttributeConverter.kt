package io.joopang.services.common.infrastructure.jpa

import io.joopang.services.common.domain.OrderMonth
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter(autoApply = true)
class OrderMonthAttributeConverter : AttributeConverter<OrderMonth, String> {

    override fun convertToDatabaseColumn(attribute: OrderMonth?): String? =
        attribute?.format()

    override fun convertToEntityAttribute(dbData: String?): OrderMonth? =
        dbData?.let(OrderMonth::parse)
}
