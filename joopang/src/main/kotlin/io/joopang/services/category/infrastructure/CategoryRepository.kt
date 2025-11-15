package io.joopang.services.category.infrastructure

import io.joopang.services.category.domain.Category
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Repository

@Repository
class CategoryRepository(
    @PersistenceContext private val entityManager: EntityManager,
) {

    fun findAll(): List<Category> =
        entityManager.createQuery("select c from Category c", Category::class.java)
            .resultList

    fun findById(id: Long): Category? =
        entityManager.find(Category::class.java, id)

    fun findByParentId(parentId: Long?): List<Category> =
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

    fun save(category: Category): Category =
        entityManager.merge(category)
}
