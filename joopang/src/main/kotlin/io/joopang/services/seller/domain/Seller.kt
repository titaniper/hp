package io.joopang.services.seller.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "sellers")
class Seller(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "BIGINT")
    var id: Long = 0,

    @Column(nullable = false)
    var name: String = "",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var type: SellerType = SellerType.BRAND,

    @Column(name = "owner_id", columnDefinition = "BIGINT", nullable = false)
    var ownerId: Long = 0,
) {
    init {
        if (ownerId != 0L || name.isNotBlank()) {
            require(name.isNotBlank()) { "Seller name must not be blank" }
        }
    }

    @Suppress("unused")
    constructor() : this(
        id = 0,
        name = "",
        type = SellerType.BRAND,
        ownerId = 0,
    )
}
