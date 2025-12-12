package io.joopang.common.lock

import java.util.concurrent.TimeUnit

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class DistributedLock(
    val prefix: String,
    val key: String,
    val waitTime: Long = 2,
    val leaseTime: Long = 5,
    val timeUnit: TimeUnit = TimeUnit.SECONDS,
    val failureMessage: String = "잠시 후 다시 시도해주세요.",
)
