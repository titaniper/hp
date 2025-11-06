package io.joopang.services.order.application

import java.util.UUID

interface ProductLockManager {
    fun <T> withProductLock(productId: UUID, action: () -> T): T
}
