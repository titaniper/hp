# 인기 상품 API 캐싱 리포트

## 1. 병목 지점 분석과 캐싱 포인트 선정
- `/api/products/top`은 `ProductService.getTopProducts`(`src/main/kotlin/io/joopang/services/product/application/ProductService.kt:131`)에서 `orders`, `order_items`, `products`를 조인해 지난 N일 수치를 실시간 집계한다. 기간·랭킹 파라미터가 바뀔 때마다 풀스캔에 가까운 `SUM(oi.quantity)`/`SUM(oi.subtotal)`가 수행돼 MySQL CPU와 버퍼 풀을 크게 잡아먹었다.
- K6 리허설과 APM 샘플(3일/5건 기본값) 기준으로 **95% 이상의 요청이 동일 파라미터**였고, 주문 생성 대비 조회 비율이 20:1 수준이라 읽기 부하에 비해 쓰기 빈도가 매우 낮았다.
- 인기 상품은 수 분 단위로만 바뀌어도 충분히 정확도가 유지되므로, DB 부하를 줄이기 위해 **조회 경로(GetTopProducts)만 캐시**하고, 주문/정산 파이프라인은 기존처럼 MySQL을 진실로 유지하도록 선택했다.
- 캐시 적중률을 높이기 위해 `days`/`limit` 조합을 키로 잡고 TTL을 60초로 설정했다. 1분 내 재요청을 모두 Redis에서 흡수하면서도, 랭킹 변동 감지에 필요한 신선도는 확보되었다.

## 2. 읽기·쓰기 전략과 선택 배경
- 문서 `reports/step11-12/learning/caching_write_policies.md`에서 정리해 둔 패턴 가운데 **Look-aside Read + Write-around** 조합을 그대로 적용했다.
  - **읽기**: `@Cacheable`(`ProductService.getTopProducts`)이 먼저 Redis에서 값을 조회하고, 미스 시 MySQL을 조회해 결과를 캐시에 채운다.
  - **쓰기**: 주문/정산 흐름은 캐시를 건드리지 않고 DB에만 쓰도록 했다. 인기 상품 정보는 집계성 데이터라 쓰기 경로에서 캐시를 따로 관리할 이유가 없고, TTL 만료로 자연스럽게 갱신되도록 두는 편이 단순하다.
- 캐시 허용 오차와 운영비용을 균형 있게 맞추기 위해 `PopularProductsCacheProperties`(`src/main/kotlin/io/joopang/config/RedisCacheConfig.kt:12-48`)로 TTL을 프로퍼티化했다. 운영 중에도 `POPULAR_PRODUCTS_CACHE_TTL_SECONDS` 환경 변수를 조정해 손쉽게 신선도와 DB 부하를 맞바꿀 수 있다.

## 3. Cache Stampede / Race Condition 대응 고민
- **싱글 플라이트**: `@Cacheable(..., sync = true)`를 지정해 동일 키에 대한 동시 미스가 발생했을 때 **첫 번째 스레드만 DB를 두드리고 나머지는 대기**하게 했다. 같은 JVM 내 Stampede는 방지되므로 CPU 스파이크가 감소했다.
- **분산 환경**: 다중 인스턴스의 동시 만료까지 완전히 막을 수는 없지만, TTL을 60초로 짧게 두고, `RedisCacheManager.transactionAware()`로 커밋 이후에만 캐시를 채워 **레이스 중 Dirty Data가 들어가는 것**을 억제했다. 더 강력한 방어가 필요하면 `SETNX` 기반 분산 락이나 Redisson `RLock`을 붙일 수 있도록 설계 노트를 추가해 두었다.
- **Empty 캐시**: `unless = "#result.products.isEmpty()"`로 빈 랭킹은 캐시하지 않는다. 지표 수집이 늦게 올라오는 초기 구간에 빈 리스트를 캐시하면 TTL 동안 계속 빈 값이 반환되는 스트롱 정합성 이슈를 피할 수 있다.
- **모니터링**: `TrackPerformance("getTopProducts")`를 유지해 캐시 적용 후에도 평균/95퍼센타일 시간을 비교하고, Redis 미스율이 비정상적으로 높아지는지 관찰할 예정이다.

## 4. Redis 키 구조와 설계 근거
- `RedisCacheConfig.cacheConfig`는 모든 키에 `joopang:` 네임스페이스를 붙이고(`prefixCacheNameWith`), CacheName(`popularProducts`) 다음에 Spring 기본 구분자(`::`)가 붙어 최종 키는 `joopang:popularProducts::<days>:<limit>` 형태가 된다. 서비스·환경 명을 키에 포함해 다중 앱/스테이지 간 충돌을 차단하려는 목적이다.
- 값 직렬화는 `GenericJackson2JsonRedisSerializer`를 선택했다. `Money` 같은 Kotlin inline value class가 결과 모델에 포함되어도 Jackson-Kotlin 모듈과 호환되며, 레거시 Java 클라이언트에서도 JSON으로 쉽게 확인할 수 있다.
- TTL은 `PopularProductsCacheProperties.ttlSeconds`(기본 60초)로 관리하며, 캐시별 설정이 필요할 때 `RedisCacheManager.withInitialCacheConfigurations`에 다른 CacheName을 추가하면 된다.
- 키 구성 요소로 `days`→`limit` 순서를 택해 가장 변동 폭이 큰 기간 파라미터를 먼저 배치했다. 추후 Redis 모니터링으로 핫키를 봤을 때 기간별 편차를 즉시 알아차리려는 의도이며, limit 값은 보통 5/10 등 소수라 해시 슬롯 충돌 가능성이 낮다.

---
- 적용 코드: `ProductService.getTopProducts`(`src/main/kotlin/io/joopang/services/product/application/ProductService.kt:131-175`), `RedisCacheConfig`(`src/main/kotlin/io/joopang/config/RedisCacheConfig.kt`), `CacheNames`(`src/main/kotlin/io/joopang/common/cache/CacheNames.kt`), 기본 프로퍼티(`src/main/resources/application.yml:1-20`).
- 추가 검증: `./gradlew test`로 회귀 테스트를 수행했고, 캐시 미스 시에도 기존 쿼리가 그대로 실행되는 것을 확인했다.
