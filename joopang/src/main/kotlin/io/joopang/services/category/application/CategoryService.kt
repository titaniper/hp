package io.joopang.services.category.application

import io.joopang.services.category.domain.Category
import io.joopang.services.category.domain.CategoryNotFoundException
import io.joopang.services.category.domain.CategoryStatus
import io.joopang.services.category.infrastructure.CategoryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class CategoryService(
    private val categoryRepository: CategoryRepository,
) {

    fun listCategories(parentId: Long?): List<Output> =
        if (parentId == null) {
            categoryRepository.findAll()
        } else {
            categoryRepository.findByParentId(parentId)
        }.map { it.toOutput() }

    fun getCategory(id: Long): Output =
        categoryRepository.findById(id)
            ?.toOutput()
            ?: throw CategoryNotFoundException(id.toString())

    @Transactional
    fun createCategory(command: CreateCategoryCommand): Output {
        val parent = command.parentId?.let { parentId ->
            categoryRepository.findById(parentId)
                ?: throw CategoryNotFoundException(parentId.toString())
        }

        val category = Category(
            id = command.id ?: 0,
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
        val parentId: Long?,
        val id: Long? = null,
    )

    data class Output(
        val id: Long?,
        val level: Int,
        val name: String,
        val status: CategoryStatus,
        val parentId: Long?,
    )
}
