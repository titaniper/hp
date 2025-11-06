package io.joopang.services.seller.application

import io.joopang.services.seller.domain.Seller
import io.joopang.services.seller.domain.SellerNotFoundException
import io.joopang.services.seller.domain.SellerType
import io.joopang.services.seller.infrastructure.SellerRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class SellerService(
    private val sellerRepository: SellerRepository,
) {

    fun listSellers(): List<Seller> = sellerRepository.findAll()

    fun getSeller(id: UUID): Seller =
        sellerRepository.findById(id)
            ?: throw SellerNotFoundException(id.toString())

    fun registerSeller(command: RegisterSellerCommand): Seller {
        val seller = Seller(
            id = command.id ?: UUID.randomUUID(),
            name = command.name,
            type = command.type,
            ownerId = command.ownerId,
        )
        return sellerRepository.save(seller)
    }

    data class RegisterSellerCommand(
        val name: String,
        val type: SellerType,
        val ownerId: UUID,
        val id: UUID? = null,
    )
}
