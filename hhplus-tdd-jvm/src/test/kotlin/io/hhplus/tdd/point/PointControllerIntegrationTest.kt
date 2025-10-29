package io.hhplus.tdd.point

import com.fasterxml.jackson.databind.ObjectMapper
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * PointController 통합 테스트
 *
 * 통합 테스트 (Integration Test):
 * - 여러 컴포넌트가 함께 동작하는지 검증
 * - 실제 HTTP 요청/응답을 시뮬레이션
 * - Spring 컨텍스트를 로드하여 실제 환경과 유사하게 테스트
 *
 * @SpringBootTest:
 * - 전체 Spring Application Context를 로드
 * - 모든 Bean을 생성하고 의존성 주입 수행
 * - 실제 서버는 시작하지 않음 (기본값: MOCK)
 *
 * @AutoConfigureMockMvc:
 * - MockMvc를 자동으로 설정
 * - 실제 HTTP 서버 없이 Controller 테스트 가능
 * - MVC 계층(Controller, Filter, Interceptor 등) 통합 테스트
 *
 * @DirtiesContext:
 * - 각 테스트 메서드 후 Spring Context를 다시 로드
 * - classMode = AFTER_EACH_TEST_METHOD: 메서드마다 Context 재생성
 * - 테스트 간 격리성 보장 (메모리 DB 상태 초기화)
 * - 성능 저하가 있지만 테스트 독립성을 보장
 *
 * @Autowired constructor:
 * - Kotlin의 생성자 주입 방식
 * - Spring이 자동으로 MockMvc와 ObjectMapper를 주입
 *
 * @property mockMvc HTTP 요청을 시뮬레이션하는 Mock 객체
 * @property objectMapper JSON 직렬화/역직렬화 도구 (Jackson)
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PointControllerIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
) {

    /**
     * 충전 엔드포인트 통합 테스트
     *
     * MockMvc 사용 패턴:
     * 1. perform(): HTTP 요청 수행
     * 2. andExpect(): 응답 검증
     * 3. andDo(): 추가 동작 (로깅 등, 선택적)
     *
     * patch():
     * - MockMvcRequestBuilders.patch() 정적 메서드
     * - PATCH 요청 빌더 생성
     *
     * contentType():
     * - HTTP Content-Type 헤더 설정
     * - MediaType.APPLICATION_JSON: "application/json"
     *
     * content():
     * - HTTP 요청 본문 (Body) 설정
     * - objectMapper.writeValueAsString(): 객체를 JSON 문자열로 변환
     *
     * status().isOk:
     * - HTTP 200 OK 응답 검증
     *
     * jsonPath():
     * - JSON 응답 내용 검증
     * - JSONPath 표현식 사용 ($.field로 필드 접근)
     * - Hamcrest Matcher 사용 (equalTo())
     */
    @Test
    fun `charge endpoint updates balance`() {
        // When: 100 포인트 충전 요청
        mockMvc.perform(
            patch("/point/{id}/charge", 1L)  // PATCH /point/1/charge
                .contentType(MediaType.APPLICATION_JSON)  // Content-Type: application/json
                .content(objectMapper.writeValueAsString(PointAmountRequest(100L))),  // Body: {"amount":100}
        )
            // Then: 200 OK 응답 및 잔액 100 확인
            .andExpect(status().isOk)  // HTTP 200
            .andExpect(jsonPath("$.id", equalTo(1)))  // response.id == 1
            .andExpect(jsonPath("$.point", equalTo(100)))  // response.point == 100

        // When: 포인트 조회 요청으로 충전 결과 재확인
        mockMvc.perform(get("/point/{id}", 1L))  // GET /point/1
            // Then: 잔액이 유지되고 있음
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.point", equalTo(100)))
    }

    /**
     * 사용 엔드포인트 통합 테스트
     *
     * 시나리오:
     * 1. 300 포인트 충전
     * 2. 100 포인트 사용
     * 3. 잔액 200 포인트 확인
     */
    @Test
    fun `use endpoint deducts balance`() {
        // Given: 300 포인트 충전
        mockMvc.perform(
            patch("/point/{id}/charge", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(PointAmountRequest(300L))),
        ).andExpect(status().isOk)

        // When: 100 포인트 사용
        mockMvc.perform(
            patch("/point/{id}/use", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(PointAmountRequest(100L))),
        )
            // Then: 잔액 200 포인트
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.point", equalTo(200)))
    }

    /**
     * 이력 조회 엔드포인트 통합 테스트
     *
     * 검증 사항:
     * - 이력이 시간순으로 반환됨
     * - 배열 길이 검증 ($.length())
     * - 배열 요소 접근 ($[0], $[1])
     * - Enum 값은 .name으로 문자열 변환됨
     */
    @Test
    fun `history endpoint returns chronological operations`() {
        // Given: 200 포인트 충전
        mockMvc.perform(
            patch("/point/{id}/charge", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(PointAmountRequest(200L))),
        ).andExpect(status().isOk)

        // Given: 100 포인트 사용
        mockMvc.perform(
            patch("/point/{id}/use", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(PointAmountRequest(100L))),
        ).andExpect(status().isOk)

        // When: 이력 조회
        mockMvc.perform(get("/point/{id}/histories", 1L))
            // Then: 2개의 이력 (충전, 사용) 시간순으로 반환
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()", equalTo(2)))  // 배열 길이
            .andExpect(jsonPath("$[0].type", equalTo(TransactionType.CHARGE.name)))  // 첫 번째 이력
            .andExpect(jsonPath("$[0].amount", equalTo(200)))
            .andExpect(jsonPath("$[1].type", equalTo(TransactionType.USE.name)))  // 두 번째 이력
            .andExpect(jsonPath("$[1].amount", equalTo(100)))
    }

    /**
     * 유효하지 않은 금액 충전 거부 테스트
     *
     * status().isBadRequest:
     * - HTTP 400 Bad Request 검증
     * - ApiControllerAdvice에서 처리된 에러 응답
     */
    @Test
    fun `charge rejects invalid amount`() {
        // When: 150 포인트 충전 시도 (100 단위 아님)
        mockMvc.perform(
            patch("/point/{id}/charge", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(PointAmountRequest(150L))),
        )
            // Then: 400 Bad Request 응답
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code", equalTo("400")))
    }

    /**
     * 잔액 부족 시 사용 거부 테스트
     */
    @Test
    fun `use rejects insufficient balance`() {
        // Given: 200 포인트 충전
        mockMvc.perform(
            patch("/point/{id}/charge", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(PointAmountRequest(200L))),
        ).andExpect(status().isOk)

        // When: 300 포인트 사용 시도 (잔액 부족)
        mockMvc.perform(
            patch("/point/{id}/use", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(PointAmountRequest(300L))),
        )
            // Then: 400 Bad Request 응답
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code", equalTo("400")))
    }

    /**
     * 최대 잔액 초과 충전 거부 테스트
     */
    @Test
    fun `charge rejects when exceeding max balance`() {
        // Given: 최대 잔액(1,000,000) 충전
        mockMvc.perform(
            patch("/point/{id}/charge", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(PointAmountRequest(1_000_000L))),
        ).andExpect(status().isOk)

        // When: 추가 충전 시도 (최대 잔액 초과)
        mockMvc.perform(
            patch("/point/{id}/charge", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(PointAmountRequest(100L))),
        )
            // Then: 400 Bad Request 응답
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code", equalTo("400")))
    }
}
