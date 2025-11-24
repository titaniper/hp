# 캐싱 전략 및 설계 가이드 (Caching Strategies & Design Guide)

## 1. 캐시 개요 (Caching Overview)

### 1.1 DB 조회 부하와 캐시의 필요성

데이터베이스(DB)는 디스크 I/O를 수반하므로 메모리 접근에 비해 속도가 현저히 느립니다. 트래픽이 증가하면 DB 부하가 병목이 되어 전체 시스템 성능을 저하시킬 수 있습니다. 캐시는 자주 사용되는 데이터를 고속의 메모리 영역에 저장하여 DB 부하를 줄이고 응답 속도를 획기적으로 개선하는 기술입니다.

### 1.2 언제 캐시를 사용해야 하는가?

* **반복적인 조회**: 동일한 데이터에 대한 요청이 빈번할 때 (예: 인기 상품, 카테고리 목록, 설정 정보).
* **변경이 적은 데이터**: 데이터가 자주 바뀌지 않아 캐시 갱신/무효화 비용보다 조회 이득이 클 때.
* **높은 읽기 비용**: 복잡한 쿼리(Join, Group By)나 연산이 필요한 데이터를 미리 계산해둘 때.
* **허용 가능한 데이터 불일치**: 실시간성이 조금 떨어져도 비즈니스에 치명적이지 않은 경우 (예: 조회수, 좋아요 수).

## 2. 캐시 유형 비교: Memory Cache vs External Cache

### 2.1 Local Cache (Memory Cache)

애플리케이션 프로세스 내부 메모리(Heap 등)에 데이터를 저장하는 방식입니다. (예: Ehcache, Caffeine, ConcurrentHashMap)

* **장점**:
  * **신속성 (Speed)**: 인스턴스의 메모리에 캐시 데이터를 저장하므로 속도가 가장 빠름 (Microseconds).
  * **저비용 (Low Cost)**: 별도의 네트워크 비용이 발생하지 않음.
* **단점**:
  * **휘발성 (Volatility)**: 애플리케이션이 종료되면 캐시 데이터가 삭제됨.
  * **메모리 부족 (Memory Shortage)**: 활성화된 애플리케이션 인스턴스에 데이터를 올리므로, 과도한 캐싱은 OOM(Out Of Memory) 및 비정상 종료를 유발할 수 있음.
  * **분산 환경 문제 (Consistency Issue)**: 분산 환경(Multi-Instance)에서 각 인스턴스 간 데이터 불일치 문제가 발생할 수 있음.

### 2.2 External Cache (Global Cache / Redis)

별도의 캐시 서버(Redis, Memcached)를 두고 데이터를 저장하는 방식입니다.

* **장점**:
  * **일관성 (Consistency)**: 별도의 담당 서비스를 둠으로써 분산 환경에서도 모든 인스턴스가 동일한 데이터를 공유할 수 있음.
  * **안정성 (Stability)**: 외부 캐시 서비스(Redis 등)의 Disk 스냅샷(RDB, AOF) 기능을 통해 장애 발생 시 복구가 용이함.
  * **고가용성 (High Availability)**: 애플리케이션 인스턴스에 의존하지 않으므로, Sentinel이나 Cluster 등을 통해 HA 구성이 용이함.
* **단점**:
  * **고비용 (High Cost)**: 네트워크 통신을 통해 외부 서비스와 소통해야 하므로 네트워크 지연(Latency) 및 비용을 고려해야 함.

### 2.3 비교 요약

| 구분 | Local Cache | External Cache (Redis) |
| :--- | :--- | :--- |
| **속도** | 매우 빠름 (메모리 접근) | 빠름 (네트워크 접근) |
| **데이터 공유** | 불가능 (서버별 독립) | 가능 (모든 서버 공유) |
| **일관성** | 유지 어려움 (분산 환경 이슈) | 유지 용이 |
| **용량** | 작음 (Heap 메모리 의존) | 큼 (별도 서버 메모리) |

