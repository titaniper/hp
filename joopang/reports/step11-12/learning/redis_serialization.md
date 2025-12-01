# Redis 직렬화(Serialization) 가이드

## 1. 직렬화 개요

Redis는 바이트 배열만 저장할 수 있으므로, Java/Kotlin 객체를 Redis에 저장하려면 직렬화(Serialization) 과정이 필요합니다. 역직렬화(Deserialization)는 저장된 바이트 배열을 다시 객체로 변환하는 과정입니다.

### 1.1 직렬화가 필요한 이유

* **Redis는 바이트 배열만 저장**: Redis는 기본적으로 String, List, Set 등의 자료구조를 저장하지만, 내부적으로는 모두 바이트 배열로 처리됩니다.
* **객체를 바이트로 변환**: Java/Kotlin 객체를 Redis에 저장하려면 객체를 바이트 배열로 변환해야 합니다.
* **타입 정보 보존**: 역직렬화 시 원래 객체 타입으로 복원하기 위해 타입 정보가 필요합니다.

## 2. 직렬화 방식 비교

### 2.1 Java Serialization (비추천)

```kotlin
// Java 기본 직렬화 사용 예시
val serializer = JdkSerializationRedisSerializer()
```

**단점:**
* **큰 크기**: 직렬화된 데이터 크기가 크고 비효율적
* **버전 호환성 문제**: 클래스 구조 변경 시 역직렬화 실패 가능
* **보안 취약점**: 역직렬화 과정에서 악의적인 코드 실행 가능 (Deserialization Vulnerability)
* **성능 저하**: 직렬화/역직렬화 속도가 느림

**결론**: 프로덕션 환경에서 사용하지 않는 것을 강력히 권장합니다.

### 2.2 JSON 직렬화 (권장)

```kotlin
// Jackson을 사용한 JSON 직렬화
val serializer = GenericJackson2JsonRedisSerializer(objectMapper)
```

**장점:**
* **가독성**: Redis에서 직접 확인 가능한 텍스트 형식
* **디버깅 용이**: Redis CLI에서 직접 데이터 확인 가능
* **범용성**: 다양한 언어와 시스템에서 호환 가능
* **성능**: Java Serialization보다 빠름

**단점:**
* **타입 정보 필요**: 역직렬화 시 원래 타입을 알아야 함
* **크기**: 바이너리 포맷보다 약간 큼 (하지만 압축 가능)

## 3. GenericJackson2JsonRedisSerializer

Spring Data Redis에서 제공하는 Jackson 기반 직렬화기입니다.

### 3.1 기본 사용법

```kotlin
@Configuration
class RedisCacheConfig {
    @Bean
    fun redisCacheManager(
        connectionFactory: RedisConnectionFactory,
        objectMapper: ObjectMapper,
    ): RedisCacheManager {
        // ObjectMapper를 그대로 사용
        val serializer = GenericJackson2JsonRedisSerializer(objectMapper)
        
        val cacheConfig = RedisCacheConfiguration.defaultCacheConfig()
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(serializer)
            )
        
        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(cacheConfig)
            .build()
    }
}
```

### 3.2 문제 상황: 타입 정보 부재

**문제:**
```kotlin
@Cacheable(cacheNames = ["products"])
fun getTopProducts(): TopProductsOutput {
    // ...
}
```

위 코드에서 Redis에 저장된 데이터를 읽을 때:
```json
{
  "period": "3days",
  "products": [...]
}
```

이 JSON을 역직렬화하면 Jackson은 타입 정보가 없어서 `LinkedHashMap`으로 변환합니다:
```kotlin
// 에러 발생!
val cached = cache.get("key") as TopProductsOutput
// ClassCastException: LinkedHashMap cannot be cast to TopProductsOutput
```

## 4. activateDefaultTyping

### 4.1 개념

`activateDefaultTyping`은 Jackson의 `ObjectMapper`에 타입 정보를 자동으로 포함시키는 기능입니다.

```kotlin
val objectMapper = ObjectMapper().apply {
    activateDefaultTyping(
        LaissezFaireSubTypeValidator.instance,
        ObjectMapper.DefaultTyping.NON_FINAL,
        JsonTypeInfo.As.PROPERTY,
    )
}
```

### 4.2 파라미터 설명

#### 4.2.1 SubTypeValidator

타입 정보를 포함할 수 있는 클래스를 검증하는 역할입니다.

* **`LaissezFaireSubTypeValidator.instance`**: 모든 클래스에 대해 타입 정보 포함 허용
  * **장점**: 간단하고 모든 타입 지원
  * **단점**: 보안 위험 (악의적인 클래스 역직렬화 가능)
  * **사용 시나리오**: 내부 시스템, 신뢰할 수 있는 데이터만 다룰 때

* **`BasicPolymorphicTypeValidator.builder()`**: 특정 클래스만 허용
  ```kotlin
  val validator = BasicPolymorphicTypeValidator.builder()
      .allowIfSubType(TopProductsOutput::class.java)
      .allowIfSubType("io.joopang.services.product.application")
      .build()
  ```
  * **장점**: 보안 강화
  * **단점**: 설정이 복잡함

