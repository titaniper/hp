package io.joopang.services.seller.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "sellers")
data class Seller(
    @Id
    @Column(columnDefinition = "BINARY(16)")
    var id: UUID = UUID(0L, 0L),

    @Column(nullable = false)
    var name: String = "",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var type: SellerType = SellerType.BRAND,

    @Column(name = "owner_id", columnDefinition = "BINARY(16)", nullable = false)
    var ownerId: UUID = UUID(0L, 0L),
) {
    init {
        require(name.isNotBlank()) { "Seller name must not be blank" }
    }
}
