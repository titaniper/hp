# N+1 Query Analysis

## 1. Cart merge loads every product aggregate separately
- **위치**: `src/main/kotlin/io/joopang/services/cart/application/CartService.kt:115-176`, `src/main/kotlin/io/joopang/services/product/infrastructure/ProductRepository.kt:24-94`
- **현상**:
  - `CartService.mergeCarts`는 `sourceItems.forEach` 내부에서 `findProductAggregate(item.productId)`를 호출합니다.
  - `findProductAggregate`는 결국 `productRepository.findById(productId)`를 실행하는데, 이 메서드는 제품 1건을 조회한 뒤 또 한 번 `findItems(productId)`를 호출해 옵션 목록을 읽습니다.
  - 즉, 병합 대상 장바구니에 있는 SKU 수만큼 `products`와 `product_items` 쿼리가 반복됩니다. 동일한 `productId`가 여러 번 등장하면 같은 데이터를 다시 가져옵니다.
- **영향**:
  - 예를 들어 30개의 서로 다른 상품을 담은 소스 장바구니를 병합하면 `cart_items` 조회 2회(소스/타깃) 이후에 최소 60개의 추가 SELECT가 실행됩니다. 장바구니 대량 이동/이벤트 시 merge 호출이 몰리면 DB 연결 수를 빠르게 고갈시키고 응답 지연을 유발합니다.
  - 같은 상품이 여러 SKU를 가지는 경우에도 반복 조회가 일어나므로 CPU 캐시/트랜잭션 시간이 불필요하게 증가합니다.
- **추천 해결 방법**:
  1. `sourceItems`(필요하면 `targetItems`까지)에서 `productId` 목록을 뽑아 `productRepository.findProductsByIds(ids)` 또는 유사한 IN 쿼리로 한 번에 로딩한 뒤 `Map<Long, ProductWithItems>`로 캐싱해 사용합니다.
  2. 간단히는 `sourceItems.groupBy { it.productId }` 후 `findProductAggregate` 결과를 메모이제이션하여 동일 productId 조회 시 DB를 다시 치지 않도록 해도 N+1을 제거할 수 있습니다.
  3. 추가로 product+item을 조인해서 가져오는 전용 DAO(예: `SELECT p, pi FROM Product p JOIN FETCH p.items WHERE p.id IN :ids`)를 만들면 상품 한 묶음을 가져오는 쿼리를 1~2회로 줄일 수 있습니다.

## 2. Coupon validation during payment fetches coupons one-by-one
- **위치**: `src/main/kotlin/io/joopang/services/order/application/OrderService.kt:148-165`
- **현상**:
  - 결제 처리 시 `order.discounts.forEach` 내부에서 `discount.couponId`가 존재하면 `couponRepository.findByIdOrNull(couponId)`를 호출합니다.
  - 주문에 쿠폰 기반 할인 항목이 여러 개 있으면, 각 항목에 대해 별도의 SELECT가 실행됩니다. 이미 `OrderRepository.findWithDetailsByIdForUpdate`로 주문/할인을 모두 읽었는데, 쿠폰 검증 단계에서 다시 N회 쿼리가 발생하는 구조입니다.
- **영향**:
  - 프로모션/묶음 쿠폰을 여러 장 적용할 수 있는 주문일수록 결제 트랜잭션 내 DB round-trip 수가 늘어납니다. 예를 들어 할인 항목 5개면 `couponRepository` SELECT가 5번 추가되고, 결제 트랜잭션이 잡고 있는 락이 그만큼 오래 유지됩니다.
  - 결제 API는 SLA가 엄격한 영역이라 잦은 N+1 SELECT가 커넥션 대기·타임아웃으로 이어질 수 있습니다.
- **추천 해결 방법**:
  1. `order.discounts.mapNotNull { it.couponId }.distinct()`로 쿠폰 ID를 모아 `couponRepository.findAllById(ids)` (또는 custom `findAllByIdIn`)으로 한 번에 로딩하고 Map으로 변환해 검증합니다.
  2. 쿠폰 상태/소유자 검증이 빈번하다면 `OrderDiscount`에 필요한 쿠폰 스냅샷(소유자 ID, 상태)을 포함시키거나, `Coupon`을 `OrderDiscount`와 조인하여 `@EntityGraph(attributePaths = ["discounts.coupon"])`로 같이 불러오는 방법도 고려할 수 있습니다.
  3. 동시에 여러 쿠폰을 처리할 때는 DB 측에서도 `WHERE id IN (...) FOR UPDATE` 형태로 잠글 수 있도록 batch update용 쿼리를 별도로 제공하면 락 순서가 일정해집니다.

위 두 지점의 N+1을 제거하면 장바구니 병합/결제 처리 시 DB 왕복 수를 현저히 줄일 수 있고, 트랜잭션 보유 시간이 짧아져 동시 처리량이 개선됩니다.
