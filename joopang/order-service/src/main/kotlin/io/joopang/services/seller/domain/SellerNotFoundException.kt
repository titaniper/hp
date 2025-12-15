package io.joopang.services.seller.domain

class SellerNotFoundException(sellerId: String) : RuntimeException("Seller $sellerId not found")
