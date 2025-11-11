package io.joopang.services.category.infrastructure

import io.joopang.services.category.domain.Category
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Repository
@Transactional(readOnly = true)
open class CategoryRepository(
    @PersistenceContext private val entityManager: EntityManager,
) {

    open fun findAll(): List<Category> =
        entityManager.createQuery("select c from Category c", Category::class.java)
            .resultList

    open fun findById(id: UUID): Category? =
        entityManager.find(Category::class.java, id)

    open fun findByParentId(parentId: UUID?): List<Category> =
        if (parentId == null) {
            entityManager.createQuery(
                "select c from Category c where c.parentId is null",
                Category::class.java,
            )
                .resultList
        } else {
            entityManager.createQuery(
                "select c from Category c where c.parentId = :parentId",
                Category::class.java,
            )
                .setParameter("parentId", parentId)
                .resultList
        }

    @Transactional
    open fun save(category: Category): Category =
        entityManager.merge(category)
}
