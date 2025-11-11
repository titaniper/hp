package io.joopang.services.user.infrastructure

import io.joopang.services.user.domain.User
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Repository
@Transactional(readOnly = true)
open class UserRepository(
    @PersistenceContext private val entityManager: EntityManager,
) {

    open fun findById(userId: UUID): User? =
        entityManager.find(User::class.java, userId)

    open fun findAll(): List<User> =
        entityManager.createQuery("select u from User u", User::class.java)
            .resultList

    @Transactional
    open fun save(user: User): User =
        entityManager.merge(user)
}
