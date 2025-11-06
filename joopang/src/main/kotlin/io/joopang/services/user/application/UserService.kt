package io.joopang.services.user.application

import io.joopang.services.common.domain.Email
import io.joopang.services.common.domain.Money
import io.joopang.services.common.domain.PasswordHash
import io.joopang.services.user.domain.User
import io.joopang.services.user.domain.UserNotFoundException
import io.joopang.services.user.infrastructure.UserRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class UserService(
    private val userRepository: UserRepository,
) {

    fun listUsers(): List<User> = userRepository.findAll()

    fun getUser(id: UUID): User =
        userRepository.findById(id)
            ?: throw UserNotFoundException(id.toString())

    fun registerUser(command: RegisterUserCommand): User {
        val user = User(
            id = command.id ?: UUID.randomUUID(),
            email = command.email,
            password = command.password,
            firstName = command.firstName,
            lastName = command.lastName,
            balance = command.balance ?: Money.ZERO,
        )
        return userRepository.save(user)
    }

    data class RegisterUserCommand(
        val email: Email,
        val password: PasswordHash,
        val firstName: String?,
        val lastName: String?,
        val balance: Money?,
        val id: UUID? = null,
    )
}
