package io.joopang.services.category.domain

import io.joopang.services.common.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table

@Entity
@Table(
    name = "categories",
    indexes = [
        Index(
            name = "idx_categories_parent_id",
            columnList = "parent_id",
        ),
    ],
)
class Category(
    id: Long? = null,
    @Column(nullable = false)
    var level: Int = 0,

    @Column(nullable = false)
    var name: String = "",

    @Column(nullable = false, length = 32)
    var status: CategoryStatus = CategoryStatus("DRAFT"),

    @Column(name = "parent_id", columnDefinition = "BIGINT")
    var parentId: Long? = null,
) : BaseEntity(id) {
    init {
        if (id != null || name.isNotBlank()) {
            require(level >= 0) { "Category level must be non-negative" }
            require(name.isNotBlank()) { "Category name must not be blank" }
        }
    }

    fun isRoot(): Boolean = parentId == null

    @Suppress("unused")
    constructor() : this(
        id = null,
        level = 0,
        name = "",
        status = CategoryStatus("DRAFT"),
        parentId = null,
    )
}