## 3. 캐시 패턴 (Caching Patterns)

### 3.1 읽기 전략 (Read Strategies)

두 전략의 가장 큰 차이점은 **"누가 DB 데이터를 채워 넣는가?"** 입니다.

* **Cache-Aside (Look Aside)**: **애플리케이션(Application)**이 주도합니다.
  * **개념**: 애플리케이션이 캐시와 DB를 각각 직접 찌르는 구조입니다. 캐시는 DB에 대해 전혀 모릅니다.
  * **흐름**:
    1. 앱이 캐시에 데이터 요청.
    2. (Miss) 앱이 직접 DB에 쿼리 날려서 데이터 획득.
    3. 앱이 획득한 데이터를 캐시에 저장(`set`).
    4. 앱이 데이터를 반환.
  * **장점**: 가장 유연합니다. 캐시가 죽어도 앱은 DB에서 데이터를 가져올 수 있어 서비스가 중단되지 않습니다.
  * **쇼핑몰 예시**: **상품 상세 정보 조회**. 개발자가 직접 `redisTemplate.get()`을 호출하고, 없으면 `repository.findById()`를 호출한 뒤 `redisTemplate.set()`을 하는 코드를 작성합니다.
* **Read-Through**: **캐시(Cache Provider/Library)**가 주도합니다.
  * **개념**: 애플리케이션은 캐시만 바라봅니다. 데이터가 없으면 캐시 솔루션(또는 라이브러리)이 알아서 DB에서 가져옵니다.
  * **흐름**:
    1. 앱이 캐시에 데이터 요청.
    2. (Miss) **캐시 라이브러리**가 내부적으로 DB를 조회.
    3. **캐시 라이브러리**가 데이터를 캐시에 저장.
    4. 캐시 라이브러리가 앱에 데이터 반환.
  * **장점**: 애플리케이션 코드가 매우 깔끔해집니다. (데이터 조회 로직이 캐시 설정으로 위임됨)
  * **쇼핑몰 예시**: **Ehcache의 CacheLoader**, **Caffeine의 LoadingCache**. 캐시에 데이터가 없으면 미리 등록한 Loader 함수가 자동으로 DB를 조회하고 캐시에 저장합니다.
  * **주의**: Spring Cache의 `@Cacheable`은 **엄밀히 말하면 Look-Aside 패턴**입니다. AOP가 캐시를 먼저 확인하고, 없으면 메서드를 실행(DB 조회)한 후 결과를 캐시에 저장하는 방식이므로, 애플리케이션이 캐시와 DB를 모두 제어하는 Look-Aside와 동일합니다. 다만 코드 레벨에서는 캐시만 바라보는 것처럼 보이므로 Read-Through처럼 느껴질 수 있습니다.

* **Refresh-Ahead (Proactive Refresh)**:
  * **개념**: 캐시가 만료되기 전에 미리 백그라운드에서 데이터를 갱신하는 방식입니다.
  * **흐름**:
    1. 캐시에 데이터가 있고 TTL이 남아있음.
    2. TTL의 일정 비율(예: 80%)이 지나면, 백그라운드 스레드가 자동으로 DB를 조회하여 캐시를 갱신.
    3. 사용자 요청은 기존 캐시 데이터를 즉시 반환 (갱신 대기 불필요).
  * **장점**: 사용자는 항상 최신 데이터를 받으면서도 대기 시간이 없음. Cache Stampede 방지에도 효과적.
  * **단점**: 백그라운드 갱신으로 인한 추가 DB 부하 발생 가능.
  * **쇼핑몰 예시**: **인기 상품 랭킹**. 60초 TTL 중 48초가 지나면 자동으로 랭킹을 갱신하여, 만료 시점에 DB 부하가 몰리는 것을 방지합니다.

### 3.2 쓰기 전략 (Write Strategies)

