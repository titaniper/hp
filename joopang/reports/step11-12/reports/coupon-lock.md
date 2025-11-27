# 쿠폰 발급 분산락 리포트

## 배경

- `CouponService.issueCoupon`(`src/main/kotlin/io/joopang/services/coupon/application/CouponService.kt:21-79`)는 발급 수량 차감, 사용자 중복 검증 등을 하나의 트랜잭션에서 처리하지만, 템플릿 단위 락이 JVM 내부 `ReentrantLock`(`CouponLockManagerImpl`)에 의존하고 있었다.
- 애플리케이션 인스턴스가 늘어나거나 롤링 배포 중인 경우 서로 다른 JVM에서 동시에 발급 요청을 처리하면, DB `SELECT ... FOR UPDATE` 이전 구간에서 중복 발급·재고 초과 발급이 발생할 수 있었다.
- 선착순 쿠폰은 짧은 시간에 폭발적으로 요청이 몰리므로, 인스턴스 수만큼 race condition이 늘어나는 상황을 방치할 수 없어 Redis 기반 분산 락을 도입하기로 했다.

## 문제해결

- **Redisson 도입**: `gradle/libs.versions.toml`과 `build.gradle.kts`에 `org.redisson:redisson-spring-boot-starter` 의존성을 추가해 Redisson 클라이언트를 사용할 수 있도록 했다. Pub/Sub 기반 대기열과 Watchdog 갱신을 그대로 활용해 네이티브 Redis `SETNX` 구현보다 안정성을 확보했다.
- **RedissonClient 구성**: `RedissonConfig`(`src/main/kotlin/io/joopang/config/RedissonConfig.kt`)에서 Spring `RedisProperties`를 주입받아 단일 서버 모드 구성을 생성하고, 비밀번호/DB 인덱스를 프로퍼티 기준으로 오버라이드하도록 했다. Bean destroy 시점에는 `shutdown()`을 호출해 커넥션을 정리한다.
- **AOP 기반 분산 락**: 공통 애노테이션 `@DistributedLock`(`src/main/kotlin/io/joopang/common/lock/DistributedLock.kt`)과 `DistributedLockAspect`(`src/main/kotlin/io/joopang/common/lock/DistributedLockAspect.kt`)를 만들었다. SpEL로 키를 계산하고 Redisson `RLock`을 획득/해제하는 로직을 Aspect 한 곳에 모아 코드 중복을 제거했다.
- **서비스 직접 적용**: 더 이상 별도 `CouponLockManager` 빈이 존재하지 않는다. `CouponService.issueCoupon` 자체에 `@DistributedLock(prefix = "lock:coupon-template:", key = "#command.couponTemplateId")`를 선언해 락 파라미터를 한 곳에서 관리하고, 락 실패 메시지도 서비스 코드에서 바로 정의한다.
- **락 키 설계**: 키는 `lock:coupon-template:<템플릿ID>` 형태로 구성했다. 템플릿 ID가 발급 수량·한도 계산의 단위이므로 동일 템플릿에 대한 요청만 직렬화하면 충분하고, 다른 템플릿의 발급은 서로 영향 없이 병렬로 처리할 수 있다. 네임스페이스 접두사를 붙여 다른 도메인의 락과 충돌하지 않도록 했다.
- **락 → 트랜잭션 순서 보장**: `@Order(Ordered.HIGHEST_PRECEDENCE)`로 설정된 AOP가 트랜잭션 프록시보다 먼저 락을 잡고, `CouponService.issueCoupon`의 `@Transactional`이 그다음 실행되어 “락 획득 → 트랜잭션 시작 → 커밋/롤백 → 락 해제” 순서를 일관되게 유지한다.
- **테스트 인프라 정비**: 테스트 실행 시에도 분산 락이 Redis 의존성을 요구하므로, `IntegrationTestSupport`(`src/test/kotlin/io/joopang/support/IntegrationTestSupport.kt`)가 기존 MySQL Testcontainer와 함께 `redis:7.2` 컨테이너를 띄우고 `DynamicPropertySource`로 호스트/포트를 주입하도록 확장했다. 덕분에 모든 `@SpringBootTest`가 동일한 Redis 환경을 사용한다.

## 테스트

- `./gradlew test`를 실행해 전 영역 회귀 테스트를 통과했다. 테스트 과정에서 MySQL/Redis Testcontainer가 자동으로 기동되어 분산 락 획득·해제가 실제 Redis를 통해 검증된다.

## 한계점

- Redisson 단일 인스턴스 구성이라 Redis 장애 시 쿠폰 발급이 전면 중단된다. Sentinel/Cluster 구성이나 멀티 Redis 노드를 사용하는 Redlock 도입을 검토해야 한다.
- 락 획득 대기시간(2초)과 리스 임대시간(5초)은 경험값이므로, 트래픽 폭증 또는 비정상적인 DB 지연이 발생하면 타임아웃/재시도 전략을 조정해야 한다.
- 템플릿 단위 단일 락은 공정성을 보장하지 않는다. 특정 사용자가 반복적으로 락을 선점하면 나머지 사용자가 장시간 대기할 수 있으며, 필요 시 Redisson `tryLock` 대신 `tryLockAsync` + 큐잉/레이트리밋를 고려해야 한다.

## 결론

- 쿠폰 발급 로직의 핵심 임계구역을 Redis 분산 락으로 감싸 다중 인스턴스 환경에서도 발급 수량과 사용자별 제한을 일관되게 지킬 수 있게 되었다.
- Testcontainer 기반 Redis 환경을 추가함으로써 개발자 로컬이나 CI에서도 동일한 분산 락 경로를 검증할 수 있게 되어, 실행 환경 간 격차를 줄였다.

## NEXT

1. 고부하 모의 테스트(K6 등)를 돌려 락 획득 대기시간·실패율을 측정하고 `LOCK_WAIT_SECONDS`/`LOCK_LEASE_SECONDS`를 데이터 기반으로 조정한다.
2. Redis 장애/느린 커맨드 감지를 위한 메트릭(연결 끊김 수, 락 획득 실패 비율)을 Prometheus에 노출하고, 알람을 걸어 중단 사태를 조기 인지한다.
3. 일부 쿠폰을 Redis 재고 카운터(`DECR`) 기반으로 처리해 분산 락 의존도를 줄이는 전략을 검토하고, 락과 원자 연산의 혼합 운용 가이드라인을 작성한다.

- 적용 코드: `build.gradle.kts`, `gradle/libs.versions.toml`, `src/main/kotlin/io/joopang/config/RedissonConfig.kt`, `src/main/kotlin/io/joopang/common/lock/DistributedLock.kt`, `src/main/kotlin/io/joopang/common/lock/DistributedLockAspect.kt`, `src/main/kotlin/io/joopang/services/coupon/application/CouponService.kt`, `src/test/kotlin/io/joopang/support/IntegrationTestSupport.kt`.
