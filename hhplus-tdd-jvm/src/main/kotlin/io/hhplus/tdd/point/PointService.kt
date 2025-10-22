package io.hhplus.tdd.point

import io.hhplus.tdd.database.PointHistoryTable
import io.hhplus.tdd.database.UserPointTable
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.abs

/**
 * 포인트 비즈니스 로직 서비스
 *
 * @Service 어노테이션:
 * - Spring Bean으로 등록되어 의존성 주입 가능
 * - @Component의 특수화된 형태로, 비즈니스 로직 계층을 나타냄
 * - 싱글톤으로 관리됨
 *
 * 책임 (Responsibilities):
 * - 포인트 충전/사용/조회 비즈니스 로직 처리
 * - 유효성 검증 (금액 단위, 최대 잔액 등)
 * - 동시성 제어 (사용자별 Lock)
 *
 * 동시성 문제와 해결:
 * - 문제: 여러 스레드가 동시에 같은 사용자의 포인트를 수정하면 데이터 불일치 발생
 * - 해결: 사용자별 ReentrantLock을 사용한 비관적 락(Pessimistic Lock)
 *
 * @property userPointTable 사용자 포인트 저장소
 * @property pointHistoryTable 포인트 이력 저장소
 */
@Service
class PointService(
    private val userPointTable: UserPointTable,
    private val pointHistoryTable: PointHistoryTable,
) {
    /**
     * 동반 객체: 상수 정의 (더 이상 필요 없음 - UserPoint로 이동)
     *
     * 리팩토링 노트:
     * - 도메인 규칙(POINT_UNIT, MAX_BALANCE)은 UserPoint로 이동
     * - Service는 애플리케이션 로직에만 집중
     * - 도메인 모델이 자신의 규칙을 관리 (응집도 향상)
     */
    companion object

    /**
     * 사용자별 Lock을 관리하는 동시성 안전 맵
     *
     * ConcurrentHashMap:
     * - Thread-safe한 HashMap 구현체
     * - 여러 스레드가 동시에 읽기/쓰기 가능
     * - 세그먼트 락킹으로 높은 동시성 제공
     *
     * Key: 사용자 ID
     * Value: 해당 사용자 전용 ReentrantLock
     *
     * 사용자별 Lock의 장점:
     * - 서로 다른 사용자의 요청은 동시에 처리 가능 (높은 처리량)
     * - 같은 사용자의 요청만 순차적으로 처리 (데이터 일관성)
     */
    private val userLocks = ConcurrentHashMap<Long, ReentrantLock>()

    /**
     * 사용자 포인트 조회
     *
     * 조회는 읽기 전용 작업이므로 Lock이 필요 없습니다.
     * (현재 구현에서는 조회와 수정이 동시에 발생해도 무방)
     *
     * @param userId 조회할 사용자 ID
     * @return 사용자 포인트 정보
     */
    fun get(userId: Long): UserPoint {
        return userPointTable.selectById(userId)
    }

    /**
     * 포인트 충전
     *
     * 비즈니스 규칙:
     * 1. 충전 금액은 양수여야 함
     * 2. 충전 금액은 100 단위여야 함
     * 3. 충전 후 잔액이 최대 잔액을 초과하면 안 됨
     *
     * 동시성 제어:
     * - withUserLock()을 사용하여 같은 사용자의 충전 요청을 순차 처리
     * - 다른 사용자의 충전은 병렬로 처리 가능
     *
     * @param userId 충전할 사용자 ID
     * @param amount 충전 금액 (100 단위, 양수)
     * @return 충전 후 사용자 포인트 정보
     * @throws IllegalArgumentException 금액이 유효하지 않거나 최대 잔액 초과 시
     */
    fun charge(userId: Long, amount: Long): UserPoint {
        validateAmount(amount, "charge")  // 금액 유효성 검증
        return withUserLock(userId) {      // 사용자별 Lock 획득
            update(userId, amount, TransactionType.CHARGE)
        }
    }

    /**
     * 포인트 사용
     *
     * 비즈니스 규칙:
     * 1. 사용 금액은 양수여야 함
     * 2. 사용 금액은 100 단위여야 함
     * 3. 잔액이 충분해야 함 (사용 후 잔액 >= 0)
     *
     * 동시성 제어:
     * - withUserLock()을 사용하여 같은 사용자의 사용 요청을 순차 처리
     * - 잔액 확인과 차감이 원자적으로 수행됨 (Race Condition 방지)
     *
     * @param userId 사용할 사용자 ID
     * @param amount 사용 금액 (100 단위, 양수)
     * @return 사용 후 사용자 포인트 정보
     * @throws IllegalArgumentException 금액이 유효하지 않거나 잔액 부족 시
     */
    fun use(userId: Long, amount: Long): UserPoint {
        validateAmount(amount, "use")      // 금액 유효성 검증
        return withUserLock(userId) {      // 사용자별 Lock 획득
            update(userId, -amount, TransactionType.USE)  // 음수로 전달하여 차감
        }
    }

    /**
     * 사용자의 포인트 이력 조회
     *
     * @param userId 조회할 사용자 ID
     * @return 사용자의 모든 포인트 이력 (시간순)
     */
    fun history(userId: Long): List<PointHistory> {
        return pointHistoryTable.selectAllByUserId(userId)
    }

    /**
     * 포인트 업데이트 (내부 메서드)
     *
     * 충전과 사용의 공통 로직을 처리합니다.
     * private 메서드로 외부에서 직접 호출 불가능합니다.
     *
     * 처리 흐름:
     * 1. 현재 포인트 조회
     * 2. 새로운 잔액 계산 (현재 잔액 + delta)
     * 3. 비즈니스 규칙 검증 (최대 잔액, 잔액 부족)
     * 4. 포인트 업데이트
     * 5. 이력 기록
     *
     * @param userId 사용자 ID
     * @param delta 변경 금액 (양수: 충전, 음수: 사용)
     * @param transactionType 트랜잭션 타입 (CHARGE/USE)
     * @return 업데이트된 사용자 포인트 정보
     * @throws IllegalArgumentException 최대 잔액 초과 또는 잔액 부족 시
     */
    private fun update(userId: Long, delta: Long, transactionType: TransactionType): UserPoint {
        // 1. 현재 포인트 조회
        val currentPoint = userPointTable.selectById(userId)

        // 2. 새로운 잔액 계산
        val updatedAmount = currentPoint.point + delta

        // 3. 비즈니스 규칙 검증
        if (delta > 0 && updatedAmount > MAX_BALANCE) {
            throw IllegalArgumentException("Point balance cannot exceed $MAX_BALANCE.")
        }
        if (updatedAmount < 0) {
            throw IllegalArgumentException("Insufficient point balance.")
        }

        // 4. 포인트 업데이트
        val updatedPoint = userPointTable.insertOrUpdate(userId, updatedAmount)

        // 5. 이력 기록 (절대값으로 저장)
        val historyAmount = abs(delta)  // 사용도 양수로 기록

        pointHistoryTable.insert(
            id = userId,
            amount = historyAmount,
            transactionType = transactionType,
            updateMillis = updatedPoint.updateMillis,
        )

        return updatedPoint
    }

    /**
     * 금액 유효성 검증 (내부 메서드)
     *
     * 검증 규칙:
     * 1. 금액은 양수여야 함 (> 0)
     * 2. 금액은 POINT_UNIT(100)의 배수여야 함
     *
     * @param amount 검증할 금액
     * @param action 동작 이름 (에러 메시지용: "charge" 또는 "use")
     * @throws IllegalArgumentException 유효하지 않은 금액인 경우
     */
    private fun validateAmount(amount: Long, action: String) {
        if (amount <= 0) {
            throw IllegalArgumentException("Amount to $action must be positive.")
        }
        if (amount % POINT_UNIT != 0L) {
            throw IllegalArgumentException("Amount to $action must be in increments of $POINT_UNIT.")
        }
    }

    /**
     * 사용자별 Lock을 사용하여 블록 실행 (내부 메서드)
     *
     * 고급 패턴: Higher-Order Function + Try-Finally
     *
     * Higher-Order Function:
     * - 함수를 매개변수로 받는 함수
     * - block: () -> T는 매개변수가 없고 T를 반환하는 람다
     * - Kotlin의 강력한 함수형 프로그래밍 기능
     *
     * computeIfAbsent():
     * - ConcurrentHashMap의 원자적 연산
     * - Key가 없으면 람다를 실행하여 Value 생성 및 저장
     * - Key가 있으면 기존 Value 반환
     * - 여러 스레드가 동시에 호출해도 하나의 Lock만 생성됨
     *
     * ReentrantLock:
     * - Java의 명시적 Lock (synchronized보다 유연)
     * - lock(): Lock 획득 (다른 스레드가 Lock을 가지고 있으면 대기)
     * - unlock(): Lock 해제
     * - 재진입 가능: 같은 스레드가 여러 번 lock() 호출 가능
     *
     * Try-Finally 패턴:
     * - try 블록에서 비즈니스 로직 실행
     * - finally 블록에서 반드시 Lock 해제 (예외 발생 시에도)
     * - Lock을 해제하지 않으면 데드락(Deadlock) 발생 가능
     *
     * Lock 정리 로직:
     * - hasQueuedThreads(): 다른 스레드가 이 Lock을 기다리는지 확인
     * - 대기 중인 스레드가 없으면 Map에서 Lock 제거 (메모리 누수 방지)
     * - remove(key, value): ConcurrentHashMap의 원자적 조건부 삭제
     *
     * @param userId 사용자 ID
     * @param block 실행할 코드 블록 (람다)
     * @return 블록의 실행 결과
     */
    private fun <T> withUserLock(userId: Long, block: () -> T): T {
        // 사용자별 Lock 획득 (없으면 생성)
        val lock = userLocks.computeIfAbsent(userId) { ReentrantLock() }

        // Lock 획득
        lock.lock()

        return try {
            // 비즈니스 로직 실행
            block()
        } finally {
            // Lock 해제 (반드시 실행됨)
            lock.unlock()

            // 대기 중인 스레드가 없으면 Lock 제거 (메모리 정리)
            if (!lock.hasQueuedThreads()) {
                userLocks.remove(userId, lock)
            }
        }
    }
}
