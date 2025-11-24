# Spring Redis Client 비교: Lettuce vs Redisson vs Jedis

Spring Boot에서 Redis를 사용할 때 선택할 수 있는 주요 클라이언트 라이브러리인 Lettuce, Redisson, 그리고 Jedis를 비교 분석합니다.

## 1. 개요

### 1.1 Lettuce

* **기반**: Netty (비동기 이벤트 기반 네트워크 프레임워크)
* **특징**: 비동기(Asynchronous) 및 논블로킹(Non-blocking) I/O를 지원하며, 리액티브 프로그래밍(Reactive Programming)을 완벽하게 지원합니다.
* **Spring Boot**: Spring Boot 2.0부터 기본 Redis 클라이언트로 채택되었습니다.

### 1.2 Redisson

* **기반**: Netty
* **특징**: Redis의 명령어를 직접 다루기보다는, 분산 환경에서 필요한 다양한 기능(분산 락, 분산 컬렉션 등)을 Java 객체처럼 쉽게 사용할 수 있도록 추상화된 인터페이스를 제공합니다.
* **Spring Boot**: 별도의 의존성 추가를 통해 통합 가능합니다.

### 1.3 Jedis

* **기반**: 동기(Synchronous) 블로킹(Blocking) I/O
* **특징**: 가장 오래된 Java Redis 클라이언트 중 하나로, API가 단순하고 직관적입니다.
* **Spring Boot**: Spring Boot 1.x의 기본 클라이언트였으나, 2.0부터는 Lettuce로 대체되었습니다.

## 2. 상세 비교

| 특징 | Lettuce | Redisson | Jedis |
| :--- | :--- | :--- | :--- |
| **I/O 모델** | Non-blocking I/O (Netty) | Non-blocking I/O (Netty) | Blocking I/O |
| **동기/비동기** | 비동기 (Async) 지원 | 비동기 (Async) 지원 | 동기 (Sync) |
| **Thread Safety** | Thread-safe (Connection 공유 가능) | Thread-safe | Not Thread-safe (Pool 필요) |
| **Reactive** | 지원 (Project Reactor) | 지원 (RxJava, Reactor) | 미지원 |
| **주요 강점** | 고성능, 확장성, 리소스 효율성 | 분산 락, 분산 컬렉션 등 고수준 기능 | 단순함, 가벼움 |
| **단점** | 러닝 커브 (비동기/리액티브) | 무거움, 별도 학습 필요 | 성능 한계 (블로킹), 멀티스레드 이슈 |

### 2.1 Lettuce 상세 분석

* **장점**:
  * **성능**: 비동기 처리를 통해 적은 수의 스레드로 많은 요청을 처리할 수 있어 처리량(Throughput)이 높습니다.
  * **자원 효율**: 스레드 안전(Thread-safe)하게 설계되어 있어, 여러 스레드에서 하나의 커넥션을 공유할 수 있습니다. (Connection Pooling 불필요)
* **단점**:
  * 분산 락과 같은 고급 기능을 직접 구현해야 하거나 복잡할 수 있습니다. (예: `SETNX`를 이용한 스핀 락 구현 시 부하 발생 가능)

### 2.2 Redisson 상세 분석

* **장점**:
  * **고수준 추상화**: `RLock`, `RMap`, `RSet` 등 Java 인터페이스와 유사한 분산 객체를 제공하여 개발 생산성이 높습니다.
  * **분산 락**: Pub/Sub 방식을 이용한 락 구현으로 Redis 부하를 최소화하고, 타임아웃 및 자동 갱신(Watchdog) 기능을 기본 제공합니다.
* **단점**:
  * 라이브러리가 무겁고, 단순 캐싱 용도로만 쓰기에는 오버헤드가 있을 수 있습니다.
  * Redis 명령어 자체를 직접 제어하고 싶을 때는 Lettuce보다 불편할 수 있습니다.

### 2.3 Jedis 상세 분석

* **장점**:
  * 구현이 단순하고 이해하기 쉽습니다.
* **단점**:
  * 블로킹 I/O 모델로 인해 동시 요청이 많을 경우 스레드 대기 시간이 길어질 수 있습니다.
  * 스레드 안전하지 않아 커넥션 풀(Connection Pool)을 반드시 사용해야 하며, 이는 메모리 사용량 증가로 이어질 수 있습니다.

## 3. Spring Boot에서의 선택 가이드

### 3.1 일반적인 캐싱 및 데이터 저장 (Lettuce 권장)

* 단순히 데이터를 저장하고 조회하는 캐싱 용도나, 높은 처리량이 필요한 API 서버의 경우 **Lettuce**가 가장 적합합니다.
* Spring Data Redis의 기본 설정이므로 별도 설정 없이 바로 사용 가능하며, 성능과 자원 효율성이 뛰어납니다.

