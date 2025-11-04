package io.joopang.domain.common

data class Address(
    val zipCode: String,
    val baseAddress: String,
    val detailAddress: String? = null,
) {
    init {
        require(zipCode.isNotBlank()) { "Zip code must not be blank" }
        require(baseAddress.isNotBlank()) { "Base address must not be blank" }
    }
}
