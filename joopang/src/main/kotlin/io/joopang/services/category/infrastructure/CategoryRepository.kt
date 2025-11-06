package io.joopang.services.category.infrastructure

import io.joopang.services.category.domain.Category
import io.joopang.services.category.domain.CategoryStatus
import org.springframework.stereotype.Repository
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Repository
open class CategoryRepository {

    private val store = ConcurrentHashMap<UUID, Category>()

    init {
        seed()
    }

    open fun findAll(): List<Category> = store.values.toList()

    open fun findById(id: UUID): Category? = store[id]

    open fun findByParentId(parentId: UUID?): List<Category> =
        store.values.filter { it.parentId == parentId }

    open fun save(category: Category): Category {
        store[category.id] = category
        return category
    }

    private fun seed() {
        val electronicsId = UUID.fromString("33333333-4444-5555-6666-777777777777")
        val fashionId = UUID.fromString("44444444-5555-6666-7777-888888888888")
        val laptopId = UUID.fromString("55555555-6666-7777-8888-999999999999")

        store[electronicsId] = Category(
            id = electronicsId,
            level = 0,
            name = "Electronics",
            status = CategoryStatus("ACTIVE"),
            parentId = null,
        )

        store[fashionId] = Category(
            id = fashionId,
            level = 0,
            name = "Fashion",
            status = CategoryStatus("ACTIVE"),
            parentId = null,
        )

        store[laptopId] = Category(
            id = laptopId,
            level = 1,
            name = "Laptops",
            status = CategoryStatus("ACTIVE"),
            parentId = electronicsId,
        )
    }
}
