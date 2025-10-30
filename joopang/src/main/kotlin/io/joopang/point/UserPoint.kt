package io.joopang.point

/**
 * 사용자 포인트 엔티티 (도메인 모델)
 *
 * data class:
 * - Kotlin의 특별한 클래스로, 데이터를 보관하는 용도로 사용됩니다.
 * - 자동으로 equals(), hashCode(), toString(), copy() 메서드를 생성합니다.
 * - 불변(immutable) 데이터 구조로 설계하는 것이 좋습니다 (val 사용).
 *
 * 불변성의 장점:
 * - 스레드 안전성: 여러 스레드에서 동시에 접근해도 안전합니다.
 * - 예측 가능성: 객체가 생성된 후 상태가 변하지 않아 디버깅이 쉽습니다.
 * - 함수형 프로그래밍: 부수 효과가 없는 순수 함수 작성이 가능합니다.
 *
 * 도메인 모델의 책임:
 * - 비즈니스 규칙 검증 (포인트 단위, 최대 잔액 등)
 * - 도메인 로직 캡슐화 (충전, 사용 시 새로운 상태 생성)
 *
 * @property id 사용자 ID (기본키)
 * @property point 현재 포인트 잔액
 * @property updateMillis 마지막 업데이트 시간 (밀리초 단위 Unix timestamp)
 */
data class UserPoint(
    val id: Long,
    val point: Long,
    val updateMillis: Long,
) {
    /**
     * 동반 객체: 상수와 팩토리 메서드 정의
     *
     * 도메인 규칙을 도메인 모델 안에 정의하면:
     * - 응집도 향상: 관련 로직이 한 곳에 모임
     * - 재사용성: 다른 곳에서도 검증 가능
     * - 유지보수성: 규칙 변경 시 한 곳만 수정
     */
    companion object {
        /** 포인트 최소 단위 (100 포인트) */
        private const val POINT_UNIT = 100L

        /** 최대 포인트 잔액 (1,000,000 포인트) */
        private const val MAX_BALANCE = 1_000_000L

        /**
         * 포인트 금액 유효성 검증
         *
         * 검증 규칙:
         * 1. 금액은 양수여야 함
         * 2. 금액은 POINT_UNIT(100)의 배수여야 함
         *
         * @param amount 검증할 금액
         * @throws IllegalArgumentException 유효하지 않은 금액
         */
        fun validateAmount(amount: Long) {
            require(amount > 0) {
                "Point amount must be positive, but was $amount"
            }
            require(amount % POINT_UNIT == 0L) {
                "Point amount must be in increments of $POINT_UNIT, but was $amount"
            }
        }
    }

    /**
     * 포인트 충전 (새로운 UserPoint 반환)
     *
     * 불변 객체 패턴:
     * - 현재 객체를 수정하지 않고 새로운 객체를 생성
     * - copy(): data class가 자동 생성한 메서드
     *
     * 비즈니스 규칙:
     * - 충전 금액은 validateAmount()로 검증됨
     * - 최대 잔액 초과 불가
     *
     * @param amount 충전 금액 (양수, 100 단위)
     * @return 충전 후 새로운 UserPoint 인스턴스
     * @throws IllegalArgumentException 금액이 유효하지 않거나 최대 잔액 초과
     */
    fun charge(amount: Long): UserPoint {
        validateAmount(amount)

        val newPoint = this.point + amount
        require(newPoint <= MAX_BALANCE) {
            "Point balance cannot exceed $MAX_BALANCE (current: ${this.point}, charge: $amount)"
        }

        return copy(
            point = newPoint,
            updateMillis = System.currentTimeMillis()
        )
    }

    /**
     * 포인트 사용 (새로운 UserPoint 반환)
     *
     * 비즈니스 규칙:
     * - 사용 금액은 validateAmount()로 검증됨
     * - 잔액 부족 불가
     *
     * @param amount 사용 금액 (양수, 100 단위)
     * @return 사용 후 새로운 UserPoint 인스턴스
     * @throws IllegalArgumentException 금액이 유효하지 않거나 잔액 부족
     */
    fun use(amount: Long): UserPoint {
        validateAmount(amount)

        val newPoint = this.point - amount
        require(newPoint >= 0) {
            "Insufficient point balance (current: ${this.point}, use: $amount)"
        }

        return copy(
            point = newPoint,
            updateMillis = System.currentTimeMillis()
        )
    }

    /**
     * 잔액이 충분한지 확인
     *
     * @param amount 확인할 금액
     * @return 잔액이 충분하면 true
     */
    fun hasSufficientBalance(amount: Long): Boolean {
        return this.point >= amount
    }

    /**
     * 최대 잔액까지 충전 가능한 금액
     *
     * @return 충전 가능한 최대 금액
     */
    fun availableChargeAmount(): Long {
        return MAX_BALANCE - this.point
    }
}
