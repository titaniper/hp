# Step 7-8 코드 리뷰

## 1. 레이어드 아키텍처 & 트랜잭션 경계
- `OrderService`와 `ProductService`처럼 서비스 레이어에 `@Transactional(readOnly = true)`를 기본 적용하고 쓰기 메서드에만 별도 트랜잭션을 거는 패턴은 명확하고 KISS 원칙에도 부합한다. 다만 레포지토리들이 모두 수동으로 `EntityManager`를 다루고 있기 때문에, 서비스마다 트랜잭션을 직접 관리하지 않으면 데이터 일관성이 깨질 수 있다. 예를 들어 `ProductRepository.consumeStock`(src/main/kotlin/io/joopang/services/product/infrastructure/ProductRepository.kt:147-165)은 native update를 실행하지만 호출부(`OrderService.reserveStock`, src/main/kotlin/io/joopang/services/order/application/OrderService.kt:203-247)가 반환 값을 활용해 보상 로직을 두지 않아 실패 지점을 추적하기 어렵다. 트랜잭션 경계를 명시적으로 서비스에서만 열도록 정리한 것은 좋지만, 레포지토리 레벨에서 `@Transactional`을 제거한 이후에는 모든 쓰기 메서드가 서비스 트랜잭션 안에서만 호출된다는 계약을 문서화하거나 인터페이스로 강제하는 편이 안전하다.
- `ProductLockManagerImpl`와 `CouponLockManagerImpl`은 JVM 내부 `ReentrantLock`을 사용한다 (src/main/kotlin/io/joopang/services/product/infrastructure/ProductLockManagerImpl.kt:10-32, src/main/kotlin/io/joopang/services/coupon/infrastructure/CouponLockManagerImpl.kt:10-32). 단일 프로세스에서는 동작하지만, 다중 인스턴스 배포 시에는 전혀 보호되지 않는다. Redis나 DB 기반 락으로 확장하거나, 최소한 주석/문서로 “싱글 인스턴스 전제”를 명시하는 것이 좋다.

## 2. 서비스 로직과 도메인 책임
- `OrderService.reserveStock`(src/main/kotlin/io/joopang/services/order/application/OrderService.kt:203-251)이 재고 조회→도메인 검증→DB 차감까지 모두 담당하고 있어 SRP 및 KISS 관점에서 복잡하다. 재고 차감 규칙을 도메인 서비스/도메인 이벤트로 분리하면 테스트와 재사용성이 올라간다. 또한 `Quantity`와 `StockQuantity`를 혼용해 수치 계산하는 부분이 많아, 메서드 분리나 Value Object 간 변환 유틸을 추가하면 가독성이 좋아질 것 같다.
- `OrderService.processPayment`에서 주문/회원/쿠폰을 모두 잠그고 상태 전이를 직접 수행한다(src/main/kotlin/io/joopang/services/order/application/OrderService.kt:125-200). 도메인 이벤트나 전략 패턴을 이용하면 결제 파이프라인을 확장하기 쉬운데, 현재는 서비스가 여러 책임을 지고 있어 OCP 관점에서는 확장 비용이 크다.

## 3. 레포지토리 패턴 & 퍼시스턴스
- 레포지토리들이 모두 `EntityManager` 기반 수동 구현이라 메서드가 `open` 상태로 남아 있다 (예: src/main/kotlin/io/joopang/services/product/infrastructure/ProductRepository.kt:24-110, src/main/kotlin/io/joopang/services/order/infrastructure/OrderRepository.kt:18-70). Kotlin에서는 기본이 `final`이므로 필요할 때만 `open`을 사용해야 한다. 현재 테스트나 프록시에서 상속하지 않는다면 `open` 키워드를 제거해 불필요한 확장을 막을 수 있다.
- `consumeStock`, `incrementIssuedQuantity`처럼 native query를 직접 작성하는 구간은 명확히 격리해 두었지만 예외 처리가 단순 `Boolean` 반환에 그친다. 실패 시 어떤 파라미터로 실패했는지 로그/예외를 남겨야 문제를 추적하기 쉽다. 또한, 메서드 시그니처에 `@Modifying` + Spring Data Repository를 활용하면 트랜잭션 및 플러시 전략을 일관되게 유지할 수 있다.

## 4. 클린 코드, SOLID, KISS
- 엔티티마다 `copy()`를 수동으로 구현하고 `id == 0`일 때만 검증을 건너뛰도록 한 패턴은 JPA 본연의 “프록시 + no-arg 생성자” 요구사항을 만족시키는 좋은 접근이다. 다만 `Product`, `OrderItem`, `Category` 등 여러 곳에서 동일한 패턴이 반복되므로, 공통 베이스 엔티티나 팩토리 함수를 두면 중복을 줄일 수 있다.
- 예외 클래스를 풍부하게 정의한 것은 도메인 이해도를 높여 준다(`OrderOwnershipException`, `OrderPaymentNotAllowedException`, `ProductItemInactiveException` 등). 반면 예외 메시지를 한 곳에서 관리하지 않아 국제화(다국어)나 로깅 시 중복 가능성이 있다. `ErrorCode` enum 등을 도입해 일관된 에러 응답을 제공하는 방법을 고려해 볼 수 있다.

## 5. 테스트 전략
- 모든 통합 테스트가 `@DirtiesContext`와 Testcontainers(MySQL)를 사용하고 있어 실행 시간이 길다 (`CartServiceTest`, `CouponServiceIntegrationTest`, `OrderServiceIntegrationTest`). 핵심 동시성 시나리오 외에는 `@Transactional` + H2 인메모리 DB로도 충분할 수 있으니, 슬라이스 테스트/단위 테스트를 추가해 전체 파이프라인을 가볍게 유지하는 것이 좋다.
- `IntegrationTestSupport`에 `TransactionTemplate`을 주입해 테스트에서도 명시적 트랜잭션을 사용하게 한 점은 안전하지만, 공통 헬퍼(`inTransaction`)을 적극적으로 사용하지 않는 테스트도 눈에 띈다. 베이스 클래스를 sealed하게 만들거나 lint 규칙으로 강제하면 테스트 간 일관성을 유지하기 쉽다.

## 6. 추가 제안
- 공통적인 로깅/모니터링: 쿠폰 발급이나 재고 차감같이 중요한 경합 포인트는 현재 경고/로그가 거의 없다. 실패 원인을 추적할 수 있도록 `logger.debug/info` 수준의 로그와 `Metric`을 추가해 운영 가시성을 확보하면 좋다.
- API/서비스 계층에서 DTO ↔ 도메인 변환이 여러 곳에서 중복된다 (예: `ProductService`의 `CreateProductCommand`, `OrderService`의 여러 `Command`). MapStruct나 Kotlin DSL을 도입하면 코드가 한층 간결해지고 실수 가능성이 줄어든다.

---
위 항목들은 시니어 스프링 & DB 엔지니어 관점에서 본 우선순위 이슈들입니다. 특히 동시성 제어(`consumeStock`, `incrementIssuedQuantity`)는 지금도 잘 동작하지만 확장/운영 시 리스크가 크니, 분산 락/DB 트랜잭션 전략을 정교하게 다듬는 것을 추천드립니다.
