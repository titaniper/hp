# Redis 학습 및 Spring 연동

## 1. Redis 개요

Redis(Remote Dictionary Server)는 고성능 키-값(Key-Value) 저장소로서, 단순한 캐시를 넘어 다양한 데이터 구조와 기능을 제공하는 인메모리 데이터 솔루션입니다.

* **In-Memory Architecture**: 모든 데이터를 메모리에 상주시켜 디스크 I/O 없이 마이크로초(µs) 단위의 응답 속도를 제공합니다.
* **Single Threaded**: Redis는 기본적으로 싱글 스레드 이벤트 루프 모델을 사용합니다.
  * **Atomic 보장**: 한 번에 하나의 명령만 수행하므로, 별도의 락 없이도 경쟁 상태(Race Condition)를 피할 수 있습니다.
  * **주의사항**: `KEYS`, `FLUSHALL`과 같은 O(N) 명령어를 실행하면 처리가 완료될 때까지 다른 모든 요청이 블로킹됩니다. 프로덕션 환경에서는 `SCAN` 등을 사용해야 합니다.

## 2. 주요 자료구조 및 명령어

Redis는 비즈니스 로직을 데이터 저장소 레벨에서 처리할 수 있도록 다양한 자료구조를 제공합니다.

### 2.1 자료구조 (Data Structures)

* **String**: 기본 타입 (Binary Safe, 최대 512MB). 캐싱, 카운터, 세션 관리 등에 사용.
* **List**: Linked List 구조. 메시지 큐(LPUSH/RPOP), 타임라인 등에 활용.
* **Set**: 순서 없는 유니크 집합. 태그 기능, 팔로워 리스트, 중복 제거.
* **Sorted Set (ZSet)**: Score를 포함한 Set. 실시간 랭킹, 우선순위 작업 큐.
* **Hash**: Field-Value 구조. 객체 저장에 유리하며 메모리 효율적.
* **Bitmaps**: 비트 단위 연산. 일일 활성 사용자(DAU) 체크 등 공간 효율적 저장.
* **HyperLogLogs**: 대용량 데이터의 유니크 카운트 추정 (적은 메모리로 근사치 계산).
* **Geospatial**: 위치 기반 데이터 저장 및 반경 검색.

### 2.2 핵심 기법 및 명령어

#### SETNX와 분산 락 (Distributed Lock)

* **SETNX (SET if Not eXists)**: 키가 없을 때만 값을 저장합니다. (1: 성공, 0: 실패)
* **Atomic Lock 구현**: 과거에는 `SETNX` 후 `EXPIRE`를 따로 호출했으나, 이는 원자적이지 않아 실패 시 데드락 위험이 있었습니다.
  * **Modern Way**: `SET key value NX PX 10000` (NX: 없을 때만, PX: 10초 만료) 명령어로 획득과 만료 설정을 원자적으로 수행합니다.
* **Redisson**: Java에서는 `Redisson` 라이브러리가 이 기능을 래핑하여, 스핀 락(Spin Lock) 대신 Pub/Sub 방식을 통해 Redis 부하를 줄이고 타임아웃/재시도 로직을 안정적으로 제공합니다.

#### Atomic Counters (INCR, DECR)

* **기능**: 값을 읽고, 증가시키고, 저장하는 과정을 원자적으로 수행합니다.
* **활용**: 동시성 이슈 없는 조회수 카운트, 재고 차감, 시퀀스 생성.

#### Pipelining

* **기능**: 클라이언트가 여러 명령을 한 번에 전송하고, 서버도 응답을 한 번에 반환합니다.
* **효과**: 네트워크 왕복 시간(RTT)을 줄여 대량 데이터 처리 시 처리량(Throughput)을 극대화합니다.

#### SCAN

* **기능**: `KEYS` 명령의 대안으로, 커서(Cursor)를 사용하여 데이터를 조금씩 끊어서 조회합니다.
* **효과**: 긴 시간 동안 서버를 블로킹하지 않고 전체 키를 탐색할 수 있습니다.

