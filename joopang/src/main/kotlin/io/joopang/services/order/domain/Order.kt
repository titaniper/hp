package io.joopang.services.order.domain

import io.joopang.services.common.domain.Money
import io.joopang.services.common.domain.OrderMonth
import io.joopang.services.common.infrastructure.jpa.OrderMonthAttributeConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(
    name = "orders",
    indexes = [
        Index(
            name = "idx_orders_status_paid_at_desc",
            columnList = "status, paid_at DESC",
        ),
        Index(
            name = "idx_orders_ordered_at_desc",
            columnList = "ordered_at DESC",
        ),
    ],
)
class Order(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "BIGINT")
    var id: Long = 0,

    @Column(name = "user_id", columnDefinition = "BIGINT", nullable = false)
    var userId: Long = 0,

    @Column(name = "image_url")
    var imageUrl: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: OrderStatus = OrderStatus.PENDING,

    @Column(name = "recipient_name", nullable = false)
    var recipientName: String = "",

    @Convert(converter = OrderMonthAttributeConverter::class)
    @Column(name = "order_month", nullable = false, length = 7)
    var orderMonth: OrderMonth = OrderMonth.from(1970, 1),

    @Column(name = "total_amount", precision = 19, scale = 2, nullable = false)
    var totalAmount: Money = Money.ZERO,

    @Column(name = "discount_amount", precision = 19, scale = 2, nullable = false)
    var discountAmount: Money = Money.ZERO,

    @Column(name = "ordered_at", nullable = false)
    var orderedAt: Instant = Instant.EPOCH,

    @Column(name = "paid_at")
    var paidAt: Instant? = null,

    @Column(columnDefinition = "TEXT")
    var memo: String? = null,
) {

    init {
        if (id != 0L || recipientName.isNotBlank()) {
            require(recipientName.isNotBlank()) { "Recipient name must not be blank" }
            require(totalAmount >= Money.ZERO) { "Total amount cannot be negative" }
            require(discountAmount >= Money.ZERO) { "Discount amount cannot be negative" }
            require(discountAmount <= totalAmount) { "Discount cannot exceed total amount" }
        }
    }

    fun payableAmount(): Money = totalAmount - discountAmount

    fun canPay(): Boolean = status == OrderStatus.PENDING

    fun markPaid(paidTimestamp: Instant): Order =
        copy(status = OrderStatus.PAID, paidAt = paidTimestamp)

    @Suppress("unused")
    constructor() : this(
        id = 0,
        userId = 0,
        imageUrl = null,
        status = OrderStatus.PENDING,
        recipientName = "",
        orderMonth = OrderMonth.from(1970, 1),
        totalAmount = Money.ZERO,
        discountAmount = Money.ZERO,
        orderedAt = Instant.EPOCH,
        paidAt = null,
        memo = null,
    )

    fun copy(
        id: Long = this.id,
        userId: Long = this.userId,
        imageUrl: String? = this.imageUrl,
        status: OrderStatus = this.status,
        recipientName: String = this.recipientName,
        orderMonth: OrderMonth = this.orderMonth,
        totalAmount: Money = this.totalAmount,
        discountAmount: Money = this.discountAmount,
        orderedAt: Instant = this.orderedAt,
        paidAt: Instant? = this.paidAt,
        memo: String? = this.memo,
    ): Order =
        Order(
            id = id,
            userId = userId,
            imageUrl = imageUrl,
            status = status,
            recipientName = recipientName,
            orderMonth = orderMonth,
            totalAmount = totalAmount,
            discountAmount = discountAmount,
            orderedAt = orderedAt,
            paidAt = paidAt,
            memo = memo,
        )
}
