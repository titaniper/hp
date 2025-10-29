package io.hhplus.tdd

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

/**
 * API 에러 응답 데이터 클래스
 *
 * 클라이언트에게 전송할 에러 정보를 담는 DTO입니다.
 *
 * @property code 에러 코드 (주로 HTTP 상태 코드를 문자열로 사용)
 * @property message 에러 메시지 (사용자가 읽을 수 있는 설명)
 */
data class ErrorResponse(val code: String, val message: String)

/**
 * 전역 예외 처리 클래스
 *
 * @RestControllerAdvice 어노테이션:
 * - 모든 @RestController에서 발생하는 예외를 중앙에서 처리합니다.
 * - AOP(Aspect-Oriented Programming) 방식으로 동작합니다.
 * - 각 Controller에서 try-catch를 반복하지 않아도 됩니다.
 *
 * ResponseEntityExceptionHandler:
 * - Spring이 제공하는 기본 예외 처리 클래스
 * - Spring MVC의 표준 예외들을 자동으로 처리해줍니다.
 */
@RestControllerAdvice
class ApiControllerAdvice : ResponseEntityExceptionHandler() {
    /**
     * 로거 인스턴스
     *
     * SLF4J(Simple Logging Facade for Java)를 사용하여 로깅합니다.
     * javaClass는 현재 클래스(ApiControllerAdvice)를 의미합니다.
     */
    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    /**
     * IllegalArgumentException 예외 처리 핸들러
     *
     * @ExceptionHandler 어노테이션:
     * - 특정 타입의 예외가 발생했을 때 이 메서드가 호출됩니다.
     * - 비즈니스 로직에서 잘못된 입력값을 받았을 때 발생하는 예외를 처리합니다.
     *
     * 사용 예시:
     * - 포인트 충전 금액이 100 단위가 아닐 때
     * - 잔액이 부족할 때
     *
     * @param e 발생한 IllegalArgumentException 예외 객체
     * @return 400 Bad Request 응답과 에러 정보
     */
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(e: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        logger.warn("Bad request: ${e.message}")  // 경고 레벨 로그 기록
        return ResponseEntity(
            ErrorResponse("400", e.message ?: "잘못된 요청입니다."),
            HttpStatus.BAD_REQUEST,  // HTTP 400 상태 코드
        )
    }

    /**
     * 모든 예외의 최종 처리 핸들러
     *
     * @ExceptionHandler(Exception::class):
     * - 다른 핸들러에서 처리되지 않은 모든 예외를 처리합니다.
     * - 예상하지 못한 서버 에러를 안전하게 처리합니다.
     *
     * 주의사항:
     * - 실제 운영 환경에서는 구체적인 에러 메시지를 노출하지 않는 것이 보안상 좋습니다.
     * - 로그에는 상세 정보를 기록하되, 클라이언트에는 일반적인 메시지만 전달합니다.
     *
     * @param e 발생한 Exception 예외 객체
     * @return 500 Internal Server Error 응답과 에러 정보
     */
    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<ErrorResponse> {
        logger.error("Unexpected error", e)  // 에러 레벨 로그 기록 (스택 트레이스 포함)
        return ResponseEntity(
            ErrorResponse("500", "에러가 발생했습니다."),
            HttpStatus.INTERNAL_SERVER_ERROR,  // HTTP 500 상태 코드
        )
    }
}
