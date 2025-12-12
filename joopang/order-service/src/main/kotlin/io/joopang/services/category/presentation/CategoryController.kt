package io.joopang.services.category.presentation

import io.joopang.services.category.application.CategoryService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/categories")
class CategoryController(
    private val categoryService: CategoryService,
) {

    @GetMapping
    fun listCategories(
        @RequestParam(required = false) parentId: Long?,
    ): List<CategoryResponse> =
        categoryService
            .listCategories(parentId)
            .map { it.toResponse() }

    @GetMapping("/{id}")
    fun getCategory(
        @PathVariable id: Long,
    ): CategoryResponse =
        categoryService
            .getCategory(id)
            .toResponse()

    @PostMapping
    fun createCategory(
        @RequestBody request: CreateCategoryRequest,
    ): CategoryResponse =
        categoryService
            .createCategory(request.toCommand())
            .toResponse()

    private fun CategoryService.Output.toResponse(): CategoryResponse =
        CategoryResponse(
            id = id,
            name = name,
            level = level,
            status = status.value,
            parentId = parentId,
        )

    private fun CreateCategoryRequest.toCommand(): CategoryService.CreateCategoryCommand =
        CategoryService.CreateCategoryCommand(
            name = name,
            status = status,
            parentId = parentId,
            id = id,
        )
}

data class CreateCategoryRequest(
    val name: String,
    val status: String,
    val parentId: Long?,
    val id: Long? = null,
)

data class CategoryResponse(
    val id: Long?,
    val name: String,
    val level: Int,
    val status: String,
    val parentId: Long?,
)
