package io.joopang.services.order.infrastructure.jpa

import io.joopang.services.common.domain.Money
import io.joopang.services.common.domain.OrderMonth
import io.joopang.services.common.domain.Quantity
import io.joopang.services.order.domain.Order
import io.joopang.services.order.domain.OrderAggregate
import io.joopang.services.order.domain.OrderDiscount
import io.joopang.services.order.domain.OrderDiscountType
import io.joopang.services.order.domain.OrderItem
import io.joopang.services.order.domain.OrderStatus
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "orders")
class OrderEntity(
    @Id
    @Column(columnDefinition = "BINARY(16)")
    var id: UUID,

    @Column(name = "user_id", columnDefinition = "BINARY(16)", nullable = false)
    var userId: UUID,

    @Column(name = "image_url")
    var imageUrl: String?,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: OrderStatus,

    @Column(name = "recipient_name", nullable = false)
    var recipientName: String,

    @Column(name = "order_month", nullable = false, length = 7)
    var orderMonth: OrderMonth,

    @Column(name = "total_amount", precision = 19, scale = 2, nullable = false)
    var totalAmount: Money,

    @Column(name = "discount_amount", precision = 19, scale = 2, nullable = false)
    var discountAmount: Money,

    @Column(name = "ordered_at", nullable = false)
    var orderedAt: Instant,

    @Column(name = "paid_at")
    var paidAt: Instant?,

    @Column(columnDefinition = "TEXT")
    var memo: String?,

    @OneToMany(mappedBy = "order", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    var items: MutableList<OrderItemEntity> = mutableListOf(),

    @OneToMany(mappedBy = "order", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    var discounts: MutableList<OrderDiscountEntity> = mutableListOf(),
) {

    fun toAggregate(): OrderAggregate = OrderAggregate(
        order = Order(
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
        ),
        items = items.map(OrderItemEntity::toDomain),
        discounts = discounts.map(OrderDiscountEntity::toDomain),
    )

    fun updateFrom(aggregate: OrderAggregate) {
        imageUrl = aggregate.order.imageUrl
        status = aggregate.order.status
        recipientName = aggregate.order.recipientName
        orderMonth = aggregate.order.orderMonth
        totalAmount = aggregate.order.totalAmount
        discountAmount = aggregate.order.discountAmount
        orderedAt = aggregate.order.orderedAt
        paidAt = aggregate.order.paidAt
        memo = aggregate.order.memo

        items.clear()
        aggregate.items.forEach { item ->
            items.add(OrderItemEntity.from(item, this))
        }

        discounts.clear()
        aggregate.discounts.forEach { discount ->
            discounts.add(OrderDiscountEntity.from(discount, this))
        }
    }

    companion object {
        fun fromAggregate(aggregate: OrderAggregate): OrderEntity {
            val entity = OrderEntity(
                id = aggregate.order.id,
                userId = aggregate.order.userId,
                imageUrl = aggregate.order.imageUrl,
                status = aggregate.order.status,
                recipientName = aggregate.order.recipientName,
                orderMonth = aggregate.order.orderMonth,
                totalAmount = aggregate.order.totalAmount,
                discountAmount = aggregate.order.discountAmount,
                orderedAt = aggregate.order.orderedAt,
                paidAt = aggregate.order.paidAt,
                memo = aggregate.order.memo,
            )
            entity.items = aggregate.items.map { OrderItemEntity.from(it, entity) }.toMutableList()
            entity.discounts = aggregate.discounts.map { OrderDiscountEntity.from(it, entity) }.toMutableList()
            return entity
        }
    }
}

@Entity
@Table(name = "order_items")
class OrderItemEntity(
    @Id
    @Column(columnDefinition = "BINARY(16)")
    var id: UUID,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", columnDefinition = "BINARY(16)", nullable = false)
    var order: OrderEntity,

    @Column(name = "product_id", columnDefinition = "BINARY(16)")
    var productId: UUID?,

    @Column(name = "product_item_id", columnDefinition = "BINARY(16)")
    var productItemId: UUID?,

    @Column(name = "product_name", nullable = false)
    var productName: String,

    @Column(nullable = false)
    var quantity: Quantity,

    @Column(name = "unit_price", precision = 19, scale = 2, nullable = false)
    var unitPrice: Money,

    @Column(name = "subtotal", precision = 19, scale = 2, nullable = false)
    var subtotal: Money,

    @Column(name = "refunded_amount", precision = 19, scale = 2, nullable = false)
    var refundedAmount: Money,

    @Column(name = "refunded_quantity", nullable = false)
    var refundedQuantity: Quantity,
) {
    fun toDomain(): OrderItem = OrderItem(
        id = id,
        orderId = order.id,
        productId = productId,
        productItemId = productItemId,
        productName = productName,
        quantity = quantity,
        unitPrice = unitPrice,
        subtotal = subtotal,
        refundedAmount = refundedAmount,
        refundedQuantity = refundedQuantity,
    )

    companion object {
        fun from(domain: OrderItem, order: OrderEntity): OrderItemEntity = OrderItemEntity(
            id = domain.id,
            order = order,
            productId = domain.productId,
            productItemId = domain.productItemId,
            productName = domain.productName,
            quantity = domain.quantity,
            unitPrice = domain.unitPrice,
            subtotal = domain.subtotal,
            refundedAmount = domain.refundedAmount,
            refundedQuantity = domain.refundedQuantity,
        )
    }
}

@Entity
@Table(name = "order_discounts")
class OrderDiscountEntity(
    @Id
    @Column(columnDefinition = "BINARY(16)")
    var id: UUID,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", columnDefinition = "BINARY(16)", nullable = false)
    var order: OrderEntity,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var type: OrderDiscountType,

    @Column(name = "reference_id", columnDefinition = "BINARY(16)")
    var referenceId: UUID?,

    @Column(name = "price", precision = 19, scale = 2, nullable = false)
    var price: Money,

    @Column(name = "coupon_id", columnDefinition = "BINARY(16)")
    var couponId: UUID?,
) {
    fun toDomain(): OrderDiscount = OrderDiscount(
        id = id,
        orderId = order.id,
        type = type,
        referenceId = referenceId,
        price = price,
        couponId = couponId,
    )

    companion object {
        fun from(domain: OrderDiscount, order: OrderEntity): OrderDiscountEntity = OrderDiscountEntity(
            id = domain.id,
            order = order,
            type = domain.type,
            referenceId = domain.referenceId,
            price = domain.price,
            couponId = domain.couponId,
        )
    }
}