## 3. Redis 아키텍처 및 운영

### 3.1 영속성 (Persistence)

메모리 데이터의 휘발성을 보완하기 위한 저장 방식입니다.

* **RDB (Snapshot)**: 특정 간격으로 데이터 전체 스냅샷을 디스크에 저장. 로딩이 빠르지만 장애 시 마지막 스냅샷 이후 데이터 유실 가능.
* **AOF (Append Only File)**: 모든 쓰기 명령을 로그로 기록. 데이터 유실이 적지만 파일 크기가 크고 로딩이 느림.
* **권장**: RDB로 주기적 백업을 하고, AOF로 최신 데이터를 보장하는 혼합 방식 사용.

### 3.2 고가용성 (High Availability)

* **Replication**: Master-Replica 구조로 읽기 부하 분산 및 데이터 이중화.
* **Sentinel**: Master 장애 감지 및 자동 페일오버(Failover) 시스템.
* **Cluster**: 데이터를 여러 노드에 분산(Sharding)하여 무제한에 가까운 확장성 제공.

### 3.3 메모리 관리 (Eviction)

메모리가 가득 찼을 때의 삭제 정책(`maxmemory-policy`) 설정이 중요합니다.

* **allkeys-lru**: 모든 키 중 가장 오랫동안 사용되지 않은 키 삭제 (캐시 용도).
* **volatile-lru**: 만료 시간(TTL)이 있는 키 중에서 LRU 삭제.
* **allkeys-lfu**: 모든 키 중 사용 빈도가 가장 낮은 키 삭제.
* **volatile-lfu**: 만료 시간(TTL)이 있는 키 중에서 LFU 삭제.
* **allkeys-random**: 모든 키 중 랜덤하게 삭제.
* **volatile-random**: 만료 시간(TTL)이 있는 키 중에서 랜덤 삭제.
* **volatile-ttl**: 만료 시간(TTL)이 가장 짧은 키부터 삭제.
* **noeviction**: 메모리가 가득 차면 쓰기 작업을 거부 (에러 반환). (기본값)
* **선택 가이드**:
  * **캐시 전용**: `allkeys-lru` 또는 `allkeys-lfu` (모든 키가 캐시인 경우)
  * **캐시 + 영구 데이터 혼재**: `volatile-lru` (TTL이 있는 키만 삭제)
  * **중요 데이터 보호**: `volatile-lru` + 영구 데이터는 TTL 없이 저장

## 4. Redis 캐싱 전략 (Caching Strategies)

### 4.1 Look Aside (Lazy Loading)

가장 일반적으로 사용되는 전략입니다.

* **동작**:
    1. 애플리케이션이 데이터를 읽을 때 먼저 캐시(Redis)를 확인.
    2. **Cache Hit**: 캐시에 데이터가 있으면 바로 반환.
    3. **Cache Miss**: 캐시에 없으면 DB에서 조회 후 캐시에 저장하고 반환.
* **장점**: Redis가 다운되어도 DB에서 데이터를 가져올 수 있어 서비스 장애로 직결되지 않음.
* **단점**: 초기 조회 시나 Cache Miss 발생 시 DB 부하가 생길 수 있음. 데이터 정합성 유지 필요.
* **적용**: 반복적인 읽기가 많은 서비스.

### 4.2 Write Through

* **동작**: 데이터를 쓸 때 캐시와 DB에 동시에 저장.
* **장점**: 캐시와 DB의 데이터가 항상 일치하여 정합성이 높음.
* **단점**: 쓰기 작업 시 두 곳에 저장해야 하므로 성능 저하 발생. 자주 읽히지 않는 데이터도 캐시에 저장되어 리소스 낭비 가능성.
* **적용**: 데이터 유실이 절대 없어야 하거나, 저장된 데이터가 빈번하게 바로 읽히는 경우.

### 4.3 Write Back (Write Behind)

