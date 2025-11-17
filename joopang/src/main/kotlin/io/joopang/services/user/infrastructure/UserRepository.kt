package io.joopang.services.user.infrastructure

import io.joopang.services.user.domain.User
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Repository

@Repository
class UserRepository(
    @PersistenceContext private val entityManager: EntityManager,
) {

    fun findById(userId: Long): User? =
        entityManager.find(User::class.java, userId)

    fun findByIdForUpdate(userId: Long): User? =
        entityManager.find(User::class.java, userId, LockModeType.PESSIMISTIC_WRITE)

    fun findAll(): List<User> =
        entityManager.createQuery("select u from User u", User::class.java)
            .resultList

    fun save(user: User): User =
        entityManager.merge(user)
}