* **Write-Through (캐시를 써서 통과한다) **:

  * **동작**: 캐시와 DB에 동시에 씀.
  * **특징**: 데이터 일관성 보장. 쓰기 지연 발생. 일반적으로 **No Write Allocate** 정책과 함께 사용됨.
  * **쇼핑몰 예시**: **재고(Stock) 감소, 주문 상태 변경**. 재고는 데이터 정합성이 생명이므로, 캐시와 DB가 항상 일치해야 합니다. 주문 완료 시점에 DB와 캐시를 동시에 업데이트합니다.
* **Write-Back (Write Behind, 나중에 다시(back) DB에 써둔다)**:
  * **동작**: 캐시에만 먼저 쓰고, 일정 주기/조건에 따라 DB에 비동기 반영.
  * **특징**: 쓰기 성능 극대화. 캐시 장애 시 데이터 유실 위험. (로그, 조회수 등에 적합). 일반적으로 **Write Allocate** 정책과 함께 사용됨.
  * **쇼핑몰 예시**: **상품 조회수 증가, 좋아요 수**. 사용자가 상품을 볼 때마다 DB를 업데이트하면 부하가 큽니다. Redis에서 카운트를 증가시키고, 5분마다 DB에 한 번씩 반영합니다.
* **Write-Around (둘러서 간다) **:
  * **동작**: DB에만 쓰고 캐시에는 쓰지 않음. (읽기 시점에 Cache Miss로 로드됨)
  * **특징**: 자주 읽히지 않는 데이터가 캐시를 점유하는 것을 방지.
  * **쇼핑몰 예시**: **CS 문의글 작성, 로그 저장**. 사용자가 문의글을 작성했을 때, 관리자가 바로 읽지 않을 수도 있습니다. 굳이 작성 시점에 캐시에 넣지 않고, 관리자가 조회를 요청할 때(Read Miss) 캐싱합니다.

### 3.3 Write Miss 정책 (Write Miss Policies)

Write 작업 시 데이터가 캐시에 없는 경우(Write Miss)에 대한 처리 정책입니다.

* **Write Allocate (Fetch on Write)**:
  * **동작**: Write Miss 발생 시, 해당 데이터 블록을 캐시로 로드한 뒤 Write를 수행합니다. 이후의 작업은 Read Miss와 유사하게 처리됩니다.
  * **조합**: 주로 **Write-Back** 정책과 함께 사용됩니다.
  * **쇼핑몰 예시**: **장바구니 수량 변경 (Write-Back 사용 시)**. 사용자가 장바구니 수량을 변경하려는데 캐시에 장바구니 정보가 없다면, DB에서 장바구니 정보를 가져와 캐시에 올린 후 수량을 변경합니다. 이후 변경 사항은 캐시에만 쌓이다가 나중에 DB에 반영됩니다.
