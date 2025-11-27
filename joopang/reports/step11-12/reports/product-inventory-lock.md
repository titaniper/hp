# 상품 재고 분산락 리포트

## 배경

- `OrderService.createOrder`(`src/main/kotlin/io/joopang/services/order/application/OrderService.kt:33-172`)는 주문 생성 시 `reserveStock`을 호출하여 각 상품의 잔여 수량을 확인하고 `productRepository.consumeStock`으로 차감한다.
- 기존 `ProductLockManagerImpl`은 JVM 내부 `ReentrantLock`으로만 보호되어, 다중 인스턴스 배포 시 서로 다른 서버가 같은 상품을 동시에 차감하면 `consumeStock` 전후 레이스 컨디션이 발생했다.
- 인기 상품·타임세일 구간에서는 수백 RPS 수준의 주문이 들어오기 때문에, 재고가 음수로 떨어지거나 소량 재고가 두 배로 판매되는 사고를 막기 위해 분산 락이 필요했다.

## 문제해결

- **AOP 기반 락 공통화**: 쿠폰 발급에서 사용한 공통 애노테이션 `@DistributedLock`과 Aspect(`src/main/kotlin/io/joopang/common/lock`)를 재고 로직에도 재사용했다. SpEL 키(`lock:product:<productId>`)만 바꿔주면 동일한 Redisson 락을 활용할 수 있다.
- **서비스 직접 적용**: `ProductLockManager`/`ProductLockManagerImpl`을 완전히 제거하고, `OrderService.reserveStockForProduct`에 `@DistributedLock(prefix = "lock:product:", key = "#productId")`를 직접 선언해 SKU별 락을 잡는다. 락 설정과 실패 메시지를 서비스에 붙여 도메인별 정책을 더 쉽게 조정할 수 있다.
- **OrderService 적용**: `OrderService.reserveStock`이 상품을 그룹핑한 뒤 `reserveStockForProduct`(락이 선언된 메서드)를 호출하도록 분리했다. 다중 상품 주문 시에도 SKU 순서대로 락을 순차 취득하므로 데드락을 예방하면서 개별 재고 차감이 보호된다.
- **의존성/빌드 구성**: AOP 적용을 위해 `spring-boot-starter-aop`를 추가했고(`build.gradle.kts`), Redisson 설정(`RedissonConfig`) 및 Redis Testcontainer 인프라는 쿠폰 락 때 도입한 구성을 그대로 활용했다.

## 테스트

- `./gradlew test`를 실행해 주문/상품 관련 단위·통합 테스트가 모두 통과함을 확인했다. 테스트 실행 시 MySQL과 Redis Testcontainer가 뜨므로 분산 락 경로도 실제 Redis로 검증된다.
- `OrderServiceIntegrationTest.concurrent orders do not oversell stock`에서 같은 상품을 동시에 주문해도 재고가 0 이하로 떨어지지 않는지 확인했다.

## 한계점

- 상품별 락 대기시간(2초)이 지나면 곧바로 실패시키므로, 초고부하 상황에서는 사용자 재시도가 늘어날 수 있다. 필요 시 상품 유형에 따라 대기시간을 조정하거나 큐 기반 선착순 정책을 고려해야 한다.
- 다중 상품 주문 시 순차적으로 여러 락을 획득하므로, 락 획득 순서와 대기시간이 길어질 수 있다. 정렬된 상품 ID 순서대로 락을 잡고 있지만, 여전히 교착 방지를 위해 모니터링이 필요하다.
- Redis 단일 노드 장애 시 재고 차감이 불가능해진다. Sentinel/Cluster 구성 또는 멀티 Redis 인스턴스를 이용한 Redlock 고려가 필요하다.

## 결론

- 주문 재고 차감 로직을 Redisson 기반 분산 락으로 감싸 다중 인스턴스 환경에서도 초과 판매 없이 일관된 재고 상태를 유지할 수 있게 되었다.
- `@DistributedLock` 애노테이션으로 락 구성 파라미터를 한곳에서 선언하므로, 쿠폰·재고 등 여러 도메인에서 동일한 방식으로 락을 적용하고 운영할 수 있다.

## NEXT

1. Hot 상품에 대해 락 대기시간/리스 시간을 별도로 구성할 수 있도록 프로퍼티화를 검토한다.
2. Redis 락 획득 실패/대기 지표를 Prometheus에 노출해 과도한 재시도가 감지되면 알람을 걸고, 필요 시 Graceful Degradation 전략을 설계한다.
3. 재고 차감 로직을 CQRS/재고 전용 서비스로 분리해 락 홀더가 줄어드는지 PoC를 진행한다.

- 적용 코드: `build.gradle.kts`, `src/main/kotlin/io/joopang/common/lock/DistributedLock.kt`, `src/main/kotlin/io/joopang/common/lock/DistributedLockAspect.kt`, `src/main/kotlin/io/joopang/services/order/application/OrderService.kt`.