#### 4.2.2 DefaultTyping

어떤 타입에 타입 정보를 포함할지 결정합니다.

* **`NON_FINAL`**: `final`이 아닌 모든 클래스에 타입 정보 포함
  * 가장 일반적으로 사용
  * `final` 클래스는 타입 정보 없이 직렬화 (예: `String`, `Integer`)

* **`OBJECT_AND_NON_CONCRETE`**: 추상 클래스, 인터페이스, Object 타입에만 포함
  * 구체적인 클래스는 타입 정보 없이 직렬화

* **`NON_CONCRETE_AND_ARRAYS`**: 배열과 비구체 타입에만 포함

#### 4.2.3 JsonTypeInfo.As

타입 정보를 어떻게 포함할지 결정합니다.

* **`PROPERTY`**: JSON 객체에 `@class` 속성으로 포함
  ```json
  {
    "@class": "io.joopang.services.product.application.ProductService$TopProductsOutput",
    "period": "3days",
    "products": [...]
  }
  ```
  * **장점**: 명확하고 디버깅 용이
  * **단점**: JSON 크기 증가, 클래스명 변경 시 호환성 문제

* **`WRAPPER_ARRAY`**: 배열로 감싸서 첫 번째 요소에 타입 정보
  ```json
  ["io.joopang.services.product.application.ProductService$TopProductsOutput", {
    "period": "3days",
    "products": [...]
  }]
  ```

* **`WRAPPER_OBJECT`**: 객체로 감싸서 타입 정보 포함

### 4.3 실제 사용 예시

```kotlin
@Configuration
class RedisCacheConfig {
    @Bean
    fun redisCacheManager(
        connectionFactory: RedisConnectionFactory,
        objectMapper: ObjectMapper,
    ): RedisCacheManager {
        // 원본 ObjectMapper를 복사하여 수정 (다른 곳에 영향 없음)
        val cacheObjectMapper = objectMapper.copy().apply {
            activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY,
            )
        }
        
        val serializer = GenericJackson2JsonRedisSerializer(cacheObjectMapper)
        
        val cacheConfig = RedisCacheConfiguration.defaultCacheConfig()
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(serializer)
            )
        
        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(cacheConfig)
            .build()
    }
}
```

**결과:**
```json
{
  "@class": "io.joopang.services.product.application.ProductService$TopProductsOutput",
  "period": "3days",
  "products": [
    {
      "@class": "io.joopang.services.product.application.ProductService$TopProductsOutput$TopProductOutput",
      "rank": 1,
      "product": {...},
      "salesCount": 343,
      "revenue": 8575000.00
    }
  ]
}
```

## 5. @JsonTypeInfo 어노테이션

클래스 레벨에서 개별적으로 타입 정보를 포함하는 방법입니다.

### 5.1 사용법

```kotlin
@JsonTypeInfo(
    use = JsonTypeInfo.Id.CLASS,
    include = JsonTypeInfo.As.PROPERTY,
    property = "@class"
)
data class TopProductsOutput(
    val period: String,
    val products: List<TopProductOutput>,
) {
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
    data class TopProductOutput(
        val rank: Int,
        val product: ProductSummary,
        // ...
    )
}
```

### 5.2 activateDefaultTyping vs @JsonTypeInfo

| 방식 | 장점 | 단점 | 사용 시나리오 |
|------|------|------|---------------|
| `activateDefaultTyping` | 모든 클래스에 자동 적용, 설정 간단 | 보안 위험, 모든 클래스에 타입 정보 포함 | 내부 시스템, 빠른 프로토타이핑 |
| `@JsonTypeInfo` | 선택적 적용, 보안 강화 | 각 클래스에 명시 필요, 설정 복잡 | 프로덕션 환경, 보안 중요 |

### 5.3 조합 사용

두 방식을 함께 사용할 수도 있습니다:
* `activateDefaultTyping`: 기본적으로 타입 정보 포함
* `@JsonTypeInfo`: 특정 클래스에만 명시적으로 타입 정보 포함

하지만 일반적으로는 하나의 방식만 사용하는 것을 권장합니다.

## 6. 실제 문제 해결 사례

### 6.1 문제 상황

```kotlin
@Cacheable(cacheNames = ["popular-products"])
fun getTopProducts(): TopProductsOutput {
    // ...
}
```

**에러:**
```
ClassCastException: class java.util.LinkedHashMap cannot be cast to 
class io.joopang.services.product.application.ProductService$TopProductsOutput
```

### 6.2 원인

1. `GenericJackson2JsonRedisSerializer`가 타입 정보 없이 JSON을 역직렬화
2. Jackson이 타입 정보가 없으면 기본적으로 `LinkedHashMap`으로 변환
3. `TopProductsOutput`으로 캐스팅 시도 시 `ClassCastException` 발생

### 6.3 해결 방법

