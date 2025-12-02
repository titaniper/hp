# STEP 13-14 시스템 디자인 & 회고 리포트

## 개요
- `reports/step13-14/leaning/spec.md` 요구사항에 따라 랭킹 시스템(STEP 13)과 비동기 쿠폰 발급(STEP 14)을 Redis 기반으로 재설계했다.
- 각각의 세부 설계 내용은 `redis-product-ranking.md`와 `redis-first-come-coupon.md`에 정리되어 있으며, 본 문서는 두 설계의 구현 범위, 테스트 계획, 잔여 과제를 통합 관점에서 정리한다.

## STEP 13: Ranking Design (이커머스 시나리오)
### 설계 결과 요약
- 주문 확정 시점(`OrderService.processPayment`, `src/main/kotlin/io/joopang/services/order/application/OrderService.kt:118-206`)에 Redis Sorted Set을 갱신해 실시간 랭킹을 구성한다.
- 랭킹 키는 `product:rank:{metric}:{window}` 패턴을 사용하며, `sales`/`revenue`/`views` 지표를 TTL 기반 슬라이딩 윈도우로 분리한다.
- 복수 지표 결합은 `ZINTERSTORE` 또는 Lua 가중치 계산을 통해 사전 계산된 키로 제공한다.

### 구현 반영 포인트
- `ProductService.getTopProducts`(`src/main/kotlin/io/joopang/services/product/application/ProductService.kt:136-211`)가 DB 조인 대신 Redis Sorted Set을 우선 조회하고, 부족 시 DB fallback을 수행하도록 리팩터링 예정.
- `PopularProductsCacheWarmupJob`(`src/main/kotlin/io/joopang/services/product/application/PopularProductsCacheWarmupJob.kt`)은 Redis 랭킹을 강제 조회하여 캐시를 사전 워밍하는 역할로 전환한다.

### 테스트 계획
- Testcontainers Redis를 이용해 Sorted Set 윈도우 TTL + `ZREMRANGEBYSCORE` 만료 로직을 검증.
- `k6/scenarios/popular-products.js`를 수정해 초당 수천 건의 `ZINCRBY` 적재 시 레이턴시와 Redis CPU를 측정한다.

## STEP 14: Asynchronous Design (이커머스 선착순 쿠폰)
### 설계 결과 요약
- Lua 스크립트 + Sorted Set 대기열 + Set 중복 방지 + Stream 후속 처리를 조합해 분산락 없이 선착순/중복 방지 요구사항을 만족한다.
- `coupon:queue:{templateId}` Sorted Set, `coupon:issued:{templateId}` Set, `coupon:stock:{templateId}` String, `coupon:issue-stream` Stream을 핵심 키로 사용한다.
- API는 Lua 스크립트를 통해 원자적으로 큐 등록 및 재고 차감을 실행하고, 비동기 워커가 Stream 메시지를 소비해 RDB에 최종 확정 저장을 수행한다.

### 구현 반영 포인트
- `CouponService.issueCoupon`(`src/main/kotlin/io/joopang/services/coupon/application/CouponService.kt:26-84`)에서 Redisson 분산락을 제거하고 RedisTemplate/Redisson Script 실행으로 교체한다.
- 발급 성공/실패 이벤트는 `CouponIssueCoordinator`(신규)에서 변환 후 `couponRepository.save`를 호출하는 컨슈머로 전달한다.
- 대기열/좌석 등 spec에 언급된 시나리오에도 동일 패턴을 적용할 수 있도록 키 네임스페이스와 Lua 스크립트를 모듈화한다.

### 테스트 계획
- Embedded Redis or Testcontainers 기반 통합 테스트로 `SOLD_OUT`, `DUPLICATE`, `QUEUED` 경로를 검증한다.
- Stream 컨슈머가 장애 후 재시작했을 때 `PEL` 재처리가 정상적으로 작동하는지 시뮬레이션한다.
- `k6/scenarios/coupon-issue.js` 부하 테스트를 Lua 기반 API로 갱신해 RPS/대기열 길이를 관찰한다.

## 통합 체크리스트 진행 상황
- [x] 랭킹 기능 설계 및 자료구조 선정(`redis-product-ranking.md`).
- [x] 선착순 쿠폰 비동기 설계 및 자료구조 선정(`redis-first-come-coupon.md`).
- [ ] Redis Testcontainer 통합 테스트 작성 (Sorted Set/Lua/Stream 경로).
- [ ] 실서비스 수준 부하 테스트(k6) 결과 정리.

## 간단 회고 (3줄)
- **잘한 점**: Redis Sorted Set/Stream을 활용한 핵심 시나리오(랭킹, 선착순) 설계를 문서화하고, 기존 코드 경로(ProductService, CouponService)와 연결 지점을 명확히 찾았다.
- **어려운 점**: Lua 스크립트와 Stream 컨슈머 운용 시 장애 대응, 모니터링 기준(PEL, replication lag 등)을 정의하는 데 실운영 경험이 부족해 확신이 낮았다.
- **다음 시도**: Redis Testcontainer 기반 자동화 테스트를 작성하고, k6 부하 테스트로 Redis와 RDB 경계를 계측해 설계 적합성을 수치로 검증한다.

## NEXT
1. Redis 기반 랭킹/쿠폰 모듈의 실제 구현 코드(Repository/Service)를 작성하고, 기존 서비스에 주입한다.
2. Testcontainers Redis로 통합 테스트를 구성해 Sorted Set, Lua, Stream 흐름을 재현한다.
3. 모니터링/알람 체계를 문서화하고, 운영 대시보드 지표(issued set cardinality, stream lag 등)를 정의한다.
4. `reports/step13-14/leaning/retrospec.md`에 KPT 방식 회고를 확장해 추후 평가 자료로 활용한다.
