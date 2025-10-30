package io.joopang

/**
 * Centralized application error codes to keep API responses consistent.
 */
object ErrorCodes {
    // 상품 관련
    const val PRODUCT_NOT_FOUND = "P001"
    const val INSUFFICIENT_STOCK = "P002"

    // 주문 관련
    const val INVALID_QUANTITY = "O001"
    const val ORDER_NOT_FOUND = "O002"

    // 결제 관련
    const val INSUFFICIENT_BALANCE = "PAY001"
    const val PAYMENT_FAILED = "PAY002"

    // 쿠폰 관련
    const val COUPON_SOLD_OUT = "C001"
    const val INVALID_COUPON = "C002"
    const val EXPIRED_COUPON = "C003"
    const val ALREADY_USED = "C004"
}
