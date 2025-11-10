package io.joopang.services.seller.domain

import java.util.UUID

data class Seller(
    val id: UUID,
    val name: String,
    val type: SellerType,
    val ownerId: UUID,
) {
    init {
        require(name.isNotBlank()) { "Seller name must not be blank" }
    }
}
