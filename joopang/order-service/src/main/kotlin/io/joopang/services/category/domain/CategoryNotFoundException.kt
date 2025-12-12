package io.joopang.services.category.domain

class CategoryNotFoundException(categoryId: String) : RuntimeException("Category $categoryId not found")
