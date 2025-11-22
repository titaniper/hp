# Deadlock Risk Report

## 1. SKU별 상품 락이 호출 순서에 따라 데드락을 만들 수 있음
- **위치**: `src/main/kotlin/io/joopang/services/order/application/OrderService.kt:203-255`, `src/main/kotlin/io/joopang/services/product/infrastructure/ProductLockManagerImpl.kt:19-39`
- **현상**:
  - `reserveStock`는 `items.groupBy { it.productId }` 결과를 순회하며 각 상품에 대해 `productLockManager.withProductLock(productId)`을 호출합니다.
  - `ProductLockManagerImpl`은 트랜잭션이 열려 있으면 `TransactionSynchronization`에 `lock.unlock()`을 등록하고, `finally` 블록에서는 언락하지 않습니다. 즉, 트랜잭션이 끝날 때까지 JVM 락을 쥔 채 다음 상품으로 넘어갑니다.
  - 따라서 하나의 주문이 여러 SKU를 포함하면, 첫 번째 SKU 락을 잡은 상태로 두 번째, 세 번째 락을 계속 획득해 나갑니다.
- **데드락 시나리오**:
  1. 주문 A: `[상품1, 상품2]` 순서. 상품1 락을 선점하고 상품2를 기다림.
  2. 주문 B: `[상품2, 상품1]` 순서. 상품2 락을 선점하고 상품1을 기다림.
  3. 두 트랜잭션 모두 자신이 가진 락을 커밋 시점까지 놓지 않으므로 서로가 기다리는 락을 절대 받을 수 없어 데드락이 발생합니다. JVM 레벨 ReentrantLock이라 감지나 타임아웃도 없어 스레드가 영구 대기합니다.
- **영향**:
  - 다중 SKU 주문(또는 동일 고객/다른 고객이 동시에 다른 순서로 요청한 경우)에서 API가 멈추고 스레드가 고갈됩니다.
  - 락이 트랜잭션 종료 시까지 유지돼 다른 요청도 줄줄이 대기할 수 있습니다.
- **개선 제안**:
  1. `reserveStock`에서 락 획득 전에 `items.groupBy` 결과 키를 정렬(예: productId 오름차순)해 락 획득 순서를 전역적으로 고정합니다.
  2. 또는 `ProductLockManager.withProductLock`을 변경해 트랜잭션 여부와 관계없이 `action()` 종료 직후 즉시 락을 해제하고, 필요한 경우 DB의 행 락으로 일관성을 보장합니다.
  3. 추가로 `tryLock` + 타임아웃/재시도 백오프를 두어 교착을 완화할 수 있습니다.

## 결론
현재 구현은 복수 SKU 주문을 동시에 처리할 때 JVM 락 교착 상태가 발생할 수 있습니다. 락 획득 순서를 고정하거나, 트랜잭션 단위가 아니라 SKU 작업 단위로 락을 해제하도록 수정하여 문제를 방지하는 것이 필요합니다.
