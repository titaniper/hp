# 캐시 쓰기 정책과 Write Miss 처리 (Caching Write Policies)

## 1. 개요

캐시 시스템에서 데이터를 쓸 때(Write)의 정책은 데이터 일관성과 성능에 큰 영향을 미칩니다. 크게 **Write-Through**와 **Write-Back**으로 나뉘며, 캐시에 데이터가 없을 때(Write Miss)의 처리 방식에 따라 **Write Allocate**와 **No Write Allocate**로 구분됩니다.

## 2. Write Miss 정책

Write 작업 시 캐시에 해당 데이터가 존재하지 않을 때의 처리 방식입니다.

### 2.1 Write Allocate (Fetch on Write)

* **동작**: Write Miss가 발생하면, 해당 데이터 블록을 메인 메모리(DB)에서 가져와 캐시에 로드한 뒤 쓰기 작업을 수행합니다.
* **특징**: 데이터가 캐시에 로드되므로, 이후의 읽기/쓰기 작업(Read/Write Hit)은 캐시에서 빠르게 수행될 수 있습니다.
* **유사성**: Read Miss 처리 방식과 유사하게 데이터를 가져옵니다.

### 2.2 No Write Allocate (Don't Fetch on Write)

* **동작**: Write Miss가 발생하면, 캐시에 로드하지 않고 메인 메모리(DB)에만 직접 씁니다.
* **특징**: 데이터는 오직 Read Miss가 발생했을 때만 캐시에 로드됩니다.
* **주의**: "Write Around"는 **쓰기 전략(Write Strategy)**의 한 종류로, Write Miss 정책과는 별개입니다. Write Around는 "DB에만 쓰고 캐시는 건드리지 않음"을 의미하며, No Write Allocate는 "Write Miss 시 캐시에 로드하지 않음"을 의미합니다. Write Around 전략은 일반적으로 No Write Allocate와 함께 사용되지만, 개념적으로는 구분됩니다.

## 3. 쓰기 정책과의 일반적인 조합

Write-Through와 Write-Back 정책은 이론적으로 어떤 Write Miss 정책과도 결합 가능하지만, 효율성을 위해 주로 사용되는 조합이 있습니다.

### 3.1 Write-Through + No Write Allocate

* **이유**: Write-Through는 어차피 메인 메모리에 바로 써야 하므로, 굳이 캐시에 데이터를 가져와서(Allocate) 쓸 필요가 없습니다. 캐시에 가져와봤자 바로 메모리에도 써야 하므로 오버헤드만 증가할 수 있습니다.
* **장점**: 쓰기 작업이 빈번하지만 읽기는 드문 데이터의 경우 캐시 오염을 방지할 수 있습니다.

### 3.2 Write-Back + Write Allocate

* **이유**: Write-Back은 캐시에서만 업데이트하고 나중에 메모리에 반영하므로, 데이터를 캐시에 가져와서(Allocate) 작업하는 것이 유리합니다.
* **장점**: 동일한 위치에 대한 후속 쓰기/읽기 작업이 캐시 내에서 빠르게 처리됩니다.

## 4. Write-Around 전략 상세

### 4.1 Write-Around의 개념

* **동작**: DB에만 쓰고 캐시에는 쓰지 않음. (읽기 시점에 Cache Miss로 로드됨)
* **특징**: 자주 읽히지 않는 데이터가 캐시를 점유하는 것을 방지.
* **쇼핑몰 예시**: **CS 문의글 작성, 로그 저장**. 사용자가 문의글을 작성했을 때, 관리자가 바로 읽지 않을 수도 있습니다. 굳이 작성 시점에 캐시에 넣지 않고, 관리자가 조회를 요청할 때(Read Miss) 캐싱합니다.

### 4.2 Write-Around와 No Write Allocate의 관계

* **Write-Around**: 쓰기 전략의 한 종류. "DB에만 쓰고 캐시는 건드리지 않음"을 의미.
* **No Write Allocate**: Write Miss 정책. "Write Miss 시 캐시에 로드하지 않음"을 의미.
* **관계**: Write-Around 전략을 사용할 때는 자연스럽게 No Write Allocate 정책이 적용됩니다. 반대로 Write-Through나 Write-Back을 사용할 때도 No Write Allocate를 선택할 수 있지만, 일반적으로는 Write Allocate와 함께 사용됩니다.

### 4.3 Write-Around 구현 예시

```kotlin
@Service
class ProductService(
    private val productRepository: ProductRepository,
    private val redisTemplate: RedisTemplate<String, Any>
) {
    // Write-Around: DB에만 쓰고 캐시는 무효화
    fun updateProduct(id: Long, updateRequest: ProductUpdateRequest) {
        // 1. DB 업데이트
        val product = productRepository.findById(id)
            .orElseThrow { ProductNotFoundException(id) }
        product.update(updateRequest)
        productRepository.save(product)
        
        // 2. 캐시 무효화 (선택적)
        redisTemplate.delete("product:$id")
        // 또는 캐시를 삭제하지 않고 TTL 만료를 기다릴 수도 있음
    }
    
    // Read: Look-Aside 패턴
    fun getProduct(id: Long): Product {
        val cacheKey = "product:$id"
        val cached = redisTemplate.opsForValue().get(cacheKey) as? Product
        if (cached != null) return cached
        
        // Cache Miss: DB 조회 후 캐시 저장
        val product = productRepository.findById(id)
            .orElseThrow { ProductNotFoundException(id) }
        redisTemplate.opsForValue()
            .set(cacheKey, product, Duration.ofMinutes(10))
        return product
    }
}
```

## 5. 실무에서의 고려사항 (In Practice)

* **Redis 활용**: 웹 애플리케이션에서 Redis를 캐시로 사용할 때 가장 흔한 패턴은 **Look Aside (Read)** + **Write Around** 조합입니다. 즉, 읽을 때 없으면 채우고, 쓸 때는 DB에만 쓰고 캐시를 삭제(Evict)하거나 만료시키는 방식을 많이 사용합니다.
* **데이터 일관성**: Write-Back은 성능은 좋지만 캐시 장애 시 데이터 유실 위험이 있어, 금융 데이터 등 중요 정보에는 Write-Through나 Write Around(DB 우선) 방식을 선호합니다.
* **Write Storm**: Write Allocate 사용 시 쓰기 요청이 급증하면 캐시 미스로 인한 DB 부하(데이터 로드)가 발생할 수 있어 주의가 필요합니다.
* **캐시 무효화 전략**: Write-Around 사용 시 캐시를 즉시 삭제할지, TTL 만료를 기다릴지 결정해야 합니다.
  * **즉시 삭제**: 데이터 정합성이 중요한 경우 (예: 상품 가격 변경)
  * **TTL 만료 대기**: 성능을 우선시하고 약간의 불일치를 허용하는 경우 (예: 조회수, 좋아요 수)
