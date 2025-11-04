package io.joopang.domain.common

@JvmInline
value class PasswordHash(val value: String) {

    init {
        require(value.isNotBlank()) { "Password hash must not be blank" }
        require(value.length >= MIN_LENGTH) { "Password hash is too short" }
    }

    override fun toString(): String = "******"

    companion object {
        private const val MIN_LENGTH = 8
    }
}
