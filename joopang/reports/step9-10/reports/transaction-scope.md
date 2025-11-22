# Transaction Scope Review

## 1. Payment transaction includes external I/O
- 위치: `src/main/kotlin/io/joopang/services/order/application/OrderService.kt:167-200`
- 문제: 결제 트랜잭션 안에서 사용자 잔액 차감, 쿠폰 상태 갱신, 주문 저장이 끝난 뒤 `dataTransmissionService.send(...)`/`addToRetryQueue(...)`를 호출한다. 실제 운영에서는 외부 API 호출/큐 전송이 수백 ms 이상 걸리거나 예외를 던질 수 있다. 이 동안 주문/유저/쿠폰에 대한 DB 락이 열린 채 유지되어 동시 결제 트랜잭션을 블로킹할 수 있고, 외부 호출 실패가 발생하면 전체 결제가 롤백되면서 idempotency 보장이 어렵다.
- 해결 아이디어:
  1. 결제 커밋 직후에만 전송하도록 outbox/event 패턴을 도입한다. 예: 주문 데이터를 `order_data_outbox` 테이블에 INSERT 한 뒤 `TransactionSynchronizationManager.registerSynchronization(...afterCommit...)` 또는 `@TransactionalEventListener(phase = AFTER_COMMIT)`로 비동기 전송.
  2. 전송/재시도 큐 추가 시에는 별도 빈이나 메시지 브로커에서 재시도하도록 하여, 본 트랜잭션은 결제 상태 확정까지만 담당하게 한다.

## 2. ProductLockManager가 트랜잭션 종료까지 락을 붙잡음
- 위치: `src/main/kotlin/io/joopang/services/product/infrastructure/ProductLockManagerImpl.kt:19-39`, 사용처 `OrderService.reserveStock(...)` (`src/main/kotlin/io/joopang/services/order/application/OrderService.kt:203-240`).
- 문제: `withProductLock`가 `TransactionSynchronizationManager`에 `afterCompletion` 콜백을 등록해 락 해제를 커밋 이후까지 지연한다. 그래서 `createOrder` 트랜잭션 전체(사용자 조회, 쿠폰 계산, 주문 저장 등)가 여러 개의 ReentrantLock을 쥔 상태로 진행된다. 특정 상품에서 주문 생성이 몰리면 한 트랜잭션이 쿠폰 조회/주문 persist 등의 비즈니스 로직을 수행하는 동안 다른 요청이 동일 상품을 전혀 처리하지 못해 대기하게 된다. 또한 트랜잭션 롤백이 발생하면 이미 소비한 재고 복구 로직이 없으므로, 긴 락 유지가 굳이 보장해 주는 것도 없다.
- 해결 아이디어:
  1. 락 해제를 `action()` 블록이 끝나는 즉시 수행하고, 재고 차감/검증 파트만 짧은 세그먼트로 감싼다. 필요한 경우 재고 차감을 별도 `@Transactional(propagation = REQUIRES_NEW)` 메서드로 분리해 lock 범위와 트랜잭션 범위를 일치시킨다.
  2. 재고 차감이 끝난 뒤에는 DB 측 제약(수량 컬럼)과 낙관적 재시도에 맡기고, 쿠폰 계산/주문 저장은 락 없이 진행하도록 구조를 분리한다. 병행 주문량이 많은 SKU에서도 처리량이 크게 올라간다.

## 3. CartService가 동일 트랜잭션 안에서 다시 장바구니 전체를 로딩
- 위치: `src/main/kotlin/io/joopang/services/cart/application/CartService.kt:36-165`.
- 문제: `addItem`/`updateItemQuantity`/`removeItem`/`mergeCarts`는 `@Transactional`(readOnly=false)로 실행되는데, 마지막에 동일 클래스의 `getCart()`를 호출한다. Spring AOP 프록시가 적용되지 않으므로 `getCart`는 같은 트랜잭션 컨텍스트에서 실행되고, `buildView`(`CartService.kt:202-291`)가 cart_items, products, product_items를 전부 읽어 cart totals를 계산할 때까지 트랜잭션이 열린다. 그 사이 cart_items 행에 대한 락이 유지되고, 동시에 제품 정보를 읽어오는 SELECT도 같은 트랜잭션 안에서 수행되어 불필요한 lock footprint와 커넥션 점유 시간이 늘어난다.
- 해결 아이디어:
  1. 쓰기 메서드는 “변경 완료”까지만 트랜잭션으로 묶고, View 재구성은 커밋 후 별도 read-only 쿼리에서 수행한다. 예: 서비스 메서드가 `Unit`(또는 변경된 cartId)만 반환하게 한 뒤, 컨트롤러에서 `cartService.getCart(...)`를 따로 호출.
  2. 혹은 `TransactionTemplate`/별도 빈을 사용해 `getCart`를 `REQUIRES_NEW, readOnly=true`로 실행하면 장바구니 뷰 계산이 커밋 이후 별도 커넥션에서 수행되어 본 트랜잭션 시간을 단축할 수 있다.

위 세 지점만 정리해도 트랜잭션 보유 시간이 크게 짧아지고, 주문/장바구니에서 동시 처리량이 올라갑니다.
