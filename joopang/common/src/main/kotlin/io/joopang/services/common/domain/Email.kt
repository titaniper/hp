package io.joopang.services.common.domain

private val EMAIL_REGEX =
    Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$")

@JvmInline
value class Email(val value: String) {

    init {
        require(value.isNotBlank()) { "Email must not be blank" }
        require(EMAIL_REGEX.matches(value)) { "Invalid email format: $value" }
    }

    override fun toString(): String = value
}
