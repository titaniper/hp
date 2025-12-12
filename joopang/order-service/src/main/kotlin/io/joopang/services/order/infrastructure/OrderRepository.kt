package io.joopang.services.order.infrastructure

import io.joopang.services.order.domain.Order
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface OrderRepository : JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = ["items", "discounts"])
    @Query("select distinct o from Order o where o.id = :orderId")
    fun findWithDetailsById(@Param("orderId") orderId: Long): Order?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = ["items", "discounts"])
    @Query("select distinct o from Order o where o.id = :orderId")
    fun findWithDetailsByIdForUpdate(@Param("orderId") orderId: Long): Order?

    @EntityGraph(attributePaths = ["items", "discounts"])
    @Query("select distinct o from Order o order by o.orderedAt")
    fun findAllWithDetails(): List<Order>
}
