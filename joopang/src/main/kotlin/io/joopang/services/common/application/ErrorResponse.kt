package io.joopang.services.common.application

/**
 * Standard API error response payload shared across services.
 */
data class ErrorResponse(
    val code: String,
    val message: String,
)
