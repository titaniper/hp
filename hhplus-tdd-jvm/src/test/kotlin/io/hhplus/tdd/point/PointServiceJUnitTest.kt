package io.hhplus.tdd.point

import io.hhplus.tdd.database.PointHistoryTable
import io.hhplus.tdd.database.UserPointTable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * PointService 단위 테스트 (JUnit 5 스타일)
 *
 * 테스트 클래스 구조:
 * - JUnit 5 (Jupiter) 사용
 * - 각 테스트마다 독립적인 테스트 환경 구성
 * - 테스트 간 상호작용 방지 (격리성)
 *
 * 단위 테스트의 특징:
 * - 외부 의존성(DB, 네트워크) 없이 메모리에서만 실행
 * - 빠른 실행 속도 (밀리초 단위)
 * - 비즈니스 로직의 정확성 검증
 */
class PointServiceJUnitTest {
    /**
     * lateinit var:
     * - 나중에 초기화될 변수 (Late-Initialized Variable)
     * - val이 아닌 var로 선언 (재할당 가능)
     * - @BeforeEach에서 각 테스트 전에 초기화됨
     */
    private lateinit var userPointTable: UserPointTable
    private lateinit var pointHistoryTable: PointHistoryTable
    private lateinit var pointService: PointService

    /**
     * 테스트 셋업 메서드
     *
     * @BeforeEach:
     * - 각 테스트 메서드 실행 전에 호출됨
     * - 테스트 환경을 초기화하여 테스트 간 격리성 보장
     * - JUnit 4의 @Before와 동일
     *
     * 셋업에서 수행하는 작업:
     * 1. 새로운 테이블 인스턴스 생성 (깨끗한 상태)
     * 2. PointService 인스턴스 생성 (테스트 대상)
     */
    @BeforeEach
    fun setUp() {
        userPointTable = UserPointTable()
        pointHistoryTable = PointHistoryTable()
        pointService = PointService(userPointTable, pointHistoryTable)
    }

    /**
     * 신규 사용자 조회 시 0 포인트 반환 테스트
     *
     * @Test:
     * - 이 메서드가 테스트 케이스임을 나타냄
     * - JUnit이 자동으로 실행함
     *
     * Backtick 함수명:
     * - Kotlin은 함수명을 백틱(`)으로 감싸면 공백과 특수문자 사용 가능
     * - 테스트 설명을 자연어로 작성 가능 (가독성 향상)
     *
     * Given-When-Then 패턴:
     * - Given: 신규 사용자 (사전 조건 없음)
     * - When: 포인트 조회
     * - Then: 0 포인트 반환
     */
    @Test
    fun `get returns zero balance for new user`() {
        // When: 신규 사용자의 포인트 조회
        val result = pointService.get(1L)

        // Then: ID는 1이고, 포인트는 0이어야 함
        assertEquals(1L, result.id)
        assertEquals(0L, result.point)
    }

    /**
     * 포인트 충전 시 잔액 증가 및 이력 기록 테스트
     *
     * 검증 사항:
     * 1. 잔액이 누적되어 증가함
     * 2. 각 충전 내역이 이력에 기록됨
     * 3. 이력의 시간이 UserPoint의 업데이트 시간과 일치함
     */
    @Test
    fun `charge increases balance and logs history`() {
        // When: 100 포인트, 200 포인트 순차적으로 충전
        val firstCharge = pointService.charge(1L, 100L)
        val secondCharge = pointService.charge(1L, 200L)

        // Then: 잔액은 300 포인트 (100 + 200)
        assertEquals(300L, secondCharge.point)

        // Then: 이력이 2개 기록되어야 함
        val histories = pointService.history(1L)
        assertEquals(2, histories.size)

        // Then: 첫 번째 이력 검증
        assertEquals(TransactionType.CHARGE, histories[0].type)
        assertEquals(100L, histories[0].amount)
        assertEquals(firstCharge.updateMillis, histories[0].timeMillis)

        // Then: 두 번째 이력 검증
        assertEquals(TransactionType.CHARGE, histories[1].type)
        assertEquals(200L, histories[1].amount)
        assertEquals(secondCharge.updateMillis, histories[1].timeMillis)
    }

