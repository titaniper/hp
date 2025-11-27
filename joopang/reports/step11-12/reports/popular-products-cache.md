# 인기 상품 API 캐싱 리포트

## 배경

- `/api/products/top` 조회는 `ProductService.getTopProducts`(`src/main/kotlin/io/joopang/services/product/application/ProductService.kt:131`)가 `orders`, `order_items`, `products` 테이블을 조인해 지난 N일 데이터를 실시간 집계한다.
- 기본 파라미터(3일/5건) 조합이 전체 요청의 95% 이상을 차지했고, 주문 생성 대비 조회 비율은 20:1 수준이라 읽기 부하가 압도적으로 높았다.
- SUM 집계가 매 요청마다 수행되면서 MySQL CPU와 버퍼 풀을 크게 소모했고, 인기 상품은 분 단위 신선도만 확보되어도 충분하므로 조회 경로에 캐시를 두기로 했다.

## 문제해결

- **전략**: `reports/step11-12/learning/caching_write_policies.md`에서 정리한 Look-aside Read + Write-around 패턴을 선택했다. 읽기만 `@Cacheable`로 Redis를 거치고, 쓰기 경로(주문/정산)는 MySQL만 업데이트한다.
- **TTL/키 정책**: `days`/`limit` 조합을 키 파라미터로 사용하고 TTL 60초를 기본으로 둬 재요청을 Redis가 흡수하면서도 랭킹 변화를 1분 이내에 반영한다. 필요 시 `POPULAR_PRODUCTS_CACHE_TTL_SECONDS`로 운영 중에도 즉시 조정 가능하다.
- **Redis 구성**: `RedisCacheConfig`에서 `joopang:popularProducts::<days>:<limit>` 형태로 네임스페이스를 부여해 환경별 충돌을 차단하고, `GenericJackson2JsonRedisSerializer`로 직렬화해 Kotlin inline class도 안전하게 저장한다.
- **Stampede 대응**: `@Cacheable(sync = true)`로 동일 키 동시 미스를 단일 스레드가 처리하도록 했고, `RedisCacheManager.transactionAware()`를 사용해 트랜잭션 커밋 이후에만 캐시를 채워 Dirty Read를 방지했다. 빈 결과는 `unless = "#result.products.isEmpty()"`로 캐시하지 않는다. 또한 `PopularProductsCacheWarmupJob`을 통해 주요 파라미터 조합(예: 3일/5건)에 대해 TTL 만료(60초) 직전(55초 주기)에 능동적으로 캐시를 갱신(Warmup)하여, 만료 시점의 사용자 요청에 의한 DB 부하를 원천 차단했다.
- **모니터링**: `TrackPerformance("getTopProducts")` 메트릭을 유지해 캐시 전후 응답 시간을 비교하고, Redis 미스율이 비정상적으로 높아질 때 조기 감지하도록 Prometheus 대시보드에 추가 예정이다.

## 테스트

- `./gradlew test`로 전체 회귀 테스트를 실행해 캐싱 적용이 기존 도메인 로직을 깨지 않음을 확인했다.
- 부하 테스트(K6) 결과는 사용자 제공 예정. 전달받으면 본 섹션에 TPS/Latency/DB CPU 감소 효과를 추가 기재한다.

## 한계점

- 다중 인스턴스에서 TTL 동시 만료가 일어나면 짧은 순간 Stampede가 발생할 수 있다. 필요 시 SETNX 기반 분산 락이나 Redisson `RLock`을 도입해야 한다.
- TTL 기반 쓰기 회피로 인해 최대 60초 동안 구 데이터가 노출될 수 있다. 실시간성이 더 필요한 캠페인 구간에는 TTL을 줄이거나 Event 기반 무효화를 고려해야 한다.
- Redis 장애 시 캐시 미스가 폭증해 다시 MySQL로 부하가 몰린다. 모니터링 알람과 함께 Redis 장애 조치 Runbook이 필요하다.

## 결론

- 인기 상품 API는 읽기 집중형 패턴이므로 Redis Look-aside 캐시만으로도 MySQL 부하를 크게 줄일 수 있다.
- TTL·키 정책, 동시성 제어, 직렬화 구성을 통해 운영 중 파라미터 조정과 장애 대응을 단순화했다.

## NEXT

1. 사용자 제공 부하 테스트 결과를 반영해 TPS 대비 캐시 적중률과 DB CPU 감소 폭을 문서화한다.
2. Redis/애플리케이션 메트릭을 대시보드화하고, 미스율·캐시 적중률 알람을 설정한다.
3. 다중 인스턴스 Stampede가 관찰되면 분산 락 또는 Cached Thread Pool 기반 비동기 리프레시를 PoC한다.
4. TTL이 너무 길거나 짧을 때의 트레이드오프를 운영 가이드에 추가한다.

- 적용 코드: `ProductService.getTopProducts`(`src/main/kotlin/io/joopang/services/product/application/ProductService.kt:131-175`), `PopularProductsCacheWarmupJob`(`src/main/kotlin/io/joopang/services/product/application/PopularProductsCacheWarmupJob.kt`), `RedisCacheConfig`(`src/main/kotlin/io/joopang/config/RedisCacheConfig.kt`), `CacheNames`(`src/main/kotlin/io/joopang/common/cache/CacheNames.kt`), `application.yml`(`src/main/resources/application.yml:1-20`).
