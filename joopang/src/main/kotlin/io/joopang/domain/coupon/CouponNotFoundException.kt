package io.joopang.domain.coupon

class CouponNotFoundException(couponId: String) :
    RuntimeException("Coupon with id $couponId was not found")
