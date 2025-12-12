package io.joopang.services.seller.application

import io.joopang.services.common.domain.requireId
import io.joopang.services.seller.domain.Seller
import io.joopang.services.seller.domain.SellerNotFoundException
import io.joopang.services.seller.domain.SellerType
import io.joopang.services.seller.infrastructure.SellerRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class SellerService(
    private val sellerRepository: SellerRepository,
) {

    fun listSellers(): List<Output> =
        sellerRepository.findAll()
            .map { it.toOutput() }

    fun getSeller(id: Long): Output =
        sellerRepository.findByIdOrNull(id)
            ?.toOutput()
            ?: throw SellerNotFoundException(id.toString())

    @Transactional
    fun registerSeller(command: RegisterSellerCommand): Output {
        val seller = Seller(
            id = command.id,
            name = command.name,
            type = command.type,
            ownerId = command.ownerId,
        )
        return sellerRepository.save(seller).toOutput()
    }

    private fun Seller.toOutput(): Output =
        Output(
            id = requireId(),
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
