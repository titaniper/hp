package io.joopang.services.seller.domain

import io.joopang.services.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

@Entity
@Table(name = "sellers")
class Seller(
    id: Long? = null,
    @Column(nullable = false)
    var name: String = "",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var type: SellerType = SellerType.BRAND,

    @Column(name = "owner_id", columnDefinition = "BIGINT", nullable = false)
    var ownerId: Long = 0,
) : BaseEntity(id) {
    init {
        if (ownerId != 0L || name.isNotBlank()) {
            require(name.isNotBlank()) { "Seller name must not be blank" }
        }
    }

    @Suppress("unused")
    constructor() : this(
        id = null,
        name = "",
        type = SellerType.BRAND,
        ownerId = 0,
    )
}
