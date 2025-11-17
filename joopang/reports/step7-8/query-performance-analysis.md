# 조회 성능 저하 분석 보고서

_작성일: 2025-11-13_

## 개요
복수의 조회 API가 애플리케이션 메모리/쿼리 측에서 전체 테이블을 읽거나 많은 N+1 호출을 발생시키고 있어, 실제 운영 규모에서는 명백한 latency/메모리 급증으로 이어질 수 있습니다. 아래 기능들을 중심으로 현재 코드 흐름을 살펴보고, 원인과 쿼리 재설계 방향을 정리했습니다.

## 1. 제품 목록 API (`ProductService.getProducts`)
- **현재 흐름**
  - `productRepository.findAll()`이 `products` 전체를 불러오고, 각 제품마다 `findItems(product.id)`를 호출해 `product_items`를 별도 쿼리로 추가 조회하는 구조 (`src/main/kotlin/io/joopang/services/product/infrastructure/ProductRepository.kt:26-115`).
  - 반환된 리스트를 애플리케이션에서 `categoryId`로 필터링하고, `productComparator(sort)`로 정렬한 뒤 캐시한다 (`src/main/kotlin/io/joopang/services/product/application/ProductService.kt:43-55`).
- **문제**
  - 제품 수가 늘면 `productRepository.findAll()`이 성공적으로 모든 행+옵션을 메모리로 올리고 정렬/필터를 수행하면서 GC/응답 시간이 급격히 증가합니다.
  - 또한 `findItems` 때문에 N+1 쿼리가 발생해 DB로 부하를 줍니다.
- **쿼리 재설계 제안**
  1. DB에서 필터/정렬/페이징을 수행하는 JPQL/native query로 교체.
     ```sql
     SELECT p.*, pi.*
     FROM products p
     LEFT JOIN product_items pi ON p.id = pi.product_id
     WHERE (:categoryId IS NULL OR p.category_id = :categoryId)
     ORDER BY
       CASE WHEN :sort = 'NEWEST' THEN p.created_at END DESC,
       CASE WHEN :sort = 'PRICE_ASC' THEN p.price_amount END ASC,
       CASE WHEN :sort = 'PRICE_DESC' THEN p.price_amount END DESC,
       CASE WHEN :sort = 'SALES' THEN p.sales_count END DESC
     LIMIT :limit OFFSET :offset
     ```
     정렬 조건을 명시적으로 CASE로 분기하면 `ProductSort` 파라미터에 따라 적절한 스캔/정렬을 유도할 수 있습니다.
  2. `ProductWithItems`를 반환하려면 `JOIN FETCH` 또는 `GROUP_CONCAT` 방식(aggregated JSON)을 활용해 단일 쿼리로 `product_items`를 묶는 것이 바람직합니다.
  3. 응답 캐싱은 유지하되 `limit/offset`과 `count`를 함께 관리해 캐시된 리스트가 실제 전체 제품과 동기화되도록 합니다.

## 2. 장바구니 조회 API (`CartService.getCart` / `buildView`)
- **현재 흐름**
  - 장바구니 아이템을 읽은 후 `items.map { it.productId }.distinct().associateWith { productRepository.findById(productId) }` 패턴으로 각 상품의 aggregate를 개별적으로 조회 (`src/main/kotlin/io/joopang/services/cart/application/CartService.kt:197-215`).
- **문제**
  - `productRepository.findById()`가 UUID별로 DB를 hit하면서 상품/옵션 정보를 N+1으로 가져오며, 한 사용자의 장바구니가 여러 상품을 포함할수록 DB 호출 수가 선형 팽창합니다.
  - 상대적으로 짧은 조회임에도 연결 수가 많은 트랜잭션 풀을 잠글 수 있습니다.
- **쿼리 재설계 제안**
  1. 권장: 하나의 IN 쿼리로 병합해서 `ProductWithItems`를 가져오는 새로운 Repository 메서드 도입.
     ```kotlin
     fun findProductsWithItemsByIds(productIds: List<UUID>): List<ProductWithItems>
     ```
     이때 JPQL `SELECT p FROM Product p LEFT JOIN FETCH p.items WHERE p.id IN :ids` 또는 native `JOIN`으로 해결.
  2. `CartService.buildView`는 `product_items`까지 조인하는 뷰 형태의 쿼리를 만들어 응답 직전까지 필요한 데이터를 가공하는 것이 이상적입니다.
  3. 재고/가격 검증에서 `ProductItem`을 반복 필터링하는 대신, `product_items` 데이터를 미리 메모리에 올려두고 참조하면 같은 커넥션을 재사용할 수 있습니다.

## 3. 주문 리스트 API (`OrderRepository.findAll`)
- **현재 흐름**
  - 단순히 `select o from Order o order by o.orderedAt`로 모든 주문을 가져온 뒤, 각 오더에 대해 `findItems(order.id)`와 `findDiscounts(order.id)`를 호출해 별도 쿼리로 항목과 할인 정보 집계 (`src/main/kotlin/io/joopang/services/order/infrastructure/OrderRepository.kt:43-95`).
- **문제**
  - `OrderService.listOrders()`가 운영 주문 전체를 조회할 경우, items/discounts를 order 수만큼 반복 조회하는 N+1이 되어 트랜잭션당 쿼리 수가 `1 + 2*N`으로 뻗습니다.
  - 페이징/제한 조건이 없어 테이블이 커질수록 응답 길이가 선형 증가하고, `entityManager.createQuery`가 가져오는 모든 행+연관 객체가 메모리에 적재됩니다.
- **쿼리 재설계 제안**
  1. `ORDER BY ordered_at`에도 `LIMIT`/`OFFSET`를 적용하고, `JOIN FETCH`로 items/discounts를 미리 함께 읽는 패턴으로 전환.
     ```jpql
     SELECT DISTINCT o FROM Order o
     LEFT JOIN FETCH o.items i
     LEFT JOIN FETCH o.discounts d
     ORDER BY o.orderedAt DESC
     ```
  2. `OrderAggregate`를 직접 구성하는 대신 DTO를 사용해 필요한 항목만 뽑거나, `@NamedEntityGraph`를 활용해 fetch 전략을 조정하는 것이 더 적합합니다.
  3. 대량 이력 조회 시에는 summary 테이블(예: `order_summaries`)을 두고 정기적으로 집계하여 단순 목록을 조회할 때는 summary만 참조하게 하면 원본 테이블 쿼리 부담을 줄일 수 있습니다.

## 마무리 및 검증
1. 위 재설계를 반영할 때는 JPQL/native 쿼리에 `EXPLAIN`을 적용해 인덱스를 활용한 점유율을 확인하고, Hibernate의 `hibernate.show_sql` 로그로 발생하는 쿼리 수를 모니터링하세요.
2. `ProductService.getProducts`와 `OrderService.listOrders()`는 캐시/페이징을 병행하므로, `limit`/`offset`이 없는 요청은 방어적으로 막거나 서비스단에서 최대 조회 행을 제한하는 검증을 추가하는 것이 안전합니다.
