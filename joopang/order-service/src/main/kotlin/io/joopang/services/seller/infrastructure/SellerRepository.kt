package io.joopang.services.seller.infrastructure

import io.joopang.services.seller.domain.Seller
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface SellerRepository : JpaRepository<Seller, Long>
