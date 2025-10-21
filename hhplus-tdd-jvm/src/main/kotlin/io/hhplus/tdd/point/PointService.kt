package io.hhplus.tdd.point

import io.hhplus.tdd.database.PointHistoryTable
import io.hhplus.tdd.database.UserPointTable
import org.springframework.stereotype.Service

@Service
class PointService(
    private val userPointTable: UserPointTable,
    private val pointHistoryTable: PointHistoryTable,
) {
    private val lock = Any()

    fun get(userId: Long): UserPoint {
        return userPointTable.selectById(userId)
    }

    fun charge(userId: Long, amount: Long): UserPoint {
        require(amount > 0) { "Charge amount must be positive." }
        return update(userId, amount, TransactionType.CHARGE)
    }

    fun use(userId: Long, amount: Long): UserPoint {
        require(amount > 0) { "Usage amount must be positive." }
        return update(userId, -amount, TransactionType.USE)
    }

    fun history(userId: Long): List<PointHistory> {
        return pointHistoryTable.selectAllByUserId(userId)
    }

    private fun update(userId: Long, delta: Long, transactionType: TransactionType): UserPoint {
        synchronized(lock) {
            val currentPoint = userPointTable.selectById(userId)
            val updatedAmount = currentPoint.point + delta
            require(updatedAmount >= 0) { "Insufficient point balance." }

            val updatedPoint = userPointTable.insertOrUpdate(userId, updatedAmount)
            val historyAmount = kotlin.math.abs(delta)

            pointHistoryTable.insert(
                id = userId,
                amount = historyAmount,
                transactionType = transactionType,
                updateMillis = updatedPoint.updateMillis,
            )

            return updatedPoint
        }
    }
}
