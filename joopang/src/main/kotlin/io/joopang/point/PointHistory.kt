package io.joopang.point

/**
 * 포인트 이력 엔티티 (도메인 모델)
 *
 * 사용자의 포인트 충전/사용 내역을 기록하는 불변 객체입니다.
 * 모든 포인트 변경 사항은 이력으로 남아 추적 가능합니다 (Audit Trail).
 *
 * 이력 관리의 중요성:
 * - 감사(Audit): 언제, 얼마나 포인트가 변경되었는지 추적
 * - 디버깅: 잔액 불일치 문제 발생 시 원인 파악
 * - 통계: 사용자별 포인트 사용 패턴 분석
 *
 * @property id 이력 ID (기본키, 자동 증가)
 * @property userId 사용자 ID (외래키)
 * @property type 트랜잭션 타입 (충전/사용)
 * @property amount 포인트 변경 금액 (항상 양수)
 * @property timeMillis 트랜잭션 발생 시간 (밀리초 단위 Unix timestamp)
 */
data class PointHistory(
    val id: Long,
    val userId: Long,
    val type: TransactionType,
    val amount: Long,
    val timeMillis: Long,
)

/**
 * 포인트 트랜잭션 종류
 *
 * enum class:
 * - Kotlin의 열거형 클래스로, 제한된 값의 집합을 표현합니다.
 * - 타입 안전성을 제공하여 잘못된 값 사용을 컴파일 타임에 방지합니다.
 * - 문자열 상수보다 안전하고 가독성이 좋습니다.
 *
 * 사용 예시:
 * - if (history.type == TransactionType.CHARGE) { ... }
 * - when (type) { CHARGE -> "충전", USE -> "사용" }
 *
 * @property CHARGE 포인트 충전 (잔액 증가)
 * @property USE 포인트 사용 (잔액 감소)
 */
enum class TransactionType {
    CHARGE, USE
}