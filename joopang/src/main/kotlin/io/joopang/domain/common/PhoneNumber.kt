package io.joopang.domain.common

private val PHONE_REGEX = Regex("^[0-9+\\-]{7,15}\$")

@JvmInline
value class PhoneNumber(val value: String) {

    init {
        require(value.isNotBlank()) { "Phone number must not be blank" }
        require(PHONE_REGEX.matches(value)) { "Invalid phone number format: $value" }
    }

    override fun toString(): String = value
}
