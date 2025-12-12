package io.joopang.services.coupon.contract

class InvalidCouponException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
