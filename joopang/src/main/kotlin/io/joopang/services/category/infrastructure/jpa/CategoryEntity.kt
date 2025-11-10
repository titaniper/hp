package io.joopang.services.category.infrastructure.jpa

import io.joopang.services.category.domain.Category
import io.joopang.services.category.domain.CategoryStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "categories")
class CategoryEntity(
    @Id
    @Column(columnDefinition = "BINARY(16)")
    var id: UUID,

    @Column(nullable = false)
    var level: Int,

    @Column(nullable = false)
    var name: String,

    @Column(nullable = false, length = 32)
    var status: CategoryStatus,

    @Column(name = "parent_id", columnDefinition = "BINARY(16)")
    var parentId: UUID?,
) {
    fun toDomain(): Category = Category(
        id = id,
        level = level,
        name = name,
        status = status,
        parentId = parentId,
    )

    companion object {
        fun from(domain: Category): CategoryEntity = CategoryEntity(
            id = domain.id,
            level = domain.level,
            name = domain.name,
            status = domain.status,
            parentId = domain.parentId,
        )
    }
}
