package io.joopang.point

import io.joopang.database.PointHistoryTable
import io.joopang.database.UserPointTable
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

/**
 * PointService 단위 테스트 (Kotest 스타일)
 *
 * Kotest의 DescribeSpec:
 * - BDD(Behavior-Driven Development) 스타일의 테스트 작성
 * - describe-it 구조로 계층적 테스트 구성
 * - JUnit보다 읽기 쉽고 자연스러운 테스트 코드
 *
 * DescribeSpec 상속:
 * - DescribeSpec({ ... }): 람다 안에 테스트 코드 작성
 * - 클래스 본문이 아닌 init 블록처럼 동작
 *
 * Kotest의 장점:
 * - Kotlin 네이티브 DSL (Domain-Specific Language)
 * - 풍부한 Matcher (shouldBe, shouldHaveSize 등)
 * - 다양한 테스트 스타일 지원 (FunSpec, StringSpec, DescribeSpec 등)
 */
class PointServiceKotestSpec : DescribeSpec({

    // 테스트 픽스처 (Fixture): 각 테스트에서 사용할 객체들
    lateinit var userPointTable: UserPointTable
    lateinit var pointHistoryTable: PointHistoryTable
    lateinit var pointService: PointService

    /**
     * beforeEach:
     * - 각 테스트 케이스(it 블록) 실행 전에 호출됨
     * - JUnit의 @BeforeEach와 동일한 역할
     * - 테스트 격리성을 보장하기 위해 매번 새로운 인스턴스 생성
     */
    beforeEach {
        userPointTable = UserPointTable()
        pointHistoryTable = PointHistoryTable()
        pointService = PointService(userPointTable, pointHistoryTable)
    }

    /**
     * describe 블록:
     * - 테스트 대상 (메서드나 기능)을 그룹화
     * - 여러 개의 it 블록을 포함할 수 있음
     * - 계층 구조로 테스트를 조직화
     */
    describe("get") {
        /**
         * it 블록:
         * - 실제 테스트 케이스
         * - 자연어로 테스트 시나리오 설명
         * - JUnit의 @Test 메서드와 동일한 역할
         *
         * shouldBe:
         * - Kotest의 중위 함수(infix function) Matcher
         * - assertEquals()보다 읽기 쉽고 자연스러움
         * - a shouldBe b는 "a는 b여야 한다"로 읽힘
         */
        it("새로운 사용자는 0 포인트를 가지고 있다") {
            // When: 신규 사용자 조회
            val result = pointService.get(1L)

            // Then: Kotest 스타일 단언문
            result.id shouldBe 1L       // result.id는 1L이어야 한다
            result.point shouldBe 0L    // result.point는 0L이어야 한다
        }
    }

    /**
     * charge 메서드 테스트 그룹
     *
     * describe 블록으로 관련된 테스트들을 묶음:
     * - 정상 동작 테스트
     * - 예외 상황 테스트 (100 단위 검증, 최대 잔액 검증)
     */
    describe("charge") {
        /**
         * 충전 시 잔액 증가 및 이력 기록 검증
         *
         * shouldHaveSize:
         * - Kotest의 컬렉션 Matcher
         * - assertEquals(2, histories.size)와 동일하지만 더 읽기 쉬움
         */
        it("포인트를 충전하면 잔액이 증가하고 이력이 기록된다") {
            // When: 100 포인트, 200 포인트 순차 충전
            val firstCharge = pointService.charge(1L, 100L)
            val secondCharge = pointService.charge(1L, 200L)

            // Then: 잔액은 300 포인트
            secondCharge.point shouldBe 300L

            // Then: 이력 검증 (Kotest 스타일)
            val histories = pointService.history(1L)
            histories shouldHaveSize 2  // 이력은 2개여야 한다
            histories[0].type shouldBe TransactionType.CHARGE
            histories[0].amount shouldBe 100L
            histories[1].type shouldBe TransactionType.CHARGE
            histories[1].amount shouldBe 200L
            histories[0].timeMillis shouldBe firstCharge.updateMillis
            histories[1].timeMillis shouldBe secondCharge.updateMillis
        }

        /**
         * 100 단위 검증 테스트
         *
         * shouldThrow<T>:
         * - Kotest의 예외 검증 함수
         * - JUnit의 assertThrows보다 간결
         * - 제네릭 타입으로 예외 타입 지정
         */
        it("100 단위가 아닌 금액은 충전할 수 없다") {
            // When & Then: 150 포인트 충전 시도 시 예외 발생
            shouldThrow<IllegalArgumentException> {
                pointService.charge(1L, 150L)
            }
        }

        /**
         * 최대 잔액 초과 방지 테스트
         */
        it("최대 잔액을 초과하는 충전은 불가능하다") {
            // Given: 최대 잔액(1,000,000) 충전
            pointService.charge(1L, 1_000_000L)

            // When & Then: 추가 충전 시도 시 예외 발생
            shouldThrow<IllegalArgumentException> {
                pointService.charge(1L, 100L)
            }
        }
    }

    /**
     * use 메서드 테스트 그룹
     *
     * 포인트 사용에 대한 다양한 시나리오 테스트:
     * - 정상 사용
     * - 잔액 부족 시 예외
     * - 100 단위 검증
     */
    describe("use") {
        /**
         * 정상 사용 테스트
         *
         * last():
         * - Kotlin 컬렉션의 마지막 요소를 가져오는 확장 함수
         * - histories[histories.size - 1]보다 간결
         */
        it("잔액이 충분하면 포인트를 사용할 수 있다") {
            // Given: 400 포인트 충전
            pointService.charge(1L, 400L)

            // When: 100 포인트 사용
            val remaining = pointService.use(1L, 100L)

            // Then: 잔액은 300 포인트
            remaining.point shouldBe 300L

            // Then: 이력은 2개 (충전 1개, 사용 1개)
            val histories = pointService.history(1L)
            histories shouldHaveSize 2
            histories.last().type shouldBe TransactionType.USE  // 마지막 이력은 사용
            histories.last().amount shouldBe 100L
        }

        /**
         * 잔액 부족 예외 테스트
         */
        it("잔액이 부족하면 예외가 발생한다") {
            // Given: 100 포인트 충전
            pointService.charge(1L, 100L)

            // When & Then: 200 포인트 사용 시도 시 예외 발생
            shouldThrow<IllegalArgumentException> {
                pointService.use(1L, 200L)
            }
        }

        /**
         * 100 단위 검증 테스트
         */
        it("100 단위가 아닌 금액은 사용할 수 없다") {
            // Given: 200 포인트 충전
            pointService.charge(1L, 200L)

            // When & Then: 50 포인트 사용 시도 시 예외 발생
            shouldThrow<IllegalArgumentException> {
                pointService.use(1L, 50L)
            }
        }
    }

    /**
     * history 메서드 테스트 그룹
     *
     * 포인트 이력 조회 기능 테스트
     */
    describe("history") {
        /**
         * 사용자별 이력 격리 테스트
         *
         * map { it.type }:
         * - List<PointHistory>를 List<TransactionType>으로 변환
         * - 이력의 타입만 추출하여 검증
         * - Kotest의 shouldBe는 리스트 전체를 비교
         */
        it("포인트 이력은 사용자별로 독립적이다") {
            // Given: 사용자 1은 충전/사용, 사용자 2는 충전만
            pointService.charge(1L, 200L)
            pointService.use(1L, 100L)
            pointService.charge(2L, 300L)

            // When: 각 사용자의 이력 조회
            val user1History = pointService.history(1L)
            val user2History = pointService.history(2L)

            // Then: 사용자 1의 이력은 2개 (충전, 사용)
            user1History shouldHaveSize 2
            user1History.map { it.type } shouldBe listOf(TransactionType.CHARGE, TransactionType.USE)

            // Then: 사용자 2의 이력은 1개 (충전)
            user2History shouldHaveSize 1
            user2History[0].type shouldBe TransactionType.CHARGE
            user2History[0].amount shouldBe 300L
        }
    }
})
