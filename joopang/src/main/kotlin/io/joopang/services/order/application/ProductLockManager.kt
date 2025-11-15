package io.joopang.services.order.application

interface ProductLockManager {
    fun <T> withProductLock(productId: Long, action: () -> T): T
}
