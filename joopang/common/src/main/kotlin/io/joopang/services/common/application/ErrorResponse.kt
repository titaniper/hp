package io.joopang.services.common.application

import java.time.Instant

/**
 * Standard API error response payload shared across services.
 */
data class ErrorResponse(
    val code: String,
    val message: String,
    val timestamp: Instant = Instant.now(),
    val path: String? = null,
)
