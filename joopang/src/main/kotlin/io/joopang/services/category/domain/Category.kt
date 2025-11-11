package io.joopang.services.category.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "categories")
data class Category(
    @Id
    @Column(columnDefinition = "BINARY(16)")
    var id: UUID = UUID(0L, 0L),

    @Column(nullable = false)
    var level: Int = 0,

    @Column(nullable = false)
    var name: String = "",

    @Column(nullable = false, length = 32)
    var status: CategoryStatus = CategoryStatus("DRAFT"),

    @Column(name = "parent_id", columnDefinition = "BINARY(16)")
    var parentId: UUID? = null,
) {
    init {
        require(level >= 0) { "Category level must be non-negative" }
        require(name.isNotBlank()) { "Category name must not be blank" }
    }

    fun isRoot(): Boolean = parentId == null
}
