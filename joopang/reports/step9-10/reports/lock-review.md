# Lock & Concurrency Review

## 1. JVM 로컬 락이라 다중 인스턴스에서 무력화됨
- **위치**: `src/main/kotlin/io/joopang/services/product/infrastructure/ProductLockManagerImpl.kt:17-39`, `src/main/kotlin/io/joopang/services/coupon/infrastructure/CouponLockManagerImpl.kt:17-39`
- **상태**:
  - 두 구현 모두 `ConcurrentHashMap<Long, ReentrantLock>`을 만들어, 해당 JVM 프로세스 안에서만 상품/쿠폰 템플릿 단위로 락을 보장한다.
  - `OrderService.reserveStock`(`src/main/kotlin/io/joopang/services/order/application/OrderService.kt:203-255`)와 `CouponService.issueCoupon`(`src/main/kotlin/io/joopang/services/coupon/application/CouponService.kt:33-79`)은 이 락을 신뢰하고, 트랜잭션이 끝날 때까지 잡고 있는 전제 하에 비즈니스 검증을 수행한다.
- **문제**:
  - 애플리케이션이 인스턴스 2개 이상으로 확장되면 각 프로세스마다 별도의 `ReentrantLock`이 존재해 전혀 동기화되지 않는다.
  - 예: 서버 A와 B가 같은 상품 재고를 동시에 차감하면, 두 서버 모두 `aggregate.items`에서 동일한 재고를 읽고 검증을 통과한다. 차감 단계에서만 DB가 충돌을 감지하므로 두 번째 요청은 `InsufficientStockException`으로 실패하거나(서비스 품질 저하) 로그에서만 경고가 남는다. 쿠폰 발급 역시 단일 JVM을 전제하고 있어 템플릿 발급량/사용자 한도 검사가 분산 환경에서 깨질 수 있다.
- **개선**:
  1. Redis/ZooKeeper 등 외부 분산 락으로 교체하거나, DB의 행 락 + 조건부 업데이트만으로 일관성을 보장하도록 리팩터링한다.
  2. 최소한 단일 인스턴스 전제라는 사실을 명시적으로 문서화하고, 다중 인스턴스 배포 시 비활성화할 수 있는 토글을 둔다.

## 2. 트랜잭션이 끝날 때까지 락을 쥐면서 SKU 순서에 따라 교착 상태 발생
- **위치**: `src/main/kotlin/io/joopang/services/order/application/OrderService.kt:203-255`, `src/main/kotlin/io/joopang/services/product/infrastructure/ProductLockManagerImpl.kt:17-39`
- **상태**:
  - `ProductLockManagerImpl`는 스레드가 트랜잭션 안에 있으면 `TransactionSynchronization.afterCompletion`에서만 `lock.unlock()`을 호출한다. 즉, `action()`이 끝난 뒤에도 커밋/롤백 시점까지 락을 유지한다.
  - `reserveStock`는 `items.groupBy { it.productId }` 결과를 **정렬하지 않은 상태**로 순회하며 상품마다 `withProductLock`을 호출한다. 그룹핑이 `LinkedHashMap`이라 입력 순서에 좌우된다.
- **문제**:
  - 주문 A가 `[상품 1, 상품 2]`, 주문 B가 `[상품 2, 상품 1]` 순서로 동일 트랜잭션에서 재고를 잡으면, 각각 첫 번째 상품 락을 쥔 뒤 두 번째 락을 기다리면서 서로 영원히 대기하는 교착 상태가 생긴다. 현재 구현에는 타임아웃이나 `tryLock`이 없어 스레드가 그대로 정지한다. 자세한 재현은 `reports/step9-10/reports/deadlock-analysis.md` 참고.
- **개선**:
  1. 락 획득 전에 productId를 정렬해 모든 트랜잭션이 동일한 순서로 락을 요청하게 만든다.
  2. 또는 트랜잭션과 무관하게 `action()`이 끝나면 즉시 언락하고, 실제 일관성은 DB의 `UPDATE ... WHERE` 조건/행 락으로 확보한다.
  3. `tryLock` + 백오프 재시도를 추가해 영구 대기 대신 빠르게 실패시키고 상위 계층에서 재시도하도록 한다.

## 3. 쿠폰 사용 시 행 잠금이 없어 동시에 두 주문이 같은 쿠폰을 소비할 수 있음
- **위치**: `src/main/kotlin/io/joopang/services/order/application/OrderService.kt:148-165`, `src/main/kotlin/io/joopang/services/coupon/domain/Coupon.kt`, `src/main/kotlin/io/joopang/services/coupon/infrastructure/CouponRepository.kt`
- **현상**:
  - 결제 처리에서 `order.discounts`를 순회하며 `couponRepository.findByIdOrNull(couponId)`로 쿠폰을 조회한다. 조회 시 `@Lock`이나 `@Version`이 없어 단순 스냅샷만 읽는다.
  - 두 개의 서로 다른 주문이 동일 쿠폰을 참조하고 동시에 `processPayment`를 수행하면, 둘 다 `coupon.status == AVAILABLE` 검사를 통과한다.
  - 각 트랜잭션은 `coupon.markUsed(orderId, usedAt)` 후 `couponRepository.save(updatedCoupon)`을 호출한다. `UPDATE coupons SET ... WHERE id = ?`가 단순 실행되므로 마지막으로 커밋한 트랜잭션이 `order_id`를 덮어쓰고 끝난다. 첫 번째 트랜잭션도 성공으로 간주되기 때문에, 실제로는 하나의 쿠폰으로 두 주문이 할인된 상태가 된다.
- **영향**:
  - 쿠폰 다중 사용(AKA double spending)으로 인해 할인 금액이 중복 적용되고 정산이 맞지 않는다.
  - 사후에는 쿠폰 레코드가 마지막 주문만 가리켜 감사/추적도 어렵다.
- **개선**:
  1. 쿠폰 조회 시 `@Lock(LockModeType.PESSIMISTIC_WRITE)` 또는 `SELECT ... FOR UPDATE`를 사용해 동시에 같은 쿠폰을 집지 못하게 한다.
  2. 혹은 `UPDATE coupons SET status='USED', order_id=:orderId WHERE id=:couponId AND status='AVAILABLE'`처럼 조건부 업데이트를 수행하고, 업데이트된 행 수가 1이 아닐 경우 예외를 던져 재시도/실패로 처리한다.
  3. `Coupon` 엔티티에 `@Version`을 추가해 낙관적 락으로도 중복 사용을 차단할 수 있다.

---

### 요약
세 가지 이슈 모두 락을 JVM 내부에서만 관리하거나, 락·트랜잭션 경계가 잘못 설정되어 있어 동시성 환경에서 쉽게 깨집니다. 분산 락 도입, 락 획득 순서 고정, 그리고 쿠폰 사용 시 행 단위 잠금/낙관적 락을 적용해 데이터를 보호해야 합니다.
