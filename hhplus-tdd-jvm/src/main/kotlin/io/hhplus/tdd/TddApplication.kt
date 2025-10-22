package io.hhplus.tdd

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Spring Boot 애플리케이션의 메인 클래스
 *
 * @SpringBootApplication 어노테이션은 다음 세 가지를 포함합니다:
 * - @Configuration: 이 클래스가 Spring 설정 클래스임을 나타냄
 * - @EnableAutoConfiguration: Spring Boot의 자동 설정 기능 활성화
 * - @ComponentScan: 이 패키지와 하위 패키지에서 Spring 컴포넌트를 자동 검색
 */
@SpringBootApplication
class TddApplication

/**
 * 애플리케이션 진입점 (Entry Point)
 *
 * JVM이 실행할 때 가장 먼저 호출되는 함수입니다.
 * Spring Boot 애플리케이션을 시작하고 내장 웹 서버(Tomcat)를 실행합니다.
 *
 * @param args 명령줄 인자 (예: --server.port=9090)
 */
fun main(args: Array<String>) {
    runApplication<TddApplication>(*args)
}