    /**
     * 잔액이 충분할 때 포인트 사용 성공 테스트
     *
     * 검증 사항:
     * 1. 잔액이 정확히 차감됨
     * 2. 사용 내역이 이력에 기록됨
     */
    @Test
    fun `use decreases balance when sufficient`() {
        // Given: 400 포인트 충전
        pointService.charge(1L, 400L)

        // When: 100 포인트 사용
        val remaining = pointService.use(1L, 100L)

        // Then: 잔액은 300 포인트 (400 - 100)
        assertEquals(300L, remaining.point)

        // Then: 이력은 2개 (충전 1개, 사용 1개)
        val histories = pointService.history(1L)
        assertEquals(2, histories.size)

        // Then: 마지막 이력은 사용 내역
        assertEquals(TransactionType.USE, histories.last().type)
        assertEquals(100L, histories.last().amount)
    }

    /**
     * 잔액 부족 시 예외 발생 테스트
     *
     * assertThrows():
     * - 특정 코드 블록이 예외를 던지는지 검증
     * - 예외가 발생하지 않으면 테스트 실패
     * - 다른 타입의 예외가 발생해도 테스트 실패
     *
     * 검증 사항:
     * - 잔액(100) < 사용금액(200)일 때 IllegalArgumentException 발생
     */
    @Test
    fun `use throws when insufficient balance`() {
        // Given: 100 포인트 충전
        pointService.charge(1L, 100L)

        // When & Then: 200 포인트 사용 시도 시 예외 발생
        assertThrows(IllegalArgumentException::class.java) {
            pointService.use(1L, 200L)
        }
    }

    /**
     * 포인트 이력의 사용자별 격리 테스트
     *
     * 검증 사항:
     * - 사용자 1의 이력에는 사용자 1의 트랜잭션만 포함됨
     * - 사용자 2의 이력에는 사용자 2의 트랜잭션만 포함됨
     * - 사용자 간 이력이 섞이지 않음 (데이터 격리)
     *
     * map() 함수:
     * - List<PointHistory>를 List<TransactionType>으로 변환
     * - { it.type }은 각 이력의 type 필드를 추출
     */
    @Test
    fun `history is user specific`() {
        // Given: 사용자 1은 충전/사용, 사용자 2는 충전만 수행
        pointService.charge(1L, 200L)
        pointService.use(1L, 100L)
        pointService.charge(2L, 300L)

        // When: 각 사용자의 이력 조회
        val user1History = pointService.history(1L)
        val user2History = pointService.history(2L)

        // Then: 사용자 1의 이력은 2개 (충전, 사용)
        assertEquals(2, user1History.size)
        assertEquals(listOf(TransactionType.CHARGE, TransactionType.USE), user1History.map { it.type })

        // Then: 사용자 2의 이력은 1개 (충전)
        assertEquals(1, user2History.size)
        assertEquals(TransactionType.CHARGE, user2History[0].type)
        assertEquals(300L, user2History[0].amount)
    }

    /**
     * 충전 시 100 단위가 아닌 금액 거부 테스트
     *
     * 검증 사항:
     * - 150은 100의 배수가 아니므로 IllegalArgumentException 발생
     * - 비즈니스 규칙 검증
     */
    @Test
    fun `charge rejects non 100 unit amount`() {
        // When & Then: 150 포인트 충전 시도 시 예외 발생
        assertThrows(IllegalArgumentException::class.java) {
            pointService.charge(1L, 150L)
        }
    }

    /**
     * 사용 시 100 단위가 아닌 금액 거부 테스트
     *
     * 검증 사항:
     * - 50은 100의 배수가 아니므로 IllegalArgumentException 발생
     */
    @Test
    fun `use rejects non 100 unit amount`() {
        // Given: 200 포인트 충전
        pointService.charge(1L, 200L)

        // When & Then: 50 포인트 사용 시도 시 예외 발생
        assertThrows(IllegalArgumentException::class.java) {
            pointService.use(1L, 50L)
        }
    }

    /**
     * 최대 잔액 초과 충전 거부 테스트
     *
     * 검증 사항:
     * - 최대 잔액은 1,000,000 포인트
     * - 이미 1,000,000 포인트일 때 추가 충전 불가
     * - IllegalArgumentException 발생
     *
     * Kotlin 숫자 리터럴:
     * - 1_000_000L: 언더스코어로 가독성 향상 (1000000과 동일)
     * - L 접미사: Long 타입 명시
     */
    @Test
    fun `charge rejects when exceeding max balance`() {
        // Given: 최대 잔액(1,000,000) 충전
        pointService.charge(1L, 1_000_000L)

        // When & Then: 추가 충전 시도 시 예외 발생
        assertThrows(IllegalArgumentException::class.java) {
            pointService.charge(1L, 100L)
        }
    }
}
