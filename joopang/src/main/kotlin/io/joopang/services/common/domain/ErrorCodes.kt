package io.joopang.services.common.domain

import io.joopang.services.common.application.ErrorResponse

import org.springframework.http.HttpStatus

/**
 * Centralized application error codes with metadata for consistent API responses.
 */
enum class ErrorCode(
    val code: String,
    val httpStatus: HttpStatus,
    val defaultMessage: String,
) {
    // 상품 관련
    PRODUCT_NOT_FOUND("P001", HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."),
    INSUFFICIENT_STOCK("P002", HttpStatus.CONFLICT, "상품 재고가 부족합니다."),

    // 주문 관련
    INVALID_QUANTITY("O001", HttpStatus.BAD_REQUEST, "주문 수량이 올바르지 않습니다."),
    ORDER_NOT_FOUND("O002", HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다."),

    // 결제 관련
    INSUFFICIENT_BALANCE("PAY001", HttpStatus.PAYMENT_REQUIRED, "잔액이 부족합니다."),
    PAYMENT_FAILED("PAY002", HttpStatus.INTERNAL_SERVER_ERROR, "결제에 실패했습니다."),

    // 쿠폰 관련
    COUPON_SOLD_OUT("C001", HttpStatus.GONE, "쿠폰이 모두 소진되었습니다."),
    INVALID_COUPON("C002", HttpStatus.BAD_REQUEST, "유효하지 않은 쿠폰입니다."),
    EXPIRED_COUPON("C003", HttpStatus.GONE, "쿠폰 사용 기간이 만료되었습니다."),
    ALREADY_USED("C004", HttpStatus.CONFLICT, "이미 사용된 쿠폰입니다.");

    fun toErrorResponse(message: String? = null): ErrorResponse =
        ErrorResponse(code, message ?: defaultMessage)
}
