/**
 * ===================================================================
 * Gradle 빌드 스크립트 (Kotlin DSL)
 *
 * 이 파일은 프로젝트의 빌드 구성을 정의합니다.
 * - 플러그인 적용
 * - 의존성 관리
 * - 컴파일 옵션
 * - 테스트 설정
 * - 패키징 설정
 * ===================================================================
 */

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * --- 플러그인 설정 ---
 *
 * 프로젝트에서 사용할 Gradle 플러그인들을 선언합니다.
 */
plugins {
    alias(libs.plugins.kotlin.jvm)                      // Kotlin JVM 지원 (Kotlin 코드 컴파일)
    alias(libs.plugins.kotlin.spring)                   // Kotlin-Spring 통합 (Spring 어노테이션 처리)
    alias(libs.plugins.spring.boot)                     // Spring Boot 애플리케이션 빌드
    alias(libs.plugins.spring.dependency.management)    // Spring Boot 의존성 버전 자동 관리
    id("jacoco")                                        // 코드 커버리지 측정 도구
}

/**
 * --- 프로젝트 메타데이터 ---
 *
 * 모든 프로젝트에 공통으로 적용되는 그룹 ID를 설정합니다.
 */
allprojects {
    group = property("app.group").toString()  // gradle.properties에서 정의한 app.group 사용
}

/**
 * --- 의존성 관리 설정 ---
 *
 * Spring Cloud BOM(Bill of Materials)을 임포트하여
 * 호환되는 버전의 라이브러리들을 자동으로 관리합니다.
 */
dependencyManagement {
    imports {
        mavenBom(libs.spring.cloud.dependencies.get().toString())
    }
}

/**
 * --- 프로젝트 의존성 ---
 *
 * 프로젝트에서 사용할 라이브러리들을 선언합니다.
 */
dependencies {
    // Kotlin 핵심 라이브러리
    implementation("org.jetbrains.kotlin:kotlin-reflect")        // Kotlin 리플렉션 API (런타임 타입 검사)
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")    // Kotlin 표준 라이브러리 (JDK 8 버전)

    // Spring Boot 라이브러리
    implementation(libs.spring.boot.starter.web)                 // Spring Web MVC (REST API 개발)
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")  // JSON 직렬화/역직렬화 (Kotlin 지원)

    // 어노테이션 프로세서
    annotationProcessor(libs.spring.boot.configuration.processor) // application.properties 자동완성 지원

    // 테스트 라이브러리
    testImplementation(libs.spring.boot.starter.test)            // Spring Boot 테스트 지원 (JUnit, Mockito 등)

    // Kotest - Kotlin 친화적인 테스트 프레임워크
    testImplementation("io.kotest:kotest-runner-junit5:5.8.0")   // Kotest JUnit5 실행기
    testImplementation("io.kotest:kotest-assertions-core:5.8.0") // Kotest 단언문 (shouldBe, shouldThrow 등)
    testImplementation("io.kotest:kotest-property:5.8.0")        // 속성 기반 테스팅 (Property-based testing)
}

/**
 * --- Java 컴파일 설정 ---
 *
 * 소스 코드 및 타겟 JVM 버전을 지정합니다.
 */
java {
    sourceCompatibility = JavaVersion.VERSION_17  // Java 17 문법 사용 가능
}

/**
 * --- Jacoco 코드 커버리지 설정 ---
 *
 * 테스트 커버리지 리포트 생성 도구의 버전을 지정합니다.
 */
with(extensions.getByType(JacocoPluginExtension::class.java)) {
    toolVersion = "0.8.7"
}

/**
 * --- Kotlin 컴파일 옵션 ---
 *
 * Kotlin 컴파일러의 동작 방식을 설정합니다.
 */
tasks.withType<KotlinCompile> {
    kotlinOptions {
        // JSR 305 어노테이션 지원 (Spring의 null-safety 어노테이션 인식)
        // @Nullable, @NonNull 등을 Kotlin의 null 안전성 시스템과 통합
        freeCompilerArgs = listOf("-Xjsr305=strict")

        // 컴파일 타겟을 JVM 17로 설정
        jvmTarget = JavaVersion.VERSION_17.toString()
    }
}

/**
 * --- 패키징 설정 ---
 *
 * 실행 가능한 JAR 파일 생성 방식을 제어합니다.
 */
// bootJar: 모든 의존성을 포함한 실행 가능한 "Fat JAR" 생성 (활성화)
tasks.getByName("bootJar") {
    enabled = true
}

// jar: 의존성을 포함하지 않는 일반 JAR 생성 (비활성화)
// bootJar가 실행 가능한 JAR를 생성하므로 일반 jar는 불필요
tasks.getByName("jar") {
    enabled = false
}

/**
 * --- 테스트 설정 ---
 *
 * 테스트 실행 방식을 제어합니다.
 */
tasks.test {
    ignoreFailures = true    // 테스트 실패 시에도 빌드를 중단하지 않음 (CI/CD에서 유용)
    useJUnitPlatform()       // JUnit 5 플랫폼 사용 (Kotest도 JUnit 5 위에서 실행됨)
}
