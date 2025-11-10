package io.joopang.services.seller.presentation

import io.joopang.services.seller.application.SellerService
import io.joopang.services.seller.domain.SellerType
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@RestController
@RequestMapping("/api/sellers")
class SellerController(
    private val sellerService: SellerService,
) {

    @GetMapping
    fun listSellers(): List<SellerResponse> =
        sellerService
            .listSellers()
            .map { it.toResponse() }

    @GetMapping("/{id}")
    fun getSeller(
        @PathVariable id: UUID,
    ): SellerResponse =
        sellerService
            .getSeller(id)
            .toResponse()

    @PostMapping
    fun createSeller(
        @RequestBody request: CreateSellerRequest,
    ): SellerResponse =
        sellerService
            .registerSeller(request.toCommand())
            .toResponse()

    private fun SellerService.Output.toResponse(): SellerResponse =
        SellerResponse(
            id = id,
            name = name,
            type = type.name,
            ownerId = ownerId,
        )

    private fun CreateSellerRequest.toCommand(): SellerService.RegisterSellerCommand =
        SellerService.RegisterSellerCommand(
            name = name,
            type = parseType(type),
            ownerId = ownerId,
            id = id,
        )

    private fun parseType(value: String): SellerType =
        runCatching { SellerType.valueOf(value.uppercase()) }
            .getOrElse {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported seller type: $value",
                )
            }
}

data class CreateSellerRequest(
    val name: String,
    val type: String,
    val ownerId: UUID,
    val id: UUID? = null,
)

data class SellerResponse(
    val id: UUID,
    val name: String,
    val type: String,
    val ownerId: UUID,
)
