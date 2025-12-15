package io.joopang.services.category.infrastructure

import io.joopang.services.category.domain.Category
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface CategoryRepository : JpaRepository<Category, Long> {
    fun findAllByParentId(parentId: Long): List<Category>
    fun findAllByParentIdIsNull(): List<Category>
}
