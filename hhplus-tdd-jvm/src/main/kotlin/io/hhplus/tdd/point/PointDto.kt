package io.hhplus.tdd.point

import jakarta.validation.constraints.Positive

/**
 * 포인트 금액 요청 DTO (Data Transfer Object)
 *
 * HTTP 요청 본문(Body)에서 포인트 금액을 받기 위한 데이터 클래스입니다.
 * 클라이언트가 JSON으로 전송한 데이터를 이 객체로 변환합니다.
 *
 * 검증 어노테이션:
 * @field:Positive
 * - Jakarta Bean Validation (JSR 380) 표준 사용
 * - 필드 값이 양수(> 0)인지 자동 검증
 * - 검증 실패 시 MethodArgumentNotValidException 발생 (Spring이 400 응답으로 변환)
 *
 * @field: 접두사:
 * - Kotlin에서는 프로퍼티에 대해 여러 타겟 (필드, getter, 파라미터)이 있습니다.
 * - @field를 명시하여 컴파일된 필드에 어노테이션을 적용합니다.
 *
 * 사용 예시:
 * POST /point/1/charge
 * { "amount": 100 }
 *
 * @property amount 포인트 금액 (양수만 허용)
 */
data class PointAmountRequest(
    @field:Positive(message = "Amount must be positive.")
    val amount: Long,
)

/**
 * 사용자 포인트 응답 DTO
 *
 * HTTP 응답으로 사용자 포인트 정보를 전달하기 위한 데이터 클래스입니다.
 * 도메인 모델(UserPoint)을 직접 노출하지 않고 DTO로 변환하여 응답합니다.
 *
 * DTO 패턴의 장점:
 * - 계층 간 결합도 감소: API 스펙과 도메인 모델을 독립적으로 변경 가능
 * - 보안: 민감한 정보를 숨기고 필요한 정보만 노출
 * - 유연성: 여러 도메인 객체를 조합하거나 계산된 값 추가 가능
 *
 * @property id 사용자 ID
 * @property point 현재 포인트 잔액
 * @property updateMillis 마지막 업데이트 시간 (밀리초 단위)
 */
data class UserPointResponse(
    val id: Long,
    val point: Long,
    val updateMillis: Long,
) {
    /**
     * 동반 객체 (Companion Object)
     *
     * Kotlin의 companion object는 Java의 static과 유사합니다.
     * 클래스 인스턴스 없이 호출 가능한 팩토리 메서드를 정의합니다.
     *
     * 사용 예시:
     * val response = UserPointResponse.from(userPoint)
     */
    companion object {
        /**
         * 팩토리 메서드: 도메인 모델을 DTO로 변환
         *
         * 정적 팩토리 메서드 패턴:
         * - 생성자보다 의미있는 이름을 가질 수 있음 (from, of, create 등)
         * - 변환 로직을 한 곳에서 관리
         *
         * @param userPoint 도메인 모델
         * @return 변환된 DTO
         */
        fun from(userPoint: UserPoint): UserPointResponse {
            return UserPointResponse(
                id = userPoint.id,
                point = userPoint.point,
                updateMillis = userPoint.updateMillis,
            )
        }
    }
}

/**
 * 포인트 이력 응답 DTO
 *
 * HTTP 응답으로 포인트 이력 정보를 전달하기 위한 데이터 클래스입니다.
 * 도메인 모델(PointHistory)을 DTO로 변환하여 API 응답으로 사용합니다.
 *
 * @property id 이력 ID
 * @property userId 사용자 ID
 * @property type 트랜잭션 타입 (CHARGE/USE)
 * @property amount 포인트 금액
 * @property timeMillis 트랜잭션 발생 시간 (밀리초 단위)
 */
data class PointHistoryResponse(
    val id: Long,
    val userId: Long,
    val type: TransactionType,
    val amount: Long,
    val timeMillis: Long,
) {
    /**
     * 동반 객체: 도메인 모델을 DTO로 변환하는 팩토리 메서드 제공
     */
    companion object {
        /**
         * 팩토리 메서드: 도메인 모델을 DTO로 변환
         *
         * @param history 도메인 모델
         * @return 변환된 DTO
         */
        fun from(history: PointHistory): PointHistoryResponse {
            return PointHistoryResponse(
                id = history.id,
                userId = history.userId,
                type = history.type,
                amount = history.amount,
                timeMillis = history.timeMillis,
            )
        }
    }
}
