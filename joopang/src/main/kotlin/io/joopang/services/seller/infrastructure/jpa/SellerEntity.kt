package io.joopang.services.seller.infrastructure.jpa

import io.joopang.services.seller.domain.Seller
import io.joopang.services.seller.domain.SellerType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "sellers")
class SellerEntity(
    @Id
    @Column(columnDefinition = "BINARY(16)")
    var id: UUID,

    @Column(nullable = false)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var type: SellerType,

    @Column(name = "owner_id", columnDefinition = "BINARY(16)", nullable = false)
    var ownerId: UUID,
) {
    fun toDomain(): Seller = Seller(
        id = id,
        name = name,
        type = type,
        ownerId = ownerId,
    )

    companion object {
        fun from(domain: Seller): SellerEntity = SellerEntity(
            id = domain.id,
            name = domain.name,
            type = domain.type,
            ownerId = domain.ownerId,
        )
    }
}
