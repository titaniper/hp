package io.joopang.services.common.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
data class Address(
    @Column(name = "zip_code", nullable = false, length = 10)
    var zipCode: String = "00000",

    @Column(name = "base_address", nullable = false)
    var baseAddress: String = "UNKNOWN",

    @Column(name = "detail_address")
    var detailAddress: String? = null,
) {
    init {
        require(zipCode.isNotBlank()) { "Zip code must not be blank" }
        require(baseAddress.isNotBlank()) { "Base address must not be blank" }
    }
}
