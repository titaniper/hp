package io.joopang.point

import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 포인트 REST API 컨트롤러
 *
 * @RestController 어노테이션:
 * - @Controller + @ResponseBody의 조합
 * - 모든 메서드의 반환값을 HTTP 응답 본문(Body)으로 직렬화
 * - Spring MVC가 자동으로 JSON으로 변환 (Jackson 라이브러리 사용)
 *
 * @RequestMapping("/point"):
 * - 이 컨트롤러의 모든 엔드포인트는 /point로 시작
 * - 클래스 레벨에서 공통 경로를 정의하여 중복 제거
 *
 * 생성자 주입 (Constructor Injection):
 * - Kotlin의 주 생성자에서 의존성 주입
 * - Spring이 자동으로 PointService 빈을 주입
 * - 필드 주입이나 세터 주입보다 권장됨 (불변성, 테스트 용이성)
 *
 * @property pointService 포인트 비즈니스 로직을 처리하는 서비스
 */
@RestController
@RequestMapping("/point")
class PointController(
    private val pointService: PointService,
) {

    /**
     * 사용자 포인트 조회 API
     *
     * @GetMapping("{id}"):
     * - HTTP GET 요청을 처리
     * - 경로: GET /point/{id}
     * - {id}는 경로 변수로, @PathVariable로 바인딩됨
     *
     * @PathVariable 어노테이션:
     * - URL 경로의 변수를 메서드 파라미터로 바인딩
     * - 예: GET /point/123 -> id = 123L
     *
     * 반환값:
     * - UserPointResponse 객체를 JSON으로 자동 변환
     * - 예: {"id": 123, "point": 500, "updateMillis": 1234567890}
     *
     * @param id 조회할 사용자 ID
     * @return 사용자 포인트 정보
     */
    @GetMapping("{id}")
    fun point(
        @PathVariable id: Long,
    ): UserPointResponse {
        return UserPointResponse.from(pointService.get(id))
    }

    /**
     * 사용자 포인트 이력 조회 API
     *
     * 엔드포인트: GET /point/{id}/histories
     *
     * map() 함수:
     * - Kotlin 컬렉션 함수로, 각 요소를 변환
     * - List<PointHistory>를 List<PointHistoryResponse>로 변환
     *
     * 메서드 레퍼런스 (::):
     * - PointHistoryResponse::from은 함수 참조
     * - .map { PointHistoryResponse.from(it) }과 동일하지만 더 간결
     *
     * @param id 조회할 사용자 ID
     * @return 사용자의 모든 포인트 이력 리스트
     */
    @GetMapping("{id}/histories")
    fun history(
        @PathVariable id: Long,
    ): List<PointHistoryResponse> {
        return pointService.history(id).map(PointHistoryResponse::from)
    }

    /**
     * 포인트 충전 API
     *
     * @PatchMapping:
     * - HTTP PATCH 요청을 처리 (리소스의 부분 수정)
     * - RESTful 설계: PATCH는 기존 리소스의 일부를 업데이트
     * - 경로: PATCH /point/{id}/charge
     *
     * @Valid 어노테이션:
     * - 요청 객체의 유효성 검증 활성화
     * - PointAmountRequest의 @Positive 검증 실행
     * - 검증 실패 시 MethodArgumentNotValidException 발생
     *
     * @RequestBody 어노테이션:
     * - HTTP 요청 본문(Body)을 객체로 역직렬화
     * - JSON -> PointAmountRequest 객체로 자동 변환
     *
     * 요청 예시:
     * PATCH /point/1/charge
     * Content-Type: application/json
     * {"amount": 100}
     *
     * @param id 충전할 사용자 ID
     * @param request 충전 금액 정보
     * @return 충전 후 사용자 포인트 정보
     */
    @PatchMapping("{id}/charge")
    fun charge(
        @PathVariable id: Long,
        @Valid @RequestBody request: PointAmountRequest,
    ): UserPointResponse {
        return UserPointResponse.from(pointService.charge(id, request.amount))
    }

    /**
     * 포인트 사용 API
     *
     * 엔드포인트: PATCH /point/{id}/use
     *
     * charge()와 유사하지만 포인트를 차감합니다.
     *
     * 요청 예시:
     * PATCH /point/1/use
     * Content-Type: application/json
     * {"amount": 100}
     *
     * 에러 처리:
     * - 잔액 부족 시 IllegalArgumentException 발생
     * - ApiControllerAdvice에서 400 Bad Request로 변환
     *
     * @param id 사용할 사용자 ID
     * @param request 사용 금액 정보
     * @return 사용 후 사용자 포인트 정보
     */
    @PatchMapping("{id}/use")
    fun use(
        @PathVariable id: Long,
        @Valid @RequestBody request: PointAmountRequest,
    ): UserPointResponse {
        return UserPointResponse.from(pointService.use(id, request.amount))
    }
}
