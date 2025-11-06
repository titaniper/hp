package io.joopang.services.seller.infrastructure

import io.joopang.services.seller.domain.Seller
import io.joopang.services.seller.domain.SellerType
import org.springframework.stereotype.Repository
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Repository
open class SellerRepository {

    private val store = ConcurrentHashMap<UUID, Seller>()

    init {
        seed()
    }

    open fun findAll(): List<Seller> = store.values.toList()

    open fun findById(id: UUID): Seller? = store[id]

    open fun save(seller: Seller): Seller {
        store[seller.id] = seller
        return seller
    }

    private fun seed() {
        val brandSellerId = UUID.fromString("11111111-2222-3333-4444-555555555555")
        store[brandSellerId] = Seller(
            id = brandSellerId,
            name = "Joopang Originals",
            type = SellerType.BRAND,
            ownerId = UUID.fromString("aaaaaaaa-1111-2222-3333-444444444444"),
        )

        val personSellerId = UUID.fromString("22222222-3333-4444-5555-666666666666")
        store[personSellerId] = Seller(
            id = personSellerId,
            name = "Handcrafted Goods",
            type = SellerType.PERSON,
            ownerId = UUID.fromString("bbbbbbbb-1111-2222-3333-444444444444"),
        )
    }
}
