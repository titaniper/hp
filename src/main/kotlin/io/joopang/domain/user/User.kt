package io.joopang.domain.user

import io.joopang.domain.common.Email
import io.joopang.domain.common.PasswordHash
import java.util.UUID

data class User(
    val id: UUID,
    val email: Email,
    val password: PasswordHash,
    val firstName: String?,
    val lastName: String?,
    val point: Int = 0,
) {

    init {
        require(point >= 0) { "Point balance cannot be negative" }
    }

    fun fullName(): String? =
        listOfNotNull(firstName, lastName).takeIf { it.isNotEmpty() }?.joinToString(" ")
}
