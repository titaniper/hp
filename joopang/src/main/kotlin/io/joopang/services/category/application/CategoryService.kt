package io.joopang.services.category.application

import io.joopang.services.category.domain.Category
import io.joopang.services.category.domain.CategoryNotFoundException
import io.joopang.services.category.domain.CategoryStatus
import io.joopang.services.category.infrastructure.CategoryRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class CategoryService(
    private val categoryRepository: CategoryRepository,
) {

    fun listCategories(parentId: UUID?): List<Category> =
        if (parentId == null) {
            categoryRepository.findAll()
        } else {
            categoryRepository.findByParentId(parentId)
        }

    fun getCategory(id: UUID): Category =
        categoryRepository.findById(id)
            ?: throw CategoryNotFoundException(id.toString())

    fun createCategory(command: CreateCategoryCommand): Category {
        val parent = command.parentId?.let { parentId ->
            categoryRepository.findById(parentId)
                ?: throw CategoryNotFoundException(parentId.toString())
        }

        val category = Category(
            id = command.id ?: UUID.randomUUID(),
            level = parent?.let { it.level + 1 } ?: 0,
            name = command.name,
            status = CategoryStatus(command.status),
            parentId = parent?.id,
        )

        return categoryRepository.save(category)
    }

    data class CreateCategoryCommand(
        val name: String,
        val status: String,
        val parentId: UUID?,
        val id: UUID? = null,
    )
}
