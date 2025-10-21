package io.hhplus.tdd.point

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PointConcurrencyIntegrationTest @Autowired constructor(
    private val pointService: PointService,
) {

    @Test
    fun `concurrent charges for same user accumulate correctly`() {
        val userId = 1L
        val taskCount = 50
        val executor = Executors.newFixedThreadPool(taskCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(taskCount)

        repeat(taskCount) {
            executor.submit {
                startLatch.await()
                try {
                    pointService.charge(userId, 100L)
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        doneLatch.await(5, TimeUnit.SECONDS)
        executor.shutdown()

        val userPoint = pointService.get(userId)
        assertEquals(taskCount * 100L, userPoint.point)

        val histories = pointService.history(userId)
        assertEquals(taskCount, histories.size)
        assertTrue(histories.all { it.type == TransactionType.CHARGE && it.amount == 100L })
    }
}
