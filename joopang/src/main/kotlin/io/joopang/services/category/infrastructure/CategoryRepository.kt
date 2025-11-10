package io.joopang.services.category.infrastructure

import io.joopang.services.category.domain.Category
import io.joopang.services.category.infrastructure.jpa.CategoryEntity
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
        entityManager.createQuery("select c from CategoryEntity c", CategoryEntity::class.java)
            .resultList
            .map(CategoryEntity::toDomain)

    open fun findById(id: UUID): Category? =
        entityManager.find(CategoryEntity::class.java, id)?.toDomain()

    open fun findByParentId(parentId: UUID?): List<Category> =
        if (parentId == null) {
            entityManager.createQuery(
                "select c from CategoryEntity c where c.parentId is null",
                CategoryEntity::class.java,
            )
                .resultList
        } else {
            entityManager.createQuery(
                "select c from CategoryEntity c where c.parentId = :parentId",
                CategoryEntity::class.java,
            )
                .setParameter("parentId", parentId)
                .resultList
        }.map(CategoryEntity::toDomain)

    @Transactional
    open fun save(category: Category): Category =
        entityManager.merge(CategoryEntity.from(category)).toDomain()
}
