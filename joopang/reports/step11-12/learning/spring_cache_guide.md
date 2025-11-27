# Spring Cache 실무 가이드

캐싱이 필요한 대부분의 서비스는 Spring Cache 추상화를 이용하면 일관된 방식으로 Redis/Caffeine/Ehcache 등 다양한 구현체를 교체하면서도 동일한 애노테이션 기반 API를 사용할 수 있다. 이 문서는 `@Cacheable`을 중심으로 동작 원리와 옵션, 함께 쓰는 기타 애노테이션, 그리고 실전 예시를 정리한다.

## 1. Spring Cache 추상화 개요
- **구성 요소**
  - `CacheManager`: 캐시 저장소를 생성·관리하는 팩토리. (예: `RedisCacheManager`, `CaffeineCacheManager`)
  - `Cache`: 실제로 key/value를 저장하는 객체. `CacheManager`에서 캐시 이름으로 꺼낸다.
  - `CacheResolver`: 메서드 수준에서 어떤 캐시를 사용할지 동적으로 결정할 때 사용.
- **접근 방식**
  1. **선언형(Declarative)**: `@Cacheable`, `@CachePut`, `@CacheEvict`, `@Caching` 애노테이션으로 AOP 프록시가 캐시 로직을 자동으로 감싼다.
  2. **명령형(Programmatic)**: 서비스 코드에서 `CacheManager.getCache("...")` 후 직접 `get/put/evict`를 호출한다. 복잡한 흐름이나 분기마다 다른 캐시를 쓰고 싶을 때 사용.

## 2. `@Cacheable` 옵션 상세
기본 문법은 `@Cacheable(cacheNames = ["products"], key = "#id")`. 주요 속성은 아래와 같다.

| 속성 | 설명 | 실전 팁 |
| --- | --- | --- |
| `cacheNames`/`value` | 사용할 캐시 이름. 배열 형태로 다중 캐시 지정 가능. | 공통 문자열은 `CacheNames` 객체로 상수화하면 유지보수 편함. |
| `key` | SpEL 기반으로 키 생성. 기본값은 모든 파라미터를 조합한 `SimpleKey`. | 파라미터가 많다면 `#root.methodName + ':' + #param` 등 명확히 지정. |
| `keyGenerator` | `org.springframework.cache.interceptor.KeyGenerator` 구현체를 Bean으로 등록해 키 생성 로직을 재사용. | 멀티 파라미터를 해시로 묶거나 복잡한 도메인 키를 만들어야 할 때 활용. |
| `cacheManager` | 특정 `CacheManager` 빈 이름을 지정해 다중 캐시 매니저 환경에서 어떤 것을 쓸지 강제. | 예: `@Cacheable(cacheManager = "redisCacheManager", ...)`. |
| `cacheResolver` | 캐시 선택 로직을 커스터마이즈. `CacheManager`보다 우선순위가 높다. | 멀티 테넌트에서 테넌트별 캐시 분기 등. |
| `condition` | 메서드 실행 전 평가. `true`일 때만 캐시를 사용한다. | 예: `condition = "#days <= 30"` → 장기간 요청은 항상 DB 조회. |
| `unless` | 메서드 실행 후 평가. `true`면 결과를 캐시에 저장하지 않는다. | 예: `unless = "#result == null"` | 
| `sync` | `true`면 같은 키에 대한 동시 미스 시 하나의 쓰레드만 메서드를 실행하도록 보호. | Cache Stampede 완화에 활용. |
| `unless = "#result.products.isEmpty()"`와 같이 `#result` 접근 가능. JSON 직렬화 시 null이 캐시되지 않도록 하려면 `disableCachingNullValues()` 설정도 병행한다. 단, `sync = true`를 사용하는 경우에는 `@CacheEvict`, `@CachePut` 등 다른 캐시 애노테이션을 같은 메서드에 함께 붙일 수 없으므로, 필요 시 코드에서 직접 `CacheManager`를 통해 `evict`를 호출해야 한다.

