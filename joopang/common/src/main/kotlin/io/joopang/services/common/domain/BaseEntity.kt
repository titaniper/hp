package io.joopang.services.common.domain

import io.joopang.services.common.infrastructure.id.BaseEntityListener
import jakarta.persistence.Column
import jakarta.persistence.EntityListeners
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass

@MappedSuperclass
@EntityListeners(BaseEntityListener::class)
abstract class BaseEntity(
    id: Long? = null,
) {
    @Id
    @Column(columnDefinition = "BIGINT")
    var id: Long? = id
}

fun BaseEntity.requireId(): Long = requireNotNull(id) {
    val type = this::class.simpleName ?: "Entity"
    "$type id is not assigned"
}