**방법 1: activateDefaultTyping 사용 (권장)**
```kotlin
val cacheObjectMapper = objectMapper.copy().apply {
    activateDefaultTyping(
        LaissezFaireSubTypeValidator.instance,
        ObjectMapper.DefaultTyping.NON_FINAL,
        JsonTypeInfo.As.PROPERTY,
    )
}
val serializer = GenericJackson2JsonRedisSerializer(cacheObjectMapper)
```

**방법 2: @JsonTypeInfo 사용**
```kotlin
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
data class TopProductsOutput(...)
```

## 7. 보안 고려사항

### 7.1 LaissezFaireSubTypeValidator의 위험성

`LaissezFaireSubTypeValidator`는 모든 클래스의 역직렬화를 허용하므로, 악의적인 클래스를 역직렬화할 수 있습니다.

**위험한 시나리오:**
```kotlin
// 악의적인 클래스
class MaliciousClass {
    init {
        // 역직렬화 시 자동 실행되는 코드
        Runtime.getRuntime().exec("rm -rf /")
    }
}
```

Redis에 저장된 JSON에 `"@class": "MaliciousClass"`가 포함되면 역직렬화 시 위험한 코드가 실행될 수 있습니다.

### 7.2 보안 강화 방법

**방법 1: BasicPolymorphicTypeValidator 사용**
```kotlin
val validator = BasicPolymorphicTypeValidator.builder()
    .allowIfSubType("io.joopang.services.product.application")
    .allowIfSubType("io.joopang.services.common.domain")
    .build()

val cacheObjectMapper = objectMapper.copy().apply {
    activateDefaultTyping(
        validator,
        ObjectMapper.DefaultTyping.NON_FINAL,
        JsonTypeInfo.As.PROPERTY,
    )
}
```

**방법 2: 패키지 제한**
```kotlin
val validator = BasicPolymorphicTypeValidator.builder()
    .allowIfSubType("io.joopang")
    .denyIfSubType("java.lang")
    .denyIfSubType("java.util")
    .build()
```

**방법 3: @JsonTypeInfo만 사용**
특정 클래스에만 타입 정보를 포함하여 보안 위험을 최소화합니다.

## 8. 성능 고려사항

### 8.1 직렬화 크기 비교

| 방식 | 크기 | 예시 |
|------|------|------|
| Java Serialization | 큼 | ~500 bytes |
| JSON (타입 정보 없음) | 중간 | ~200 bytes |
| JSON (타입 정보 포함) | 중간~큼 | ~300 bytes |
| MessagePack | 작음 | ~150 bytes |

### 8.2 직렬화 속도

일반적으로:
1. MessagePack/Protobuf (가장 빠름)
2. JSON (타입 정보 없음)
3. JSON (타입 정보 포함)
4. Java Serialization (가장 느림)

### 8.3 실무 권장사항

* **대부분의 경우**: JSON + 타입 정보 포함으로 충분
* **대용량 데이터**: MessagePack 또는 Protobuf 고려
* **고빈도 조회**: 바이너리 포맷 고려

## 9. 마이그레이션 시 주의사항

### 9.1 클래스명 변경

타입 정보에 클래스명이 포함되므로, 클래스명이나 패키지명을 변경하면 기존 캐시 데이터를 읽을 수 없습니다.

**해결 방법:**
1. 캐시 무효화 후 재생성
2. `@JsonTypeName` 사용하여 별칭 지정
3. 마이그레이션 스크립트 작성

### 9.2 필드 추가/제거

Jackson은 기본적으로 알 수 없는 필드를 무시하므로, 필드 추가는 안전합니다. 하지만 필드 제거 시 주의가 필요합니다.

## 10. 실무 체크리스트

### 10.1 설정 확인

- [ ] `activateDefaultTyping` 또는 `@JsonTypeInfo` 사용 여부 확인
- [ ] `SubTypeValidator` 설정 확인 (보안)
- [ ] `DefaultTyping` 레벨 확인
- [ ] `JsonTypeInfo.As` 방식 확인

### 10.2 테스트

- [ ] 캐시 저장 후 읽기 테스트
- [ ] 중첩 객체 역직렬화 테스트
- [ ] 리스트/맵 역직렬화 테스트
- [ ] 기존 캐시 데이터 호환성 테스트

### 10.3 모니터링

- [ ] Redis 메모리 사용량 모니터링
- [ ] 직렬화/역직렬화 성능 모니터링
- [ ] 에러 로그 모니터링 (ClassCastException 등)

## 11. 참고 자료

* [Jackson Polymorphic Deserialization](https://github.com/FasterXML/jackson-docs/wiki/JacksonPolymorphicDeserialization)
* [Spring Data Redis Serialization](https://docs.spring.io/spring-data/redis/docs/current/reference/html/#redis:serializer)
* [GenericJackson2JsonRedisSerializer JavaDoc](https://docs.spring.io/spring-data/redis/docs/current/api/org/springframework/data/redis/serializer/GenericJackson2JsonRedisSerializer.html)

