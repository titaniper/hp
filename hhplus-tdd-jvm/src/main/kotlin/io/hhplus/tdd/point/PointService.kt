package io.hhplus.tdd.point

import io.hhplus.tdd.database.PointHistoryTable
import io.hhplus.tdd.database.UserPointTable
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.abs

@Service
class PointService(
    private val userPointTable: UserPointTable,
    private val pointHistoryTable: PointHistoryTable,
) {
    companion object {
        private const val POINT_UNIT = 100L
        private const val MAX_BALANCE = 1_000_000L
    }

    private val userLocks = ConcurrentHashMap<Long, ReentrantLock>()

    fun get(userId: Long): UserPoint {
        return userPointTable.selectById(userId)
    }

    fun charge(userId: Long, amount: Long): UserPoint {
        validateAmount(amount, "charge")
        return withUserLock(userId) {
            update(userId, amount, TransactionType.CHARGE)
        }
    }

    fun use(userId: Long, amount: Long): UserPoint {
        validateAmount(amount, "use")
        return withUserLock(userId) {
            update(userId, -amount, TransactionType.USE)
        }
    }

    fun history(userId: Long): List<PointHistory> {
        return pointHistoryTable.selectAllByUserId(userId)
    }

    private fun update(userId: Long, delta: Long, transactionType: TransactionType): UserPoint {
        val currentPoint = userPointTable.selectById(userId)
        val updatedAmount = currentPoint.point + delta

        if (delta > 0 && updatedAmount > MAX_BALANCE) {
            throw IllegalArgumentException("Point balance cannot exceed $MAX_BALANCE.")
        }
        if (updatedAmount < 0) {
            throw IllegalArgumentException("Insufficient point balance.")
        }

        val updatedPoint = userPointTable.insertOrUpdate(userId, updatedAmount)
        val historyAmount = abs(delta)

        pointHistoryTable.insert(
            id = userId,
            amount = historyAmount,
            transactionType = transactionType,
            updateMillis = updatedPoint.updateMillis,
        )

        return updatedPoint
    }

    private fun validateAmount(amount: Long, action: String) {
        if (amount <= 0) {
            throw IllegalArgumentException("Amount to $action must be positive.")
        }
        if (amount % POINT_UNIT != 0L) {
            throw IllegalArgumentException("Amount to $action must be in increments of $POINT_UNIT.")
        }
    }

    private fun <T> withUserLock(userId: Long, block: () -> T): T {
        val lock = userLocks.computeIfAbsent(userId) { ReentrantLock() }
        lock.lock()
        return try {
            block()
        } finally {
            lock.unlock()
            if (!lock.hasQueuedThreads()) {
                userLocks.remove(userId, lock)
            }
        }
    }
}