* **No Write Allocate (Don't Fetch on Write)**:
  * **동작**: Write Miss 발생 시, 캐시에 로드하지 않고 메인 메모리(DB)에만 직접 씁니다. 데이터는 Read Miss가 발생할 때만 캐시에 로드됩니다.
  * **조합**: 주로 **Write-Through** 정책과 함께 사용됩니다. (Write-Through는 어차피 DB에 쓰므로 굳이 캐시에 로드할 필요가 없을 수 있음)
  * **쇼핑몰 예시**: **관리자 페이지의 상품 수정 (Write-Through 사용 시)**. 관리자가 잘 팔리지 않는(캐시에 없는) 상품의 설명을 수정했습니다. 굳이 캐시에 올릴 필요 없이 DB만 업데이트합니다. 나중에 고객이 조회하면 그때 캐시에 로드됩니다.

## 4. 캐시 운영 및 설계 이슈

### 4.1 Expiration vs Eviction

* **Expiration (만료)**: TTL(Time To Live)을 설정하여 특정 시간이 지나면 데이터가 삭제되도록 함. 데이터의 신선도(Freshness) 유지 목적.
  * **쇼핑몰 예시**: **타임 세일 가격 정보 (이벤트 종료 시 만료)**, **비회원 장바구니 (3일 후 만료)**, **SMS 인증 번호 (3분)**. 유효 기간이 명확한 데이터.
* **Eviction (축출)**: 메모리가 가득 찼을 때 공간 확보를 위해 데이터를 삭제하는 정책.
  * **LRU (Least Recently Used)**: 가장 오랫동안 사용되지 않은 데이터 삭제.
    * **쇼핑몰 예시**: **최근 본 상품 리스트**. 메모리가 부족하면 사용자가 가장 오래전에 본 상품 정보부터 캐시에서 제거합니다.
  * **LFU (Least Frequently Used)**: 사용 빈도가 가장 낮은 데이터 삭제.
    * **쇼핑몰 예시**: **베스트셀러 랭킹 캐시**. 가끔 팔리는 상품보다는 꾸준히 자주 팔리는 상품 데이터를 캐시에 남겨두는 것이 유리합니다.

### 4.2 Cache Stampede (Thundering Herd)

* **문제**: 인기 있는 키(Key)가 만료되는 순간, 수많은 요청이 동시에 DB로 몰려 부하를 일으키는 현상.
* **쇼핑몰 예시**: **블랙 프라이데이 선착순 쿠폰**. 12시에 쿠폰 발급이 시작되는데, 쿠폰 잔여 수량 캐시가 12시 00분 01초에 만료되었습니다. 수만 명의 대기자가 동시에 새로고침을 누르면, 모든 요청이 DB로 쇄도하여 DB가 다운될 수 있습니다.

#### 4.2.1 Spring 단일 인스턴스 방어: `@Cacheable(sync = true)`

* **동작**: 동일 JVM 안에서 동일 키에 대한 요청이 들어오면 **첫 번째 스레드만** 실제 메서드를 실행하고, 나머지는 결과가 캐시에 들어올 때까지 대기합니다.
* **적용 코드**:
```kotlin
@Cacheable(cacheNames = [CacheNames.PRODUCT_DETAIL], key = "#productId", sync = true)
fun getProduct(productId: Long): Product =
    productRepository.findById(productId).orElseThrow { ProductNotFoundException(productId) }
```
* **장점**: 코드 한 줄로 스탬피드의 1차적인 영향을 차단할 수 있으며 로컬 캐시(Ehcache/Caffeine)와도 궁합이 좋습니다.
* **주의**: JVM(인스턴스) 단위라 멀티 인스턴스 환경에서는 각 인스턴스마다 한 번씩은 스탬피드가 발생할 수 있습니다.

#### 4.2.2 멀티 인스턴스 방어: 분산 락 + 캐시 재계산

* **개념**: 캐시 미스 시 **키 단위 분산 락(Redis `SETNX`, Redisson `RLock`)**을 잡아 **한 노드만 대표로 DB를 조회**하고, 나머지는 캐시가 채워질 때까지 기다립니다.
* **Kotlin 예시**:
```kotlin
fun getProductWithLock(productId: Long): Product {
    val cacheKey = "product:$productId"
    val lockKey = "lock:$cacheKey"

    redisTemplate.opsForValue().get(cacheKey)?.let { return it as Product }

    val lock = redissonClient.getLock(lockKey)
    val acquired = lock.tryLock(2, 5, TimeUnit.SECONDS) // 대기 2초, 보유 5초
    try {
        if (!acquired) {
            Thread.sleep(100)
            return redisTemplate.opsForValue().get(cacheKey) as? Product
                ?: productRepository.findById(productId).orElseThrow { ProductNotFoundException(productId) }
        }
        redisTemplate.opsForValue().get(cacheKey)?.let { return it as Product }

        val loaded = productRepository.findById(productId).orElseThrow { ProductNotFoundException(productId) }
        redisTemplate.opsForValue().set(cacheKey, loaded, Duration.ofMinutes(5))
        return loaded
    } finally {
        if (acquired) lock.unlock()
    }
}
```
* **포인트**:
  1. 락 획득 전후로 **두 번 캐시를 확인(더블 체크)**.
  2. `tryLock`으로 락 대기 시간을 제한하고 실패 시 짧게 대기 후 재조회.
  3. 락은 키 단위로 걸어 다른 키는 동시에 처리 가능하도록 설계.

#### 4.2.3 Soft TTL + Hard TTL (조용한 배경 갱신)

* **아이디어**: 캐시에 **논리 만료 시간(Soft TTL)**과 **물리 만료 시간(Hard TTL)**을 함께 저장합니다. Soft TTL이 지난 경우 사용자에게는 기존 데이터를 반환하되, 백그라운드에서 한 번만 갱신을 트리거합니다.
```kotlin
data class CacheEnvelope<T>(
    val value: T,
    val softExpireAt: Long,
)

fun <T> getWithSoftTtl(
    key: String,
    loader: () -> T,
    softTtlMillis: Long,
    hardTtl: Duration,
): T {
    val envelope = redisTemplate.opsForValue().get(key) as? CacheEnvelope<T>
    val now = Instant.now().toEpochMilli()

    if (envelope == null) return loadAndCache(key, loader, softTtlMillis, hardTtl)
    if (envelope.softExpireAt > now) return envelope.value

    triggerAsyncRefresh(key, loader, softTtlMillis, hardTtl)
    return envelope.value
}
```
* **효과**: 사용자 경험은 빠르게 유지하면서도 DB 부하를 한 스레드가 흡수합니다.

#### 4.2.4 로컬 캐시 Refresh: Caffeine `refreshAfterWrite`

* **개념**: Caffeine은 `refreshAfterWrite`를 사용하면 **캐시 값은 바로 반환하면서 백그라운드에서 자동으로 재계산**합니다.
```kotlin
@Bean
fun cacheManager(): CacheManager =
    CaffeineCacheManager(CacheNames.PRODUCT_DETAIL).apply {
        setCaffeine(
            Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(10, TimeUnit.MINUTES)     // 하드 TTL
                .refreshAfterWrite(5, TimeUnit.MINUTES),    // 5분 후부터 백그라운드 재계산
        )
    }
```
* Spring Cache를 그대로 사용하면서 JVM 로컬 캐시에서 스탬피드를 완화할 수 있습니다.

#### 4.2.5 PER (Probabilistic Early Recomputation)

* **개념**: 만료 시간이 다가오면 확률적으로 미리 캐시를 갱신합니다. TTL이 짧은 키가 많을 때 유용합니다.
* **코드 예시 (Kotlin)**:
```kotlin
fun getProductWithPER(id: Long): Product {
    val cacheKey = "product:$id"
    val cached = redisTemplate.opsForValue().get(cacheKey) as? Product

    if (cached != null) {
        val ttl = redisTemplate.getExpire(cacheKey, TimeUnit.SECONDS)
        if (ttl != null && ttl > 0 && ttl < 60) {
            val random = Random.nextDouble()
            val threshold = ttl / 60.0
            if (random < threshold) {
                CompletableFuture.runAsync { refreshProductCache(id) }
            }
        }
        return cached
    }

    return refreshProductCache(id)
}
```

#### 4.2.6 TTL Jitter, 동시 요청 제한, 워밍업

* **TTL Jitter**: `TTL = 기본값 + 랜덤(±X)`로 설정하여 특정 시각에 몰리지 않도록 합니다.
```kotlin
fun ttlWithJitter(baseSeconds: Long, jitterRange: Long): Duration =
    Duration.ofSeconds(baseSeconds + Random.nextLong(0, jitterRange))
```
* **Semaphore/Semantics**: 동일 키에 대한 동시 미스 처리 쓰레드 수를 `Semaphore(1~N)`로 제한하여 과도한 재계산을 차단합니다.
* **워밍업/배치 리프레시**: 메인 배너, 인기 상품 랭킹 등 핫키는 배치 작업으로 미리 채워두어 TTL 만료 시점의 충격을 줄입니다.

#### 4.2.7 실무 추천 조합

| 상황 | 추천 조합 |
| --- | --- |
| 단일 인스턴스, 트래픽 중간 | `@Cacheable(sync = true)` + 필요 시 Caffeine `refreshAfterWrite` |
| 멀티 인스턴스, 고트래픽 (커머스 랭킹/추천) | `@Cacheable(sync = true)` + Redis 분산 락 + Soft TTL/Hard TTL + TTL Jitter |
| 초핫키(메인 페이지, 선착순 이벤트) | 분산 락 + Soft TTL + 백그라운드 워밍업 + 모니터링 기반 PER |

아래 추가 기법(PER, Mutex, TTL Jitter)은 상황에 맞게 조합하여 사용합니다.

### 4.3 캐시 데이터 직렬화 (Serialization)

* **Java Serialization**: 사용 지양. 역직렬화 이슈, 버전 호환성 문제, 큰 사이즈.
* **JSON (Jackson)**: 가독성 좋음, 범용적.
  * **전략**: `GenericJackson2JsonRedisSerializer` 등을 사용.
  * **주의**: 클래스 타입 정보(`@class`) 포함 시 보안 문제 및 클래스명 변경 시 호환성 문제 발생 가능. 명확한 DTO 사용 권장.

### 4.4 캐싱 로직 적용 계층 (Layering)

* **Service (Domain) Layer**:
  * 비즈니스 로직이 포함된 가공된 결과(DTO)를 캐싱.
  * 복잡한 연산 결과를 재사용할 때 유리. `@Cacheable` 적용의 주된 위치.
* **Repository (Data Access) Layer**:
  * Entity 단위의 단순 조회 결과를 캐싱.
  * 데이터 재사용성이 높지만, 도메인 객체 변환 비용은 줄여주지 못함.
* **기준**: "연산 비용"과 "재사용 빈도"를 고려. 가공 비용이 비싸다면 Service 계층이 유리.

## 5. 분산 환경 이슈 및 용어

### 5.1 분산 환경 캐시 일관성

* **문제**: Local Cache 사용 시 각 서버가 다른 데이터를 가질 수 있음.
* **해결**:
  * **Redis Pub/Sub**: 데이터 변경 시 이벤트를 발행하여 다른 서버의 로컬 캐시 무효화.
  * **짧은 TTL**: 불일치 허용 시간을 줄임.
  * **External Cache 사용**: 근본적인 해결책.

### 5.2 주요 용어 정리

* **getAndClear**: 데이터를 조회함과 동시에 캐시에서 삭제하는 연산. (일회성 토큰 검증, 알림 읽음 처리 등에 사용)
  * **예시**: SMS 인증번호 검증. 한 번 사용하면 재사용 불가하므로 조회와 동시에 삭제해야 함.
* **Write Allocate**: Write Miss 발생 시 캐시에 데이터를 로드하고 쓰는 방식. (주로 Write-Back과 함께 사용)
* **No Write Allocate**: Write Miss 발생 시 캐시에 로드하지 않고 DB에만 쓰는 방식. (주로 Write-Through와 함께 사용)
* **Cache Warming (캐시 워밍업)**: 서비스 시작 전에 자주 사용될 데이터를 미리 캐시에 로드하는 기법.
  * **예시**: 쇼핑몰 서비스 시작 시 인기 상품 Top 100을 미리 Redis에 로드하여 첫 요청부터 빠른 응답 제공.
* **Cache Coherency (캐시 일관성)**: 여러 캐시 인스턴스 간 데이터가 일치하는 상태를 유지하는 것.
  * **해결 방법**: Write-Through, Cache Invalidation (무효화), 짧은 TTL 등.
