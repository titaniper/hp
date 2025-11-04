package io.joopang.application.order

import java.util.UUID

fun interface ProductLockManager {
    fun <T> withProductLock(productId: UUID, action: () -> T): T
}
