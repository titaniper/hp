package io.joopang.persistence.embeddable

import io.joopang.services.common.domain.Address
import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
data class AddressEmbeddable(
    @Column(name = "zip_code", nullable = false, length = 10)
    var zipCode: String = "",

    @Column(name = "base_address", nullable = false)
    var baseAddress: String = "",

    @Column(name = "detail_address")
    var detailAddress: String? = null,
) {

    fun toDomain(): Address = Address(
        zipCode = zipCode,
        baseAddress = baseAddress,
        detailAddress = detailAddress,
    )

    companion object {
        fun from(address: Address): AddressEmbeddable = AddressEmbeddable(
            zipCode = address.zipCode,
            baseAddress = address.baseAddress,
            detailAddress = address.detailAddress,
        )
    }
}
