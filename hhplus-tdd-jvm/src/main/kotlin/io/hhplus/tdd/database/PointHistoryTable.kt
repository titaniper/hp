package io.hhplus.tdd.database

import io.hhplus.tdd.point.PointHistory
import io.hhplus.tdd.point.TransactionType
import org.springframework.stereotype.Component

/**
 * 포인트 이력 저장소 (Repository 계층)
 *
 * 주의사항:
 * - 이 클래스는 변경하지 않고 공개된 API만을 사용해 데이터를 제어합니다.
 * - 실제 데이터베이스 대신 메모리(MutableList)를 사용하는 테스트용 구현입니다.
 *
 * @Component 어노테이션:
 * - Spring Bean으로 등록되어 의존성 주입(DI) 가능
 * - 싱글톤으로 관리됨
 *
 * 설계 특징:
 * - Append-only 구조: 이력은 추가만 되고 수정/삭제되지 않음 (감사 추적)
 * - 자동 증가 ID: cursor를 사용한 순차적 ID 생성
 */
@Component
class PointHistoryTable {
    /**
     * 포인트 이력 데이터를 저장하는 인메모리 리스트
     *
     * MutableList:
     * - 순서가 보장되는 가변 리스트
     * - Thread-safe하지 않음: 동시에 여러 스레드가 추가하면 데이터 손실 가능
     */
    private val table = mutableListOf<PointHistory>()

    /**
     * 이력 ID 자동 생성을 위한 커서 (Auto Increment)
     *
     * cursor++:
     * - 현재 값을 사용한 후 1 증가 (Post-increment)
     * - Thread-safe하지 않음: 동시 접근 시 중복 ID 발생 가능
     *
     * Thread-safety 문제 예시:
     * - 스레드 A가 cursor 값 읽기 (1)
     * - 스레드 B가 cursor 값 읽기 (1) <- 중복!
     * - 스레드 A가 cursor++ 실행 (2)
     * - 스레드 B가 cursor++ 실행 (2) <- 중복!
     */
    private var cursor: Long = 1L

    /**
     * 포인트 이력 추가
     *
     * Thread.sleep():
     * - 데이터베이스 쓰기 지연을 시뮬레이션 (0~300ms)
     *
     * 매개변수 설명:
     * @param id 사용자 ID (userId로 사용됨, 매개변수명이 혼동 가능)
     * @param amount 포인트 변경 금액
     * @param transactionType 트랜잭션 타입 (CHARGE/USE)
     * @param updateMillis 트랜잭션 발생 시간
     * @return 생성된 이력 객체 (자동 생성된 ID 포함)
     *
     * 동작 순서:
     * 1. 랜덤 지연 시간 대기 (DB I/O 시뮬레이션)
     * 2. 새로운 PointHistory 객체 생성 (cursor로 ID 자동 할당)
     * 3. 리스트에 추가
     * 4. 생성된 객체 반환
     */
    fun insert(
        id: Long,
        amount: Long,
        transactionType: TransactionType,
        updateMillis: Long,
    ): PointHistory {
        Thread.sleep(Math.random().toLong() * 300L)  // DB 쓰기 지연 시뮬레이션
        val history = PointHistory(
            id = cursor++,           // 이력 ID 자동 생성 및 증가
            userId = id,             // 사용자 ID
            amount = amount,         // 포인트 금액
            type = transactionType,  // 트랜잭션 타입
            timeMillis = updateMillis, // 트랜잭션 시간
        )
        table.add(history)  // 리스트에 추가 (Append-only)
        return history
    }

    /**
     * 특정 사용자의 모든 포인트 이력 조회
     *
     * filter():
     * - Kotlin 컬렉션 함수로, 조건에 맞는 요소만 필터링
     * - SQL의 WHERE 절과 유사
     * - 예: SELECT * FROM point_history WHERE userId = ?
     *
     * it:
     * - 람다식의 암시적 매개변수 (PointHistory 타입)
     * - it.userId는 각 이력 객체의 userId를 의미
     *
     * @param userId 조회할 사용자 ID
     * @return 해당 사용자의 모든 이력 (시간순으로 정렬된 리스트)
     */
    fun selectAllByUserId(userId: Long): List<PointHistory> {
        return table.filter { it.userId == userId }
    }
}