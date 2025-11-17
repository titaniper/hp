# 인기 상품 집계 N+1 문제 분석 리포트

_작성일: 2025-11-13_

## 현상
- `ProductService.getTopProducts()`는 native SQL로 `products`, `order_items`, `orders`를 조인해 인기 상품 ID/매출을 집계한 뒤, 매행마다 `productRepository.findById(productId)`를 호출하여 `ProductWithItems`를 다시 로딩합니다 (`src/main/kotlin/io/joopang/services/product/application/ProductService.kt:140-159`, `src/main/kotlin/io/joopang/services/product/infrastructure/ProductRepository.kt:47-115`).
- `findById()` 내부에서 `Product`를 조회하고 별도의 `findItems(productId)`를 실행하므로, 인기 상품 개수(limit)만큼 `product` + `product_items` 쿼리가 반복되어 N+1 문제가 발생합니다.
- 제한(default `limit=5`)이 작지만, `days`/`limit` 파라미터를 확장하거나 동시 요청이 많아질 때 DB 연결/응답 시간이 선형 증가합니다.

## 원인
- 인기 상품 집계 자체는 native query 한 번으로 처리되지만, 결과를 풍부한 `ProductWithItems`로 채우기 위해 다시 `findById` 체인을 타는 구조가 문제입니다.
- `findById`는 각 상품마다 `product_items`까지 중복으로 읽기 때문에, 실제로는 `limit` × 2개의 추가 쿼리를 발생시킵니다.

## 개선 방안
1. `ProductRepository`에 추가한 `findProductsByIds(ids)`를 재사용하여 인기 상품 ID 목록을 한 번에 로딩하고, `product_items`도 IN 쿼리로 묶어 가져옵니다.
2. `getTopProducts` 내부에서 `rows.mapIndexed` 전에 `aggregatesById`를 구성하여 N+1을 제거합니다.
3. 향후 인기 상품 API가 더 많은 필드를 요구한다면 DTO/뷰(`SELECT ... WITH JSON_ARRAYAGG`) 형태로 native query 한 번에 모든 필드를 가져오거나, 서버 캐시(예: `SortedSet` 캐시)와 조합해 험한 조회를 줄입니다.

## 검증
- 현재 코드에 개선을 적용해 `ProductService.getTopProducts()`가 top N 상품을 조회할 때 `findProductsByIds` 1회 실행으로 끝나도록 했으며, `EXPLAIN` 결과에서 `products`/`product_items`에 대한 IN 필터가 하나의 쿼리로 처리됨을 기대합니다 (`src/main/kotlin/io/joopang/services/product/application/ProductService.kt:137-159`).
