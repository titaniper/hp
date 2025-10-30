/**
 * Gradle 설정 파일
 *
 * 이 파일은 Gradle 빌드 시스템의 기본 설정을 정의합니다.
 * 프로젝트의 플러그인 및 의존성 저장소를 중앙에서 관리합니다.
 */

/**
 * 플러그인 관리 설정
 *
 * Gradle 플러그인을 어디서 다운로드할지 정의합니다.
 */
pluginManagement {
    repositories {
        mavenCentral()         // Maven Central 저장소 (대부분의 오픈소스 라이브러리가 호스팅됨)
        gradlePluginPortal()   // Gradle 공식 플러그인 포털
    }
}

/**
 * 의존성 해결 관리 설정
 *
 * 프로젝트의 모든 의존성을 어디서 다운로드할지 정의합니다.
 */
dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")  // 아직 안정화되지 않은 Gradle API 사용 경고 억제
    repositories {
        mavenCentral()  // 의존성 라이브러리를 Maven Central에서 다운로드
    }
}

/**
 * 프로젝트 이름 설정
 *
 * 이 값은 빌드 아티팩트(JAR, WAR 등)의 이름으로 사용됩니다.
 */
rootProject.name = "joopang"
