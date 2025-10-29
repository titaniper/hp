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

/**
 * 동시성 통합 테스트
 *
 * 동시성 테스트의 목적:
 * - 여러 스레드가 동시에 같은 리소스에 접근할 때 데이터 무결성 검증
 * - Race Condition 방지 확인
 * - Lost Update 문제 방지 확인
 *
 * @SpringBootTest:
 * - Spring Context를 로드하여 실제 환경과 동일하게 테스트
 *
 * @DirtiesContext:
 * - 테스트 후 Context 재생성 (메모리 상태 초기화)
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PointConcurrencyIntegrationTest @Autowired constructor(
    private val pointService: PointService,
) {

    /**
     * 동일 사용자에 대한 동시 충전 테스트
     *
     * 테스트 시나리오:
     * - 50개의 스레드가 동시에 100 포인트씩 충전
     * - 예상 결과: 5,000 포인트
     * - 동시성 제어 실패 시: 5,000보다 작은 값 (Lost Update)
     *
     * Executors.newFixedThreadPool():
     * - 고정 크기 스레드 풀 생성
     * - 여러 작업을 병렬로 실행
     *
     * CountDownLatch:
     * - 스레드 간 동기화를 위한 유틸리티
     * - 카운트가 0이 될 때까지 대기
     *
     * startLatch (CountDownLatch(1)):
     * - 모든 스레드를 동시에 시작하기 위한 장치
     * - countDown() 호출 전까지 모든 스레드 대기
     * - countDown() 호출 시 모든 스레드 동시 실행
     *
     * doneLatch (CountDownLatch(taskCount)):
     * - 모든 작업이 완료될 때까지 대기
     * - 각 작업 완료 시 countDown() 호출
     * - 카운트가 0이 되면 메인 스레드 진행
     *
     * 동시성 테스트 패턴:
     * 1. 스레드 풀 생성
     * 2. 모든 스레드를 대기 상태로 만듦 (startLatch)
     * 3. 동시에 시작 (startLatch.countDown())
     * 4. 모든 작업 완료 대기 (doneLatch.await())
     * 5. 결과 검증
     */
    @Test
    fun `concurrent charges for same user accumulate correctly`() {
        // Given: 테스트 설정
        val userId = 1L
        val taskCount = 50  // 동시 실행할 작업 수
        val executor = Executors.newFixedThreadPool(taskCount)  // 50개 스레드 풀
        val startLatch = CountDownLatch(1)  // 동시 시작을 위한 Latch
        val doneLatch = CountDownLatch(taskCount)  // 완료 대기를 위한 Latch

        // When: 50개 스레드가 동시에 100 포인트 충전
        repeat(taskCount) {
            executor.submit {
                startLatch.await()  // 시작 신호 대기
                try {
                    pointService.charge(userId, 100L)  // 충전 실행
                } finally {
                    doneLatch.countDown()  // 작업 완료 신호
                }
            }
        }

        // 모든 스레드 동시 시작
        startLatch.countDown()

        // 모든 작업 완료 대기 (최대 5초)
        doneLatch.await(5, TimeUnit.SECONDS)

        // 스레드 풀 종료
        executor.shutdown()

        // Then: 잔액은 정확히 5,000 포인트여야 함
        val userPoint = pointService.get(userId)
        assertEquals(taskCount * 100L, userPoint.point)  // 50 * 100 = 5,000

        // Then: 이력은 정확히 50개여야 함
        val histories = pointService.history(userId)
        assertEquals(taskCount, histories.size)
        assertTrue(histories.all { it.type == TransactionType.CHARGE && it.amount == 100L })
        // all(): 모든 요소가 조건을 만족하는지 검증
    }
}