### 2.1 실제 적용 예시
```kotlin
@TrackPerformance("getTopProducts")
@Cacheable(
    cacheNames = [CacheNames.POPULAR_PRODUCTS],
    key = "#days + ':' + #limit",
    unless = "#result.products.isEmpty()",
    sync = true,
)
fun getTopProducts(days: Long = 3, limit: Int = 5): TopProductsOutput { ... }
```
- 캐시 키는 `3:5`처럼 단순 조합으로 만들어 Redis 키(`joopang:popularProducts::3:5`) 충돌을 피했다.
- 빈 결과는 캐시하지 않아 데이터 수집 지연 시 빈 리스트가 고착되는 문제를 없앴다.
- `sync = true`로 다중 요청이 한꺼번에 DB를 때리는 것을 막았다.

## 3. 기타 캐시 애노테이션
- `@CachePut`
  - 메서드 실행 결과를 **항상 캐시에 갱신**하고 반환한다. 읽기는 캐시able로 처리하되, 업데이트 시에는 최신 데이터를 캐시에 즉시 반영해야 할 때 사용.
  - 예시: 상품 상세를 수정한 직후 뷰 캐시도 최신화.
  ```kotlin
  @CachePut(cacheNames = [CacheNames.PRODUCT_DETAIL], key = "#product.id")
  fun updateProduct(product: Product): Product = repository.save(product)
  ```
- `@CacheEvict`
  - 캐시 무효화. `allEntries = true`로 캐시 이름에 해당하는 모든 키를 삭제할 수 있고, 트랜잭션 종료 후에 지우려면 `beforeInvocation = false`(기본) 유지.
  ```kotlin
  @CacheEvict(cacheNames = [CacheNames.PRODUCT_LIST], key = "#categoryId + ':' + #sort")
  fun invalidateProductList(categoryId: Long?, sort: ProductSort) { ... }
  ```
- `@Caching`
  - 여러 캐시 애노테이션을 묶어서 선언. 예를 들어 한 메서드에서 `@Cacheable`과 `@CacheEvict`를 동시에 사용해야 할 때.
  ```kotlin
  @Caching(
      put = [CachePut(cacheNames = [CacheNames.PRODUCT_DETAIL], key = "#result.id")],
      evict = [CacheEvict(cacheNames = [CacheNames.PRODUCT_LIST], allEntries = true)]
  )
  fun publishProduct(cmd: PublishProductCommand): ProductDto { ... }
  ```
- `@CacheConfig`
  - 클래스 수준에서 `cacheNames`, `cacheManager`, `keyGenerator` 기본값을 정해 중복을 줄인다.
  ```kotlin
  @CacheConfig(cacheNames = [CacheNames.PRODUCT_DETAIL])
  class ProductQueryService { ... }
  ```

## 4. CacheManager & 구현 방식
### 4.1 RedisCacheManager 예시
```kotlin
@EnableCaching
@Configuration
class RedisCacheConfig {
    @Bean
    fun redisCacheManager(
        factory: RedisConnectionFactory,
        props: PopularProductsCacheProperties,
    ): RedisCacheManager {
        val popular = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofSeconds(props.ttlSeconds))
            .prefixCacheNameWith("joopang:")
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(GenericJackson2JsonRedisSerializer()),
            )

        return RedisCacheManager.builder(factory)
            .cacheDefaults(popular)
            .transactionAware()
            .build()
    }
}
```
- TTL, 키 프리픽스, 직렬화 전략을 캐시별로 설정할 수 있다.
- `transactionAware()`를 켜면 트랜잭션이 커밋된 이후에만 캐시가 갱신돼 Dirty Read가 줄어든다.

### 4.2 로컬 캐시 (Caffeine)
```kotlin
@Bean
fun caffeineCacheManager(): CaffeineCacheManager = CaffeineCacheManager().apply {
    setCaffeine(
        Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .maximumSize(10_000)
    )
}
```
- 서버 내부 메모리를 쓰므로 초저지연이 필요할 때 적합하지만, 다중 인스턴스 환경에서는 데이터 불일치를 감안해야 한다.
- 고정된 설정으로 운영하기보다, 모니터링을 통해 `maximumSize`, `expireAfterWrite` 등을 지속적으로 조정한다.

