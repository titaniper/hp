package io.hhplus.tdd.point

import io.hhplus.tdd.database.PointHistoryTable
import io.hhplus.tdd.database.UserPointTable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PointServiceTest {
    private lateinit var userPointTable: UserPointTable
    private lateinit var pointHistoryTable: PointHistoryTable
    private lateinit var pointService: PointService

    @BeforeEach
    fun setUp() {
        userPointTable = UserPointTable()
        pointHistoryTable = PointHistoryTable()
        pointService = PointService(userPointTable, pointHistoryTable)
    }

    @Test
    fun `get returns zero balance for new user`() {
        val result = pointService.get(1L)

        assertEquals(1L, result.id)
        assertEquals(0L, result.point)
    }

    @Test
    fun `charge increases balance and logs history`() {
        val firstCharge = pointService.charge(1L, 100L)
        val secondCharge = pointService.charge(1L, 200L)

        assertEquals(300L, secondCharge.point)

        val histories = pointService.history(1L)
        assertEquals(2, histories.size)
        assertEquals(TransactionType.CHARGE, histories[0].type)
        assertEquals(100L, histories[0].amount)
        assertEquals(TransactionType.CHARGE, histories[1].type)
        assertEquals(200L, histories[1].amount)
        assertEquals(firstCharge.updateMillis, histories[0].timeMillis)
        assertEquals(secondCharge.updateMillis, histories[1].timeMillis)
    }

    @Test
    fun `use decreases balance when sufficient`() {
        pointService.charge(1L, 400L)

        val remaining = pointService.use(1L, 100L)

        assertEquals(300L, remaining.point)

        val histories = pointService.history(1L)
        assertEquals(2, histories.size)
        assertEquals(TransactionType.USE, histories.last().type)
        assertEquals(100L, histories.last().amount)
    }

    @Test
    fun `use throws when insufficient balance`() {
        pointService.charge(1L, 100L)

        assertThrows(IllegalArgumentException::class.java) {
            pointService.use(1L, 200L)
        }
    }

    @Test
    fun `history is user specific`() {
        pointService.charge(1L, 200L)
        pointService.use(1L, 100L)
        pointService.charge(2L, 300L)

        val user1History = pointService.history(1L)
        val user2History = pointService.history(2L)

        assertEquals(2, user1History.size)
        assertEquals(listOf(TransactionType.CHARGE, TransactionType.USE), user1History.map { it.type })
        assertEquals(1, user2History.size)
        assertEquals(TransactionType.CHARGE, user2History[0].type)
        assertEquals(300L, user2History[0].amount)
    }

    @Test
    fun `charge rejects non 100 unit amount`() {
        assertThrows(IllegalArgumentException::class.java) {
            pointService.charge(1L, 150L)
        }
    }

    @Test
    fun `use rejects non 100 unit amount`() {
        pointService.charge(1L, 200L)

        assertThrows(IllegalArgumentException::class.java) {
            pointService.use(1L, 50L)
        }
    }

    @Test
    fun `charge rejects when exceeding max balance`() {
        pointService.charge(1L, 1_000_000L)

        assertThrows(IllegalArgumentException::class.java) {
            pointService.charge(1L, 100L)
        }
    }
}