* **동작**: 데이터를 캐시에 먼저 저장하고, 일정 주기나 조건에 따라 DB에 비동기로 반영.
* **장점**: 쓰기 성능이 매우 빠름 (DB 부하 감소).
* **단점**: 캐시 장애 시 데이터 유실 위험이 큼.
* **적용**: 로그 수집, 조회수 카운트 등 데이터 유실이 일부 허용되더라도 쓰기 성능이 중요한 경우.

## 5. Spring Boot 연동

### 5.1 기본 설정

* **의존성**: `spring-boot-starter-data-redis`
* **설정**: `RedisConnectionFactory`, `RedisTemplate` 빈 등록.
* **사용**:
  * `RedisTemplate`을 주입받아 직접 Redis 명령어 실행.
  * Spring Cache Abstraction(`@Cacheable`, `@CachePut`, `@CacheEvict`)을 사용하여 선언적으로 캐싱 적용.
  * Redisson을 활용하여 분산 락 적용 (재고 차감 등 동시성 이슈 해결).

### 5.2 RedisTemplate 설정 예시

```kotlin
@Configuration
class RedisConfig {
    @Bean
    fun redisConnectionFactory(): LettuceConnectionFactory {
        val config = RedisStandaloneConfiguration()
        config.hostName = "localhost"
        config.port = 6379
        return LettuceConnectionFactory(config)
    }
    
    @Bean
    fun redisTemplate(connectionFactory: RedisConnectionFactory): RedisTemplate<String, Any> {
        val template = RedisTemplate<String, Any>()
        template.connectionFactory = connectionFactory
        template.keySerializer = StringRedisSerializer()
        template.valueSerializer = GenericJackson2JsonRedisSerializer()
        template.hashKeySerializer = StringRedisSerializer()
        template.hashValueSerializer = GenericJackson2JsonRedisSerializer()
        return template
    }
}
```

### 5.3 고급 기능 활용

#### 5.3.1 Pipeline을 활용한 대량 처리

```kotlin
@Service
class ProductService(
    private val redisTemplate: RedisTemplate<String, Any>
) {
    fun batchUpdateProducts(products: List<Product>) {
        redisTemplate.executePipelined { connection ->
            products.forEach { product ->
                connection.set(
                    "product:${product.id}".toByteArray(),
                    objectMapper.writeValueAsBytes(product)
                )
            }
            null // Pipeline은 반환값이 null이어야 함
        }
    }
}
```

#### 5.3.2 Lua Script를 활용한 원자적 연산

```kotlin
@Service
class InventoryService(
    private val redisTemplate: RedisTemplate<String, Any>
) {
    private val decreaseStockScript = """
        local current = redis.call('GET', KEYS[1])
        if current == false or tonumber(current) < tonumber(ARGV[1]) then
            return 0
        end
        return redis.call('DECRBY', KEYS[1], ARGV[1])
    """.trimIndent()
    
    fun decreaseStock(productId: Long, quantity: Int): Boolean {
        val script = DefaultRedisScript<Long>(decreaseStockScript, Long::class.java)
        val result = redisTemplate.execute(script, listOf("stock:$productId"), quantity.toString())
        return result != null && result > 0
    }
}
```

### 5.4 모니터링 및 운영

* **Slowlog**: 느린 쿼리 로그 확인 (`SLOWLOG GET 10`)
* **INFO 명령어**: 메모리 사용량, 연결 수, 명령 통계 등 확인
* **Redis Insight / Redis Commander**: GUI 도구로 모니터링
* **핵심 지표**:
  * **Hit Rate**: 캐시 적중률 (목표: 80% 이상)
  * **Memory Usage**: 메모리 사용률 (80% 이상 시 Eviction 정책 확인)
  * **Connected Clients**: 연결된 클라이언트 수
  * **Commands per second**: 초당 명령 처리 수




```
keys joopang:popularProducts:*

get joopang:popularProducts::3:5
```

