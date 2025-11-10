# 인덱스 최적화 제안서

_작성일: 2025-11-10_

Spring Data/JPA 전환 이후 대부분의 조회가 EntityManager 기반 JPQL/네이티브 쿼리로 수행되면서, 특정 컬럼 접근이 빈번하지만 아직 적절한 보조 인덱스가 없는 테이블이 확인됐습니다. 아래 제안은 현재 코드 상의 조회 패턴을 기준으로, MySQL 8.0에서 손쉽게 적용할 수 있는 인덱스 계획과 기대 효과를 정리한 것입니다.

## 1. `cart_items`
- **주요 쿼리**
  - `findByUserId`, `findByUserIdAndProductItemId`, `deleteByUserId` (`CartItemRepository`)
- **문제**
  - 사용자별 장바구니 조회와 품목 병합 시 `user_id`+`product_item_id` 조합을 반복적으로 스캔하지만, PK(`id`) 외 보조 인덱스가 없습니다.
- **권장 인덱스**
  ```sql
  CREATE INDEX idx_cart_items_user ON cart_items(user_id);
  CREATE UNIQUE INDEX uk_cart_items_user_product_item ON cart_items(user_id, product_item_id);
  ```
- **기대 효과**
  - 사용자 장바구니 조회와 게스트→회원 병합 (`mergeCarts`) 시 풀스캔 대신 range scan으로 전환돼 지연 시간이 크게 줄고, 동일 사용자·옵션 중복 저장을 DB 레벨에서 차단 가능.

## 2. `coupons`
- **주요 쿼리**
  - `findUserCoupons`, `findUserCoupon`, `findUserCouponByTemplate` (`CouponRepository`)
- **문제**
  - 쿠폰 발급/조회가 모두 `user_id` 혹은 `user_id + coupon_template_id` 조건으로 실행되지만 관련 인덱스가 없어 만 건 단위 테이블에서 비용이 커집니다.
- **권장 인덱스**
  ```sql
  CREATE INDEX idx_coupons_user ON coupons(user_id);
  CREATE INDEX idx_coupons_user_template ON coupons(user_id, coupon_template_id);
  ```
- **기대 효과**
  - 동일 사용자의 보유 쿠폰 집합을 빠르게 스캔할 수 있어 발급·만료 처리 트랜잭션 시간이 단축되고, 템플릿별 중복 발급 검사도 index only scan으로 해결.

## 3. `orders` / `order_items`
- **주요 쿼리**
  - `findAll` (정렬: `ordered_at`) (`OrderRepository`)
  - 인기 상품 통계 네이티브 쿼리 (`ProductRepository.findPopularProductsSince`) — 조건: `orders.status='PAID' AND orders.paid_at >= ?`, 조인: `order_items.order_id`, 그룹: `order_items.product_id`
- **문제**
  - 대량 주문 이력에서 PAID 주문만 추려 최근 매출을 집계할 때 테이블/조인 스캔 비용이 커집니다.
- **권장 인덱스**
  ```sql
  CREATE INDEX idx_orders_status_paid_at ON orders(status, paid_at);
  CREATE INDEX idx_orders_ordered_at ON orders(ordered_at);
  CREATE INDEX idx_order_items_order_id ON order_items(order_id);
  CREATE INDEX idx_order_items_product_id ON order_items(product_id);
  ```
- **기대 효과**
  - 상태·결제일 조건을 만족하는 주문 범위를 빠르게 찾고, 주문/주문항목 조인 시 Nested Loop 효율을 개선합니다. `findAll`의 `order by ordered_at` 역시 Filesort 없이 커버 가능.

## 4. `deliveries`
- **주요 쿼리**
  - `findByOrderItemId` (`DeliveryRepository`)
- **문제**
  - 주문 품목 단위로 배송 객체를 가져오지만 `order_item_id`에 인덱스가 없어 배송 수 증가 시 응답이 느려집니다.
- **권장 인덱스**
  ```sql
  CREATE INDEX idx_deliveries_order_item_id ON deliveries(order_item_id);
  ```
- **기대 효과**
  - 주문 상세 화면과 배송 상태 동기화 작업에서 배송 레코드를 즉시 탐색 가능.

## 5. `payments`
- **주요 쿼리**
  - `findByOrderId` (`PaymentRepository`)
- **문제**
  - 결제/환불 이력을 주문 ID로 반복 조회하지만 보조 인덱스가 없습니다.
- **권장 인덱스**
  ```sql
  CREATE INDEX idx_payments_order_id ON payments(order_id);
  ```
- **기대 효과**
  - 주문별 결제 히스토리 조회, 정산 배치가 인덱스 range scan으로 전환됩니다.

## 6. `categories`
- **주요 쿼리**
  - `findByParentId` (`CategoryRepository`)
- **문제**
  - 카테고리 트리 조회 시 부모 ID별 자식을 자주 읽지만, `parent_id`에 인덱스가 없어 전체 스캔 후 필터링합니다.
- **권장 인덱스**
  ```sql
  CREATE INDEX idx_categories_parent_id ON categories(parent_id);
  ```
- **기대 효과**
  - 전체 계층 로딩과 관리자 UI 탐색 시 I/O가 크게 감소합니다.

---

### 적용 팁
- MySQL 8.0에서는 `CREATE INDEX`가 온라인 DDL로 실행돼 읽기 잠금이 최소화됩니다. 다만 대량 테이블에 인덱스를 추가할 때는 저부하 시간대에 수행하고, 배포 스크립트에 포함시키는 것이 안전합니다.
- `ddl-auto=update` 환경에서는 애플리케이션이 인덱스를 자동 생성하지 않으므로, 위 DDL을 Flyway/Liquibase 마이그레이션이나 DBA 스크립트에 명시적으로 추가해야 합니다.

 1. Flyway/Liquibase 마이그레이션에 보고서의 DDL을 반영해 운영 DB에 적용하세요(온라인 DDL이지만 저부하 시간대 권장).
  2. 인덱스 추가 후 ANALYZE TABLE로 통계를 새로 계산하고, 인기 상품/장바구니 API의 실제 쿼리 플랜을 확인해 효과를 검증하세요.