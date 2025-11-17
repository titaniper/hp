# 인덱스 최적화 제안서

_작성일: 2025-11-13_

👉 본 문서는 **MySQL 8.0(InnoDB)** 기준으로 작성했습니다.

현재 서비스는 Spring Data Repository 대신 `EntityManager`를 직접 사용하는 JPQL/네이티브 쿼리 위주 구조입니다. 테이블 대부분이 UUID(BINARY(16)) PK만 가진 상태여서, 조회 조건이나 정렬 조건이 붙는 곳에서 풀스캔/Filesort가 빈번하게 발생할 수 있습니다. 아래 제안은 현재 코드 기준으로 꼭 필요한 보조 인덱스를 정리한 것입니다.

## 1. `cart_items`
- **주요 경로**: `CartItemRepository.findByUserId()` / `findByUserIdAndProductItemId()` / `deleteByUserId()` (`src/main/kotlin/io/joopang/services/cart/infrastructure/CartItemRepository.kt:16-51`), `CartService.addItem()` · `mergeCarts()` (`src/main/kotlin/io/joopang/services/cart/application/CartService.kt:28-159`)
- **권장 인덱스**
  ```sql
  CREATE UNIQUE INDEX idx_cart_items_user_product_item
    ON cart_items(user_id, product_item_id);
  ```
- **기대 효과**: 사용자 장바구니 조회/병합 시 풀스캔 대신 range scan으로 전환되고, 동일 사용자·옵션 조합 중복 삽입을 DB 레벨에서 차단해 `CartService` 경쟁 조건을 제거합니다.

## 2. `coupons`
- **주요 경로**: `CouponRepository.findUserCoupons()` / `findUserCouponByTemplate()` (`src/main/kotlin/io/joopang/services/coupon/infrastructure/CouponRepository.kt:19-45`), `CouponService.issueCoupon()` (`src/main/kotlin/io/joopang/services/coupon/application/CouponService.kt:26-75`)
- **권장 인덱스**
  ```sql
  CREATE INDEX idx_coupons_user_template
    ON coupons(user_id, coupon_template_id);
  ```
- **기대 효과**: 사용자 보유 쿠폰 목록과 템플릿별 중복 발급 검사 모두 index only scan으로 처리되어 선착순 발급 API의 지연이 줄고, 만 건 단위 쿠폰에서도 사용자 단위 스캔 비용을 최소화할 수 있습니다.

## 3. `orders`
- **주요 경로**: `OrderRepository.findAll()` (`src/main/kotlin/io/joopang/services/order/infrastructure/OrderRepository.kt:43-52`), 인기 상품 통계 네이티브 쿼리 `ProductRepository.findPopularProductsSince()` (`src/main/kotlin/io/joopang/services/product/infrastructure/ProductRepository.kt:47-79`)
- **권장 인덱스**
  ```sql
  CREATE INDEX idx_orders_status_paid_at_desc
    ON orders(status, paid_at DESC);

  CREATE INDEX idx_orders_ordered_at_desc
    ON orders(ordered_at DESC);
  ```
- **기대 효과**: 결제 완료 주문을 `status='PAID' AND paid_at >= ?` 조건으로 빠르게 걸러 인기 상품 집계 조인의 드라이빙 비용을 줄이고, 최근 주문을 우선 노출하는 정렬(`ORDER BY ordered_at DESC`)도 Filesort 없이 DESC 인덱스를 그대로 활용할 수 있습니다.

## 4. `order_items`
- **주요 경로**: `OrderRepository.findItems()` / `deleteItemsByOrderId()` (`src/main/kotlin/io/joopang/services/order/infrastructure/OrderRepository.kt:69-95`), `ProductRepository.findPopularProductsSince()` (`src/main/kotlin/io/joopang/services/product/infrastructure/ProductRepository.kt:47-79`)
- **권장 인덱스**
  ```sql
  CREATE INDEX idx_order_items_order_id
    ON order_items(order_id);

  CREATE INDEX idx_order_items_product_id
    ON order_items(product_id);
  ```
- **기대 효과**: 주문 단위 아이템 로딩/삭제가 전부 order_id range scan으로 바뀌어 `OrderRepository`가 대량 주문에서도 안정적인 성능을 내고, 상품 인기 집계 시 `product_id` 조인/그룹 단계에서 조기 필터링이 가능합니다.