### 4.3 직접 제어 (Programmatic)
```kotlin
fun getOrLoadProduct(id: Long): ProductDto {
    val cache = cacheManager.getCache(CacheNames.PRODUCT_DETAIL) ?: return loadProduct(id)
    val cached = cache.get(id, ProductDto::class.java)
    if (cached != null) return cached

    val loaded = loadProduct(id)
    cache.put(id, loaded)
    return loaded
}
```
- 복잡한 플로우(예: 캐시 값에 따라 분기, 멀티 캐시 동시 업데이트)가 필요하면 명령형 접근이 더 명확할 때가 있다.

## 5. 고급 사용 시나리오
### 5.1 조건부 캐싱
- 요구사항: 30일 이상 기간을 조회할 때는 항상 DB에서 최신 데이터를 가져온다.
```kotlin
@Cacheable(cacheNames = [CacheNames.POPULAR_PRODUCTS], key = "#days + ':' + #limit", condition = "#days <= 30")
fun getTopProducts(days: Long, limit: Int): TopProductsOutput = ...
```
- 조건은 메서드 실행 전 평가되므로 `condition`이 false면 캐시는 완전히 무시된다.

### 5.2 Cache Stampede 완화 (sync + 분산 락)
- 단일 JVM에서 `sync = true`로 동시성 제어가 충분하지 않을 경우 Redis 분산 락(예: Redisson `RLock`)이나 `SETNX` + TTL을 함께 사용해 다중 인스턴스 Stampede를 막는다.
```kotlin
val lock = redissonClient.getLock("popular:lock:$days:$limit")
if (lock.tryLock(100, 1000, TimeUnit.MILLISECONDS)) {
    try {
        // 캐시 재생성
    } finally {
        lock.unlock()
    }
}
```

### 5.3 TTL 기반 캐시 재활용
- Redis를 사용할 때는 캐시 이름 기반으로 다른 TTL을 적용해 읽기 패턴에 맞춘다. 인기 상품처럼 짧은 TTL이 필요한 데이터와, 정책/카테고리 목록처럼 긴 TTL이 가능한 데이터를 `withInitialCacheConfigurations`로 분리한다.

## 6. 테스트 전략
- **통합 테스트**: `@SpringBootTest`에 `@DirtiesContext`를 붙여 캐시 상태를 초기화하거나, 메서드 호출 시 `CacheManager.getCache`로 직접 값을 읽어 검증한다.
- **단위 테스트**: `ConcurrentMapCacheManager` 같은 인메모리 CacheManager를 테스트 컨피그로 주입하면 Redis 의존 없이도 캐시 동작을 검증할 수 있다.
```kotlin
@Test
fun `popular products cached`() {
    // given
    val cache = cacheManager.getCache(CacheNames.POPULAR_PRODUCTS)!!.clear()

    // when
    productService.getTopProducts(3, 5)
    productService.getTopProducts(3, 5)

    // then
    val cached = cache.get("3:5", TopProductsOutput::class.java)
    assertThat(cached).isNotNull
}
```

## 7. 운영 체크리스트
1. **메트릭**: 캐시 적중률(hit/miss), 평균 로드 시간, 캐시 용량을 Prometheus/Micrometer로 노출.
2. **장애 대응**: Redis 장애 시 fallback 전략을 명확히(예: 캐시 무시 후 DB 조회, 타임아웃 제한).
3. **키 정책**: 네임스페이스(`서비스명:캐시명::비즈키`)로 충돌 방지, 민감 정보가 키에 포함되지 않도록 주의.
4. **TTL 관리**: 실시간성이 필요한 API일수록 짧은 TTL, 그렇지 않으면 장기 TTL + 적기 갱신 전략 조합.
5. **Evict 자동화**: 배치나 관리자 툴에서 캐시 무효화 버튼을 제공하면 운영자의 수동 조작을 줄일 수 있다.

이 가이드를 토대로 서비스 특성에 맞는 CacheManager 구현과 애노테이션 조합을 선택하면 Spring 기반 애플리케이션에서 캐시 일관성과 성능을 동시에 확보할 수 있다.
