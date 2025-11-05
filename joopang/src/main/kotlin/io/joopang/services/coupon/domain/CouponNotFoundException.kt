package io.joopang.services.coupon.domain

class CouponNotFoundException(couponId: String) :
    RuntimeException("Coupon with id $couponId was not found")
