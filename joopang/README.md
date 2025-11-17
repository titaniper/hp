# Joopang Service

## Overview
- 실시간 재고 확인, 주문/결제, 선착순 쿠폰 발급을 다루는 학습용 이커머스 백엔드입니다.
- Kotlin + Spring Boot 기반의 모놀리식 애플리케이션으로, 도메인별 헥사고날 구조(`presentation → application → domain → infrastructure`)를 따릅니다.
- 각 도메인은 인메모리 어댑터를 제공하며, 요구사항 확장 시 외부 시스템으로 대체하도록 설계되었습니다.
- ./gradlew test

## Tech Stack
- Kotlin 1.9, JDK 17
- Spring Boot 3.2 (Web MVC, Validation, Springdoc OpenAPI)
- Gradle Kotlin DSL + Version Catalog
- Kotest, Spring Boot Test (준비된 의존성)

## Architecture Highlights
- `src/main/kotlin/io/joopang/services/<domain>` 구조로 도메인을 구분합니다.
- 공통 관심사는 `services/common` 아래에 위치하며, 전역 예외 처리(`presentation/ApiControllerAdvice`)와 표준 에러 포맷(`application/ErrorResponse`)을 제공합니다.
- 세부 아키텍처 정리는 [docs/architecture.md](docs/architecture.md)에 정리돼 있습니다.

## Domain Modules
- `services/order` : 주문 생성/결제, 외부 전송 페이로드 생성, 재고/쿠폰 연동.
- `services/product` : 상품 및 상품 옵션 관리, 재고 확인, 인기 상품 조회.
- `services/coupon` : 선착순 쿠폰 발급/조회, 락 매커니즘(`CouponLockManager`).
- `services/user`, `services/payment`, `services/category`, `services/seller`, `services/cart`, `services/delivery`, `services/metrics` : 주문/상품 도메인을 보조하는 엔티티 및 값 객체.
- 인메모리 구현체(`infrastructure`)는 예시용으로 제공되며, 실제 연동에 맞춰 확장 가능합니다.

## Getting Started
### Prerequisites
- JDK 17
- (선택) 로컬에서 Gradle 설치가 필요하지 않습니다. 래퍼(`./gradlew`)를 사용하세요.

### Build & Run
```bash
./gradlew bootRun
```

### Tests
```bash
./gradlew test
```
> `build.gradle.kts`에서 테스트 실패가 무시되도록(`ignoreFailures = true`) 설정돼 있으니, 실제 품질 검증 시 값을 조정하세요.

### Test Profile (H2)
- `./gradlew test` 실행 시 자동으로 `test` 프로파일이 활성화되고, 인메모리 H2(`jdbc:h2:mem:joopang-test`)를 사용합니다.
- 로컬 애플리케이션은 기본값(로컬 MySQL)로 계속 실행되며, 다른 DB를 쓰고 싶다면 `SPRING_PROFILES_ACTIVE` 값을 직접 지정하세요.

## API Documentation
- Springdoc OpenAPI UI: `http://localhost:8080/swagger-ui/index.html` (기본 부트 실행 기준)
- OpenAPI/도메인 명세, ERD 등은 `docs/design` 디렉터리에 정리돼 있습니다.

## Additional Documentation
- [docs/architecture.md](docs/architecture.md) : 전체 아키텍처 요약 및 계층 구조.
- [docs/design/requirements.md](docs/design/requirements.md) : 요구사항 정리.
- [docs/design/api-specification.md](docs/design/api-specification.md) : API 설계 초안.
- [docs/design/erd.md](docs/design/erd.md) : 도메인 모델 ERD.
- [docs/design/tasks.md](docs/design/tasks.md), [docs/design/user-stories.md](docs/design/user-stories.md) : 학습/개발 계획 자료.

## Project Conventions
- Kotlin 파일은 도메인 중심 패키지 구조를 따릅니다 (`io.joopang.services.*`).
- 공통 DTO, 에러 코드, 유틸 등은 `services/common` 아래에서 공유합니다.
- 새로운 인프라 어댑터 추가 시 `infrastructure` 계층에 구현하고, `application` 계층에서 인터페이스로 주입받는 패턴을 유지하세요.
