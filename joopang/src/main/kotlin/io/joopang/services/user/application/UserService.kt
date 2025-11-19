package io.joopang.services.user.application

import io.joopang.services.common.domain.Email
import io.joopang.services.common.domain.Money
import io.joopang.services.common.domain.PasswordHash
import io.joopang.services.user.domain.User
import io.joopang.services.user.domain.UserNotFoundException
import io.joopang.services.user.infrastructure.UserRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class UserService(
    private val userRepository: UserRepository,
) {

    fun listUsers(): List<Output> =
        userRepository.findAll()
            .map { it.toOutput() }

    fun getUser(id: Long): Output =
        userRepository.findByIdOrNull(id)
            ?.toOutput()
            ?: throw UserNotFoundException(id.toString())

    @Transactional
    fun registerUser(command: RegisterUserCommand): Output {
        val user = User(
            id = command.id ?: 0,
            email = command.email,
            password = command.password,
            firstName = command.firstName,
            lastName = command.lastName,
            balance = command.balance ?: Money.ZERO,
        )
        return userRepository.save(user).toOutput()
    }

    private fun User.toOutput(): Output =
        Output(
            id = id,
            email = email,
            firstName = firstName,
            lastName = lastName,
            fullName = fullName(),
            balance = balance,
        )

    data class RegisterUserCommand(
        val email: Email,
        val password: PasswordHash,
        val firstName: String?,
        val lastName: String?,
        val balance: Money?,
        val id: Long? = null,
    )

    data class Output(
        val id: Long,
        val email: Email,
        val firstName: String?,
        val lastName: String?,
        val fullName: String?,
        val balance: Money,
    )
}