### 3.2 분산 락 및 복잡한 분산 처리 (Redisson 권장)

* 재고 차감, 선착순 이벤트 등 동시성 제어가 필수적인 경우 **Redisson**을 사용하는 것이 좋습니다.
* Lettuce로 분산 락을 직접 구현하면 스핀 락으로 인한 Redis 부하, 타임아웃 처리의 복잡함 등의 문제가 발생할 수 있습니다. Redisson은 이를 안정적으로 해결해줍니다.

### 3.3 혼합 사용 전략

* 실무에서는 두 라이브러리를 함께 사용하는 경우가 많습니다.
  * **기본 캐싱/조회**: `RedisTemplate` (Lettuce 기반) 사용.
  * **분산 락**: `RedissonClient` (Redisson) 사용.
* Spring Boot 설정에서 Lettuce를 기본으로 사용하면서, 분산 락이 필요한 빈(Bean)에만 Redisson을 주입받아 사용하는 방식이 효율적입니다.

## 4. 성능 비교 및 벤치마크

### 4.1 처리량 (Throughput) 비교

일반적인 캐시 조회 작업 기준:

* **Lettuce**: 비동기/논블로킹 모델로 높은 처리량 (초당 수만~수십만 요청 처리 가능)
* **Redisson**: 추가 추상화 레이어로 인해 Lettuce보다 약간 낮지만, 실무에서 충분한 성능
* **Jedis**: 블로킹 모델로 동시 요청이 많을 경우 처리량이 제한됨

### 4.2 리소스 사용량

* **Lettuce**: Connection Pool 불필요, 스레드 안전하므로 메모리 효율적
* **Redisson**: 추가 기능으로 인해 메모리 사용량이 더 큼
* **Jedis**: Connection Pool 필요로 인해 메모리 사용량 증가

### 4.3 실제 사용 시나리오별 권장사항

#### 시나리오 1: 단순 캐싱 (상품 정보, 카테고리 목록)
* **권장**: Lettuce + Spring Cache (`@Cacheable`)
* **이유**: 높은 처리량과 낮은 리소스 사용량

#### 시나리오 2: 분산 락 (재고 차감, 선착순 이벤트)
* **권장**: Redisson (`RLock`)
* **이유**: Pub/Sub 기반 락으로 Redis 부하 최소화, 자동 갱신 기능

#### 시나리오 3: 분산 컬렉션 (실시간 랭킹, 세션 관리)
* **권장**: Redisson (`RMap`, `RSet`, `RSortedSet`)
* **이유**: Java 컬렉션과 유사한 API로 개발 생산성 향상

#### 시나리오 4: 대량 데이터 처리 (배치 작업)
* **권장**: Lettuce (Pipeline 활용)
* **이유**: Pipeline을 통한 대량 명령 처리 최적화

## 5. 하이브리드 사용 전략 (실무 권장)

실무에서는 두 라이브러리를 함께 사용하는 것이 일반적입니다:

```kotlin
@Configuration
class RedisConfig {
    // Lettuce: 기본 캐싱용
    @Bean
    fun redisTemplate(connectionFactory: RedisConnectionFactory): RedisTemplate<String, Any> {
        val template = RedisTemplate<String, Any>()
        template.connectionFactory = connectionFactory
        template.keySerializer = StringRedisSerializer()
        template.valueSerializer = GenericJackson2JsonRedisSerializer()
        return template
    }
    
    // Redisson: 분산 락용
    @Bean
    fun redissonClient(): RedissonClient {
        val config = Config()
        config.useSingleServer()
            .setAddress("redis://localhost:6379")
        return Redisson.create(config)
    }
}

@Service
class ProductService(
    private val redisTemplate: RedisTemplate<String, Any>,  // Lettuce
    private val redissonClient: RedissonClient  // Redisson
) {
    // 캐싱: Lettuce 사용
    @Cacheable("products")
    fun getProduct(id: Long): Product {
        // ...
    }
    
    // 분산 락: Redisson 사용
    fun decreaseStock(productId: Long, quantity: Int) {
        val lock = redissonClient.getLock("lock:stock:$productId")
        // ...
    }
}
```

## 6. 결론

* **Lettuce**: 성능과 효율성이 중요한 일반적인 웹 애플리케이션의 기본 클라이언트.
* **Redisson**: 분산 락, 분산 컬렉션 등 특수 목적의 기능이 필요할 때 강력한 도구.
* **전략**: 기본적으로 Lettuce를 사용하되, 분산 락이 필요한 로직에 한해 Redisson을 도입하는 하이브리드 방식을 추천합니다.
* **주의사항**: 
  * 두 라이브러리를 함께 사용할 때는 동일한 Redis 인스턴스를 공유해도 문제없습니다.
  * Redisson의 무거운 의존성 때문에 단순 캐싱만 필요하다면 Lettuce만 사용하는 것이 좋습니다.