## 5. `order_discounts`
- **주요 경로**: `OrderRepository.findDiscounts()` / `deleteDiscountsByOrderId()` (`src/main/kotlin/io/joopang/services/order/infrastructure/OrderRepository.kt:77-94`)
- **권장 인덱스**
  ```sql
  CREATE INDEX idx_order_discounts_order_id
    ON order_discounts(order_id);
  ```
- **기대 효과**: 주문 집계 시 할인 행 로딩/삭제가 테이블 풀스캔 없이 수행되어 결제 처리(`processPayment`)의 응답 시간을 안정적으로 유지할 수 있습니다.

## 6. `product_items`
- **주요 경로**: `ProductRepository.findItems()` / `deleteItemsByProductId()` (`src/main/kotlin/io/joopang/services/product/infrastructure/ProductRepository.kt:26-121`), 재고 검증/예약 로직 (`CartService` · `OrderService`)
- **권장 인덱스**
  ```sql
  CREATE INDEX idx_product_items_product_id
    ON product_items(product_id);
  ```
- **기대 효과**: 상품 상세/장바구니/주문 흐름에서 반복되는 상품-옵션 로딩과 삭제가 모두 product_id 기반 range scan으로 처리되어 재고 예약 루프의 잠금 보유 시간을 단축합니다.

## 7. `deliveries`
- **주요 경로**: `DeliveryRepository.findByOrderItemId()` (`src/main/kotlin/io/joopang/services/delivery/infrastructure/DeliveryRepository.kt:23-29`), `DeliveryService.listDeliveries()` (`src/main/kotlin/io/joopang/services/delivery/application/DeliveryService.kt:20-45`)
- **권장 인덱스**
  ```sql
  CREATE INDEX idx_deliveries_order_item_id
    ON deliveries(order_item_id);
  ```
- **기대 효과**: 주문 상세에서 품목별 배송 정보 조회가 즉시 order_item_id range scan으로 수행되어 배송 상태 동기화 API의 응답이 선형으로 늘어나지 않습니다.

## 8. `payments`
- **주요 경로**: `PaymentRepository.findByOrderId()` (`src/main/kotlin/io/joopang/services/payment/infrastructure/PaymentRepository.kt:23-29`), `PaymentService.listPayments()` (`src/main/kotlin/io/joopang/services/payment/application/PaymentService.kt:20-49`)
- **권장 인덱스**
  ```sql
  CREATE INDEX idx_payments_order_id
    ON payments(order_id);
  ```
- **기대 효과**: 주문 단위 결제/환불 이력 조회가 order_id range scan으로 바뀌어 정산/배치 작업이 테이블 크기에 덜 민감해집니다.

## 9. `categories`
- **주요 경로**: `CategoryRepository.findByParentId()` (`src/main/kotlin/io/joopang/services/category/infrastructure/CategoryRepository.kt:23-37`), 관리자 카테고리 트리 조회(`CategoryService.listCategories()`)
- **권장 인덱스**
  ```sql
  CREATE INDEX idx_categories_parent_id
    ON categories(parent_id);
  ```
- **기대 효과**: 부모별 자식 카테고리 탐색이 전부 parent_id range scan으로 동작해 전체 트리를 단계별로 펼칠 때 불필요한 풀스캔을 제거합니다.

---

### 적용 및 검증 팁
- MySQL 8.0의 온라인 DDL(`CREATE INDEX ... ALGORITHM=INPLACE, LOCK=NONE`)을 활용하면 서비스 중단 없이 적용 가능합니다. 다만 대용량 테이블(`orders`, `order_items`)은 저부하 시간대를 선택하세요.
- Flyway/Liquibase 마이그레이션으로 관리하고, 배포 후 `ANALYZE TABLE <table>`을 실행해 통계를 최신 상태로 유지한 뒤 `EXPLAIN`으로 쿼리 플랜이 실제로 인덱스를 태우는지 확인하세요.
- 정렬 방향이 명확한 쿼리는 인덱스 정의에 `ASC`/`DESC`를 명시하면(MySQL 8.0+) 다중 컬럼 정렬 조건을 그대로 커버할 수 있습니다. 예) `ON orders(status, paid_at DESC)`.
- 카디널리티가 낮은 컬럼(상태 값 등)에 새 인덱스를 추가할 때는 `SHOW INDEX FROM <table>`로 기존 인덱스와 중복되지 않는지 검사하고, 필요 시 불필요한 인덱스를 함께 정리해 쓰기 부하를 억제하세요.
