package io.joopang.services.category.domain

import java.util.UUID

data class Category(
    val id: UUID,
    val level: Int,
    val name: String,
    val status: CategoryStatus,
    val parentId: UUID? = null,
) {
    init {
        require(level >= 0) { "Category level must be non-negative" }
        require(name.isNotBlank()) { "Category name must not be blank" }
    }

    fun isRoot(): Boolean = parentId == null
}
