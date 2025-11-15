package io.joopang.services.seller.application

import io.joopang.services.seller.domain.Seller
import io.joopang.services.seller.domain.SellerNotFoundException
import io.joopang.services.seller.domain.SellerType
import io.joopang.services.seller.infrastructure.SellerRepository
import org.springframework.stereotype.Service

@Service
class SellerService(
    private val sellerRepository: SellerRepository,
) {

    fun listSellers(): List<Output> =
        sellerRepository.findAll()
            .map { it.toOutput() }

    fun getSeller(id: Long): Output =
        sellerRepository.findById(id)
            ?.toOutput()
            ?: throw SellerNotFoundException(id.toString())

    fun registerSeller(command: RegisterSellerCommand): Output {
        val seller = Seller(
            id = command.id ?: 0,
            name = command.name,
            type = command.type,
            ownerId = command.ownerId,
        )
        return sellerRepository.save(seller).toOutput()
    }

    private fun Seller.toOutput(): Output =
        Output(
            id = id,
            name = name,
            type = type,
            ownerId = ownerId,
        )

    data class RegisterSellerCommand(
        val name: String,
        val type: SellerType,
        val ownerId: Long,
        val id: Long? = null,
    )

    data class Output(
        val id: Long,
        val name: String,
        val type: SellerType,
        val ownerId: Long,
    )
}
