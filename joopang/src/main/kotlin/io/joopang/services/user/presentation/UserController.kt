package io.joopang.services.user.presentation

import io.joopang.services.common.domain.Email
import io.joopang.services.common.domain.Money
import io.joopang.services.common.domain.PasswordHash
import io.joopang.services.user.application.UserService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.util.UUID

@RestController
@RequestMapping("/api/users")
class UserController(
    private val userService: UserService,
) {

    @GetMapping
    fun listUsers(): List<UserResponse> =
        userService
            .listUsers()
            .map { it.toResponse() }

    @GetMapping("/{id}")
    fun getUser(
        @PathVariable id: UUID,
    ): UserResponse =
        userService
            .getUser(id)
            .toResponse()

    @PostMapping
    fun createUser(
        @RequestBody request: CreateUserRequest,
    ): UserResponse =
        userService
            .registerUser(request.toCommand())
            .toResponse()

    private fun UserService.Output.toResponse(): UserResponse =
        UserResponse(
            id = id,
            email = email.value,
            firstName = firstName,
            lastName = lastName,
            fullName = fullName,
            balance = balance.toBigDecimal(),
        )

    private fun CreateUserRequest.toCommand(): UserService.RegisterUserCommand =
        UserService.RegisterUserCommand(
            email = Email(email),
            password = PasswordHash(password),
            firstName = firstName,
            lastName = lastName,
            balance = balance?.let { Money.of(it) },
            id = id,
        )
}

data class CreateUserRequest(
    val email: String,
    val password: String,
    val firstName: String?,
    val lastName: String?,
    val balance: BigDecimal?,
    val id: UUID? = null,
)

data class UserResponse(
    val id: UUID,
    val email: String,
    val firstName: String?,
    val lastName: String?,
    val fullName: String?,
    val balance: BigDecimal,
)
