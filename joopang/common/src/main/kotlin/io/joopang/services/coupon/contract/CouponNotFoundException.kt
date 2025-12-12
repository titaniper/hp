package io.joopang.services.coupon.contract

class CouponNotFoundException(couponId: String) :
    RuntimeException("Coupon with id $couponId was not found")
