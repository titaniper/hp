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

    fun listCategories(parentId: UUID?): List<Output> =
        if (parentId == null) {
            categoryRepository.findAll()
        } else {
            categoryRepository.findByParentId(parentId)
        }.map { it.toOutput() }

    fun getCategory(id: UUID): Output =
        categoryRepository.findById(id)
            ?.toOutput()
            ?: throw CategoryNotFoundException(id.toString())

    fun createCategory(command: CreateCategoryCommand): Output {
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

        return categoryRepository.save(category).toOutput()
    }

    private fun Category.toOutput(): Output =
        Output(
            id = id,
            level = level,
            name = name,
            status = status,
            parentId = parentId,
        )

    data class CreateCategoryCommand(
        val name: String,
        val status: String,
        val parentId: UUID?,
        val id: UUID? = null,
    )

    data class Output(
        val id: UUID,
        val level: Int,
        val name: String,
        val status: CategoryStatus,
        val parentId: UUID?,
    )
}
