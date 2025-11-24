# 멘토링 질문 답변집 (10년차 시니어 개발자 관점)

> 이 문서는 커머스 도메인에서 10년 이상의 경험을 가진 시니어 개발자의 관점에서 작성된 실무 중심의 답변입니다. 이론보다는 **실전 경험과 트레이드오프**에 초점을 맞추었습니다.

---

## Q1. 분산 락과 재고 처리 (Distributed Lock & Inventory)

**답변:**

실무에서는 **상황에 따라 두 가지 방식을 혼용**합니다.

**1) 분산 락 방식 (Redisson)**
- **적용 시나리오**: 재고가 매우 적고(예: 10개 미만), 선착순 경쟁이 치열한 경우
- **장점**: 정확한 재고 관리, 오버셀 방지
- **단점**: 락 대기 시간으로 인한 응답 지연

**2) Atomic 연산 + Write-Back 방식**
- **적용 시나리오**: 재고가 충분하고(예: 100개 이상), 트래픽이 매우 높은 경우
- **구현 방식**:
  ```kotlin
  // Redis에서 Atomic 차감
  val remaining = redisTemplate.opsForValue().increment("stock:$productId", -quantity)
  if (remaining < 0) {
      // 롤백: 다시 증가
      redisTemplate.opsForValue().increment("stock:$productId", quantity)
      throw InsufficientStockException()
  }
  
  // 비동기로 DB 반영 (Kafka 이벤트 발행)
  eventPublisher.publish(StockDecreasedEvent(productId, quantity))
  ```

**데이터 불일치 리스크 관리:**
1. **정기적 동기화 작업**: 매 시간마다 Redis 재고와 DB 재고를 비교하여 불일치 감지
2. **이벤트 재처리**: Kafka Consumer에서 실패한 이벤트는 Dead Letter Queue로 이동 후 수동 재처리
3. **보상 트랜잭션**: 주문 취소 시 Redis 재고를 다시 증가시키는 로직
4. **모니터링**: Redis 재고와 DB 재고의 차이가 임계치(예: 5%)를 넘으면 알림

**실무 팁**: 대부분의 경우 **하이브리드 접근**이 효과적입니다. 핫딜 상품은 분산 락, 일반 상품은 Atomic 연산을 사용하세요.

---

## Q2. 캐시 스탬피드와 핫딜 상품 (Cache Stampede)

**답변:**

실무에서는 **Mutex Lock + TTL Jitter 조합**을 가장 많이 사용합니다.

**1) Mutex Lock (가장 효과적)**
- Spring Cache의 `sync = true` 옵션 활용
- 동일 JVM 내에서는 완벽하게 동작
- 다중 인스턴스 환경에서는 Redis 분산 락 추가

```kotlin
@Cacheable(
    cacheNames = [CacheNames.POPULAR_PRODUCTS],
    key = "#days + ':' + #limit",
    sync = true  // 싱글 플라이트
)
fun getTopProducts(days: Int, limit: Int): List<ProductDto> {
    // 첫 번째 스레드만 DB 조회, 나머지는 대기
}
```

**2) TTL Jitter (보조 전략)**
- 기본 TTL에 ±10% 랜덤 추가
- 만료 시점을 분산시켜 부하 완화

**3) PER (Probabilistic Early Recomputation)**
- 복잡도가 높아 실무에서는 잘 사용하지 않음
- 다만, 매우 중요한 데이터(예: 실시간 랭킹)에는 적용 가능

**실무 팁**: 
- **핫딜 상품**: Mutex Lock 필수
- **일반 인기 상품**: TTL Jitter만으로도 충분
- **백그라운드 갱신**: TTL의 80% 시점에 스케줄러로 미리 갱신

---

## Q3. 캐시 정합성과 쓰기 전략 (Cache Consistency)

**답변:**

**주문/결제와 직결된 중요 데이터**는 **Write-Through + 즉시 무효화**를 사용합니다.

**1) 재고, 가격 정보**
```kotlin
@Transactional
fun updateProductPrice(productId: Long, newPrice: Money) {
    // 1. DB 업데이트
    val product = productRepository.findById(productId)
        .orElseThrow { ProductNotFoundException(productId) }
    product.updatePrice(newPrice)
    productRepository.save(product)
    
    // 2. 캐시 즉시 무효화
    cacheManager.getCache(CacheNames.PRODUCT_DETAIL)?.evict(productId)
    cacheManager.getCache(CacheNames.PRODUCT_LIST)?.clear() // 관련 목록도 무효화
}
```

**2) 보상 트랜잭션**
- 주문 실패 시: Redis 재고 롤백 + 캐시 무효화
- 결제 실패 시: 주문 상태 롤백 + 관련 캐시 무효화

**3) 정합성 검증**
- 주기적 배치 작업으로 캐시와 DB 비교
- 불일치 발견 시 자동 복구 (DB를 Source of Truth로 간주)

**실무 팁**: 
- **금액, 재고**: Write-Through 필수
- **조회수, 좋아요**: Write-Back 허용
- **보상 로직**: 항상 DB를 기준으로 복구

---

## Q4. 분산 락과 트랜잭션의 범위 (Lock & Transaction Scope)

**답변:**

**락 범위 최소화**는 성능의 핵심입니다. 다음 기법을 사용합니다:

**1) 비즈니스 로직 분리**
```kotlin
fun decreaseStock(productId: Long, quantity: Int) {
    val lock = redissonClient.getLock("lock:stock:$productId")
    try {
        lock.lock(3, 10, TimeUnit.SECONDS)
        
        // 락 내부에서는 최소한의 작업만 수행
        val currentStock = getCurrentStock(productId) // 빠른 조회
        if (currentStock < quantity) {
            throw InsufficientStockException()
        }
        
        // 실제 DB 업데이트는 락 해제 후 트랜잭션으로
        decreaseStockInternal(productId, quantity)
    } finally {
        lock.unlock()
    }
}

@Transactional
private fun decreaseStockInternal(productId: Long, quantity: Int) {
    // DB 업데이트 (락 해제 후 실행)
}
```

**2) 외부 API 호출 제거**
- 락 내부에서는 외부 API 호출 금지
- 락 해제 후 비동기로 처리

**3) 퍼사드 패턴 활용**
- 복잡한 비즈니스 로직은 별도 서비스로 분리
- 락은 최소한의 검증만 수행

**실무 팁**: 
- 락을 잡고 있는 시간은 **10ms 이하**를 목표로 하세요
- 외부 호출, 복잡한 계산은 모두 락 밖으로 빼세요

---

## Q5. Redis 클라이언트 선택 (Lettuce vs Redisson)

**답변:**

**하이브리드 방식이 실무 표준**입니다. 대부분의 대규모 커머스 서비스가 이 방식을 사용합니다.

**1) 일반 캐싱: Lettuce**
- Spring Boot 기본 설정 그대로 사용
- `@Cacheable` 등 선언적 캐싱에 활용

**2) 분산 락: Redisson**
- 재고 차감, 선착순 이벤트 등에만 사용
- 필요한 빈에만 주입

**3) 운영 복잡도 관리**
- 두 라이브러리 모두 같은 Redis 인스턴스 사용 가능
- 설정 파일로 환경별로 쉽게 전환 가능

**실무 팁**: 
- 단순 캐싱만 필요하면 Lettuce만 사용
- 분산 락이 필요하면 Redisson 추가
- **하나로 통일하는 것은 비추천** (각각의 장점을 포기하게 됨)

---

## Q6. 로컬 캐시와 글로벌 캐시의 조화 (Local vs Global Cache)

**답변:**

**관리 포인트 증가를 우려하여 대부분 Redis만 사용**합니다. 다만 특수한 경우에만 로컬 캐시를 사용합니다.

**1) Redis만 사용하는 경우 (일반적)**
- 카테고리 목록, 공통 코드 등도 Redis에 저장
- TTL을 길게 설정 (예: 1시간)
- 관리 포인트가 적어 운영이 편함

**2) 로컬 캐시를 사용하는 경우 (예외적)**
- **매우 자주 조회되고 거의 변하지 않는 데이터**: 설정 정보, 상수값
- **네트워크 지연이 치명적인 경우**: 실시간 추천 알고리즘의 중간 결과

**3) Pub/Sub 동기화 (복잡도 높음)**
- 실무에서는 잘 사용하지 않음
- 구현 복잡도 대비 이득이 적음

**실무 팁**: 
- **기본은 Redis만 사용**
- 로컬 캐시는 성능 개선이 명확히 필요할 때만 도입
- 도입 시에는 모니터링 강화 필수

---

## Q7. 분산 트랜잭션과 Saga 패턴 (Distributed Transaction)

**답변:**

**강한 일관성이 필요한 구간은 매우 제한적**입니다. 대부분 결과적 일관성으로 해결합니다.

**1) 강한 일관성(2PC)이 필요한 경우**
- **결제 금액 검증**: 주문 금액과 결제 금액이 정확히 일치해야 함
- **재고 차감**: 주문과 재고 차감이 원자적으로 처리되어야 함
- **포인트 사용**: 포인트 차감과 주문 생성이 함께 성공/실패해야 함

**2) 결과적 일관성(Saga)으로 충분한 경우**
- **주문-배송**: 주문이 생성된 후 배송 정보는 나중에 업데이트되어도 됨
- **리뷰 작성**: 주문 완료 후 리뷰는 언제든 작성 가능
- **알림 발송**: 주문 생성 후 알림은 지연되어도 문제없음

**3) 실무 기준**
```
강한 일관성 필요:
- 금액 관련 (결제, 환불)
- 재고 차감
- 포인트 사용/충전

결과적 일관성 허용:
- 로그, 알림
- 통계, 집계
- 부가 서비스 (리뷰, 추천)
```

**실무 팁**: 
- **기본은 Saga 패턴**
- 2PC는 정말 필요한 경우에만 사용 (성능 저하 큼)
- 보상 트랜잭션은 항상 구현

---

## Q8. Redis 운영 및 장애 대응 (Redis HA)

**답변:**

**Redis Cluster/Sentinel 구성 + 서킷 브레이커 조합**을 사용합니다.

**1) HA 구성**
- **프로덕션**: Redis Cluster (수평 확장 가능)
- **스테이징/개발**: Sentinel (구성 단순)

**2) Fallback 전략**
- **서킷 브레이커 패턴**: Redis 장애 시 자동으로 DB 조회로 전환
- **부분 장애 대응**: 특정 키만 실패하는 경우 해당 키만 DB 조회

```kotlin
@Service
class ProductService(
    private val redisTemplate: RedisTemplate<String, Any>,
    private val productRepository: ProductRepository,
    private val circuitBreaker: CircuitBreaker
) {
    fun getProduct(id: Long): Product {
        return circuitBreaker.executeSupplier {
            val cached = redisTemplate.opsForValue().get("product:$id") as? Product
            cached ?: productRepository.findById(id)
                .orElseThrow { ProductNotFoundException(id) }
        }
    }
}
```

**3) 서킷 브레이커 설정**
- **실패율 임계치**: 50% 이상 실패 시 Open
- **타임아웃**: 100ms 이상 응답 시 실패로 간주
- **Half-Open 간격**: 30초마다 재시도

**실무 팁**: 
- **모든 조회 코드에 Fallback 로직 추가** (코드 복잡도 증가하지만 안정성 확보)
- Redis 장애는 드물지만, 발생 시 치명적이므로 철저히 대비

---

## Q9. 캐시 만료 및 방출 정책 (Expiration & Eviction)

**답변:**

**데이터 특성에 따라 인스턴스 분리 + 정책 분리**를 사용합니다.

**1) 인스턴스 분리 (권장)**
```
Redis-1 (캐시용): allkeys-lru
- 상품 정보, 카테고리 목록
- TTL 기반 만료 + LRU 방출

Redis-2 (세션용): volatile-lru  
- 사용자 세션, 장바구니
- TTL 필수, 만료된 것만 방출

Redis-3 (락용): noeviction
- 분산 락, 임시 데이터
- TTL로만 관리, 방출 없음
```

**2) 정책 선택 기준**
- **LRU**: 일반적인 캐시 데이터 (상품 정보, 목록)
- **LFU**: 자주 조회되는 데이터 (인기 상품, 베스트셀러)
- **TTL 기반**: 시간에 민감한 데이터 (세션, 임시 토큰)

**3) 실무 설정**
```redis
# 캐시용 Redis
maxmemory 8gb
maxmemory-policy allkeys-lru

# 세션용 Redis  
maxmemory 4gb
maxmemory-policy volatile-lru
```

**실무 팁**: 
- **용도별 인스턴스 분리**가 운영과 비용 관리에 유리
- 단일 인스턴스 사용 시에는 `allkeys-lru` 권장

---

## Q10. 모니터링과 지표 (Monitoring)

**답변:**

**다음 지표들을 실시간 모니터링**합니다:

**1) 핵심 지표 (필수)**
- **Hit Rate**: 80% 이상 유지 목표
- **응답 시간 (P50, P95, P99)**: P95 < 10ms 목표
- **메모리 사용률**: 80% 이상 시 알림
- **연결 수 (Connected Clients)**: 정상 범위 유지

**2) 성능 지표**
- **Slowlog**: 10ms 이상 쿼리 분석
- **Commands per second**: 처리량 모니터링
- **Network I/O**: 네트워크 병목 감지

**3) 안정성 지표**
- **에러율**: 0.1% 이하 유지
- **타임아웃율**: 0.01% 이하 유지
- **Fragmentation Ratio**: 1.5 이상 시 알림

**4) 임계치 설정 예시**
```yaml
alerts:
  - metric: hit_rate
    threshold: < 70%
    severity: warning
    
  - metric: memory_usage
    threshold: > 85%
    severity: critical
    
  - metric: p95_latency
    threshold: > 50ms
    severity: warning
```

**실무 팁**: 
- **Hit Rate만으로는 부족**, 응답 시간과 에러율을 함께 봐야 함
- Slowlog는 주기적으로 분석하여 최적화 포인트 발견

---

## Q11. 캐시 키 설계 및 네임스페이스 전략 (Cache Key Design)

**답변:**

**계층적 네임스페이스 + 태그 기반 무효화**를 사용합니다.

**1) 키 네이밍 컨벤션**
```
{서비스}:{도메인}:{타입}:{식별자}:{옵션}

예시:
- joopang:product:detail:123
- joopang:product:list:category:1:page:1
- joopang:user:cart:456
```

**2) 태그 기반 무효화 (Redis Sets 활용)**
```kotlin
// 캐시 저장 시 태그 추가
fun cacheProduct(product: Product) {
    val key = "product:detail:${product.id}"
    redisTemplate.opsForValue().set(key, product)
    
    // 태그에 키 추가
    redisTemplate.opsForSet().add("tag:product:${product.id}", key)
    redisTemplate.opsForSet().add("tag:category:${product.categoryId}", key)
}

// 상품 업데이트 시 관련 캐시 모두 무효화
fun invalidateProductCache(productId: Long) {
    val keys = redisTemplate.opsForSet().members("tag:product:$productId")
    keys?.forEach { redisTemplate.delete(it.toString()) }
}
```

**3) 이벤트 기반 무효화**
- 상품 업데이트 이벤트 발행
- 각 서비스에서 구독하여 관련 캐시 무효화

**실무 팁**: 
- **태그 기반 무효화**가 가장 실용적
- 이벤트 기반은 마이크로서비스 환경에서 유용

---

## Q12. 멀티 레벨 캐싱 전략 (Multi-Level Caching)

**답변:**

**멀티 레벨 캐싱은 특수한 경우에만 사용**합니다.

**1) 사용 시나리오**
- **초고속 응답이 필요한 API**: 실시간 추천, 개인화된 상품 목록
- **네트워크 지연이 큰 환경**: 멀티 리전 배포 시

**2) TTL 설정 전략**
```kotlin
// 로컬 캐시: 30초 (짧게)
@Cacheable(cacheNames = ["local:products"], ttl = 30)
fun getProductLocal(id: Long): Product

// Redis 캐시: 10분 (길게)
@Cacheable(cacheNames = ["redis:products"], ttl = 600)
fun getProductRedis(id: Long): Product
```

**3) 불일치 관리**
- 로컬 캐시 TTL을 Redis TTL보다 짧게 설정
- 이벤트 기반 무효화 (복잡도 높음)

**실무 팁**: 
- **대부분의 경우 Redis만으로 충분**
- 멀티 레벨은 성능 개선이 명확히 필요할 때만 도입
- 도입 시 모니터링과 운영 복잡도 증가 고려

---

## Q13. Redis Cluster vs Sentinel 선택 기준 (High Availability)

**답변:**

**트래픽과 데이터 크기를 기준으로 선택**합니다.

**1) 선택 기준**
```
Cluster 선택:
- 트래픽: 초당 10,000 요청 이상
- 데이터 크기: 10GB 이상
- 수평 확장 필요

Sentinel 선택:
- 트래픽: 초당 10,000 요청 미만
- 데이터 크기: 10GB 미만
- 단순한 구성 선호
```

**2) 운영 복잡도**
- **Cluster**: 샤딩 키 관리, 마이그레이션 복잡
- **Sentinel**: 구성 단순, 운영 편함

**3) 장애 복구 시간**
- **Cluster**: 노드 장애 시 자동 페일오버 (수십 초)
- **Sentinel**: 마스터 장애 시 페일오버 (1-2분)

**실무 팁**: 
- **초기에는 Sentinel로 시작**
- 트래픽 증가 시 Cluster로 전환
- 전환은 점진적으로 (샤딩 없이 시작 후 점진적 확장)

---

## Q14. 캐시 워밍업 및 초기 로딩 전략 (Cache Warming)

**답변:**

**우선순위 기반 배치 워밍업 + Lazy Loading 조합**을 사용합니다.

**1) 워밍업 우선순위**
```
1순위: 인기 상품 Top 100 (조회 빈도 최고)
2순위: 카테고리별 베스트 상품
3순위: 메인 페이지 노출 상품
4순위: 최근 등록 상품
```

**2) 구현 방식**
```kotlin
@Component
class CacheWarmingService(
    private val productService: ProductService
) {
    @PostConstruct
    fun warmupCache() {
        // 비동기로 워밍업 (서비스 시작 블로킹 방지)
        CompletableFuture.runAsync {
            productService.getTopProducts(7, 100) // 인기 상품
            productService.getTopProducts(30, 100) // 월간 베스트
            // ...
        }
    }
}
```

**3) Lazy Loading 보완**
- 첫 요청 시 백그라운드로 캐시 채우기
- 워밍업 실패 시에도 서비스 영향 최소화

**실무 팁**: 
- **워밍업은 선택사항**, Lazy Loading만으로도 충분한 경우 많음
- 워밍업은 서비스 시작 시간에 영향 주지 않도록 비동기로

---

## Q15. 캐시 직렬화 및 성능 최적화 (Serialization & Performance)

**답변:**

**대부분의 경우 JSON으로 충분**하지만, 특수한 경우 바이너리 포맷을 사용합니다.

**1) JSON (일반적)**
- 가독성 좋음, 디버깅 편함
- 대부분의 데이터에 적합

**2) MessagePack/Protobuf (특수한 경우)**
- **대용량 리스트**: Top 1000 상품 목록 등
- **고빈도 조회**: 초당 수만 건 조회되는 데이터

**3) Redis Hash 구조 (대안)**
- 객체의 일부 필드만 조회하는 경우
- 메모리 효율적

```kotlin
// Hash 구조로 분할 저장
fun cacheProduct(product: Product) {
    redisTemplate.opsForHash().put("product:$id", "name", product.name)
    redisTemplate.opsForHash().put("product:$id", "price", product.price)
    // 필요한 필드만 조회 가능
}
```

**4) 압축**
- **gzip 압축**: 1KB 이상 데이터에 적용
- 압축/해제 오버헤드 vs 네트워크 전송량 트레이드오프

**실무 팁**: 
- **기본은 JSON**, 성능 문제가 명확할 때만 최적화
- 최적화 전에 프로파일링으로 병목 확인

---

## Q16. 캐시와 비동기 처리의 조합 (Async Processing with Cache)

**답변:**

**이벤트 기반 비동기 무효화 + 재처리 메커니즘**을 사용합니다.

**1) 이벤트 발행**
```kotlin
@Transactional
fun createOrder(order: Order) {
    orderRepository.save(order)
    
    // 비동기로 캐시 무효화 이벤트 발행
    eventPublisher.publishAsync(
        CacheInvalidationEvent(
            keys = listOf(
                "user:orders:${order.userId}",
                "product:popular",
                "category:stats:${order.categoryId}"
            )
        )
    )
}
```

**2) 이벤트 재처리**
- **Dead Letter Queue**: 실패한 이벤트 저장
- **재시도 로직**: 지수 백오프로 재시도
- **수동 재처리**: 최종적으로 수동 처리 가능

**3) 결과적 일관성 관리**
- **최대 지연 허용 시간**: 5분 이내
- **주기적 검증**: 배치 작업으로 불일치 감지
- **자동 복구**: 불일치 발견 시 자동 무효화

**실무 팁**: 
- **이벤트 유실은 허용하되, 재처리 메커니즘 필수**
- 중요한 데이터는 동기 무효화도 병행

---

## Q17. 캐시 용량 계획 및 비용 최적화 (Capacity Planning)

**답변:**

**우선순위 기반 캐싱 + 데이터 최적화**로 비용을 관리합니다.

**1) 우선순위 결정 기준**
```
우선순위 = (조회 빈도 × 조회 비용) / 데이터 크기

예시:
- 인기 상품 상세: 높은 우선순위 (자주 조회, 복잡한 쿼리)
- 최근 본 상품: 낮은 우선순위 (개인별, 단순 조회)
```

**2) 메모리 절감 전략**
- **필드 최소화**: 필요한 필드만 캐싱
- **압축**: 대용량 데이터 압축 저장
- **TTL 최적화**: 자주 변하지 않는 데이터는 TTL 연장

**3) 인스턴스 분리**
```
캐시용: 8GB (allkeys-lru)
세션용: 4GB (volatile-lru)  
락용: 1GB (noeviction)
```

**실무 팁**: 
- **용도별 분리**가 비용 효율적 (각각 최적화 가능)
- 단일 인스턴스는 운영 편하지만 비용이 높을 수 있음

---

## Q18. 캐시와 DB 간 데이터 불일치 감지 및 복구 (Data Inconsistency Detection)

**답변:**

**주기적 검증 + 자동 복구 메커니즘**을 구축합니다.

**1) 불일치 감지**
```kotlin
@Scheduled(cron = "0 */10 * * * *") // 10분마다
fun detectInconsistency() {
    val products = productRepository.findAll()
    products.forEach { product ->
        val cached = redisTemplate.opsForValue()
            .get("product:${product.id}") as? Product
        if (cached != null && cached.price != product.price) {
            // 불일치 발견 → 자동 복구
            recoverInconsistency(product.id, product)
        }
    }
}
```

**2) 자동 복구**
- **DB를 Source of Truth로 간주**
- 불일치 발견 시 캐시를 DB 값으로 갱신
- 알림 발송 (모니터링)

**3) 정합성 보장 수준**
- **재고, 가격**: Write-Through로 강한 일관성
- **조회수, 통계**: Write-Back 허용, 주기적 동기화

**실무 팁**: 
- **중요 데이터는 Write-Through 필수**
- 불일치 허용 데이터는 주기적 검증으로 충분

---

## Q19. 대규모 트래픽에서의 캐시 성능 병목 진단 (Performance Bottleneck Analysis)

**답변:**

**체계적인 진단 프로세스**를 따릅니다.

**1) 진단 단계**
```
1. Redis Slowlog 분석 (10ms 이상 쿼리)
2. 네트워크 지연 측정 (Redis 클라이언트 ↔ 서버)
3. 직렬화/역직렬화 시간 측정
4. 메모리 사용 패턴 분석
```

**2) 즉시 적용 가능한 최적화**
- **Pipeline 사용**: 여러 명령을 한 번에 전송
- **Connection Pool 최적화**: 적절한 풀 크기 설정
- **직렬화 최적화**: 불필요한 필드 제거

**3) 서킷 브레이커 + Rate Limiting**
```kotlin
@Bean
fun circuitBreaker(): CircuitBreaker {
    return CircuitBreaker.of("redis", CircuitBreakerConfig.custom()
        .failureRateThreshold(50f)
        .waitDurationInOpenState(Duration.ofSeconds(30))
        .build())
}

// Rate Limiting
@RateLimiter(name = "redis")
fun getProduct(id: Long): Product {
    // ...
}
```

**실무 팁**: 
- **Slowlog는 매일 확인**하여 최적화 포인트 발견
- 서킷 브레이커는 Redis 장애 시 필수

---

## Q20. 캐시를 활용한 비즈니스 로직 최적화 (Business Logic with Cache)

**답변:**

**Atomic 연산 + 분산 락 조합**으로 원자성을 보장합니다.

**1) 재고 예약**
```kotlin
fun reserveStock(productId: Long, quantity: Int): Boolean {
    // Lua Script로 원자적 처리
    val script = """
        local current = redis.call('GET', KEYS[1])
        if tonumber(current) >= tonumber(ARGV[1]) then
            redis.call('DECRBY', KEYS[1], ARGV[1])
            redis.call('SETEX', KEYS[2], 300, ARGV[1]) -- 5분 예약
            return 1
        end
        return 0
    """
    val result = redisTemplate.execute(
        DefaultRedisScript(script, Long::class.java),
        listOf("stock:$productId", "reservation:$productId:$userId"),
        quantity.toString()
    )
    return result == 1L
}
```

**2) 실패 시 롤백**
- 예약 실패 시 이미 차감된 재고 복구
- 트랜잭션 로그를 Redis에 저장하여 롤백 가능

**실무 팁**: 
- **단순 카운터는 Atomic 연산만으로 충분**
- **복잡한 로직은 Lua Script + 분산 락 조합**

---

## Q21. 마이크로서비스 환경에서의 캐시 아키텍처 (Cache Architecture in MSA)

**답변:**

**서비스별 독립 캐시 + 공유 캐시 하이브리드**를 사용합니다.

**1) 캐시 소유권 원칙**
```
서비스별 독립 캐시:
- 서비스 내부 데이터 (주문 내역, 사용자 프로필)
- 서비스 전용 집계 데이터

공유 캐시:
- 공통 데이터 (상품 정보, 카테고리)
- 크로스 서비스 조회가 빈번한 데이터
```

**2) 데이터 동기화**
- **이벤트 기반**: 상품 업데이트 이벤트 발행
- **CDC (Change Data Capture)**: DB 변경사항을 실시간 캡처

**실무 팁**: 
- **기본은 서비스별 독립 캐시**
- 공유 캐시는 신중하게 선택 (결합도 증가)

---

## Q22. 복잡한 캐시 무효화 시나리오 처리 (Complex Cache Invalidation)

**답변:**

**의존성 그래프 기반 무효화**를 사용합니다.

**1) 의존성 정의**
```kotlin
val cacheDependencies = mapOf(
    "product:detail" to listOf(
        "product:list:*",
        "category:products:*",
        "search:results:*"
    )
)
```

**2) 무효화 전략**
- **부분 무효화**: 변경된 상품만 무효화 (일반적)
- **전체 무효화**: 대규모 변경 시 (드물게)

**3) 재시도 메커니즘**
- 실패한 무효화는 재시도 큐에 저장
- 지수 백오프로 재시도

**실무 팁**: 
- **태그 기반 무효화**가 가장 실용적
- 순환 참조는 설계 단계에서 방지

---

## Q23. Redis 고급 기능 활용 전략 (Advanced Redis Features)

**답변:**

**상황에 맞게 선택적 활용**합니다.

**1) Stream**
- **이벤트 로그**: 주문 이벤트, 사용자 행동 로그
- **메시지 큐**: 간단한 큐가 필요할 때

**2) Pub/Sub**
- **실시간 알림**: 주문 상태 변경 알림
- **캐시 무효화**: 서비스 간 캐시 동기화

**3) Lua Script**
- **원자적 연산**: 재고 차감, 포인트 사용
- **복잡한 비즈니스 로직**: 단일 원자적 실행 필요 시

**4) Redis Modules**
- **RediSearch**: 상품 검색 기능
- 도입 시 성능과 기능을 신중히 평가

**실무 팁**: 
- **기본 기능으로 해결 가능하면 기본 기능 사용**
- 고급 기능은 명확한 필요성이 있을 때만 도입

---

## Q24. 캐시 전략의 ROI 측정 및 비즈니스 임팩트 (Cache ROI & Business Impact)

**답변:**

**정량적 지표 + 비즈니스 지표**를 함께 측정합니다.

**1) 정량적 지표**
```
DB 부하 감소: 70% 감소
응답 시간 개선: P95 500ms → 50ms
서버 리소스 절감: CPU 40% 감소
```

**2) 비즈니스 임팩트**
- **전환율**: 페이지 로딩 속도 개선으로 전환율 5% 향상
- **이탈률**: 응답 시간 개선으로 이탈률 3% 감소
- **사용자 만족도**: 페이지 로딩 속도 만족도 향상

**3) ROI 계산**
```
캐시 도입 비용: Redis 인프라 비용
절감 효과: 서버 리소스 절감 + DB 부하 감소
ROI = (절감 효과 - 도입 비용) / 도입 비용 × 100
```

**실무 팁**: 
- **비즈니스 임팩트를 정량화**하여 경영진에게 보고
- A/B 테스트로 캐시 효과 검증

---

## Q25. 장애 상황에서의 캐시 복구 및 데이터 복구 (Cache Recovery & Data Recovery)

**답변:**

**백업 기반 복구 + 점진적 재구축**을 사용합니다.

**1) 복구 프로세스**
```
1. RDB/AOF 백업으로 데이터 복구
2. 캐시 미스율 모니터링
3. 우선순위 기반 점진적 재구축
4. 정상화 확인
```

**2) 부분 장애 대응**
- 특정 키만 손실: 해당 키만 DB에서 재로드
- 패턴 기반 복구: `product:*` 키만 재구축

**3) DB 보호**
- 캐시 미스율 급증 시 Rate Limiting 적용
- 서킷 브레이커로 DB 보호

**실무 팁**: 
- **정기적 백업 필수** (RDB + AOF)
- 복구 시나리오를 정기적으로 테스트

---

## Q26. 캐시 데이터의 보안 및 접근 제어 (Cache Security & Access Control)

**답변:**

**암호화 + 접근 제어 + 감사 로그**를 적용합니다.

**1) 암호화**
- **전송 중 암호화**: TLS/SSL 필수
- **저장 시 암호화**: 민감 정보는 암호화 저장

**2) 접근 제어**
- **네트워크 격리**: Redis는 내부 네트워크만 접근 가능
- **인증**: AUTH 패스워드 설정
- **ACL (Access Control List)**: 역할 기반 접근 제어

**3) 다중 테넌트 격리**
- **네임스페이스 분리**: `tenant:1:product:*`
- **별도 인스턴스**: 중요 테넌트는 독립 인스턴스

**4) 감사 로그**
- 모든 접근 로그 기록
- 이상 패턴 감지

**실무 팁**: 
- **민감 정보는 캐시하지 않는 것이 최선**
- 캐시해야 한다면 암호화 필수

---

## Q27. 실시간 데이터 처리와 캐시의 통합 (Real-time Data & Cache Integration)

**답변:**

**이벤트 기반 동기화 + CDC 패턴**을 사용합니다.

**1) 이벤트 기반 동기화**
```kotlin
// 주문 생성 이벤트
@EventListener
fun onOrderCreated(event: OrderCreatedEvent) {
    // 관련 캐시 즉시 무효화
    cacheManager.getCache("user:orders")?.evict(event.userId)
    cacheManager.getCache("product:popular")?.clear()
}
```

**2) CDC (Change Data Capture)**
- DB 변경사항을 실시간 캡처
- 변경된 데이터만 캐시 갱신

**3) 트레이드오프 관리**
- **실시간성**: 중요 데이터는 즉시 동기화
- **성능**: 덜 중요한 데이터는 배치 동기화

**실무 팁**: 
- **이벤트 기반이 가장 실용적**
- CDC는 복잡도가 높아 신중히 도입

---

## Q28. 캐시 적용 기준 및 우선순위 결정 (Cache Application Criteria)

**답변:**

**캐시 적용 기준을 수치화**하여 객관적으로 판단합니다.

**1) 캐시 적용 점수 계산**
```kotlin
fun calculateCacheScore(
    readFrequency: Int,      // 초당 조회 수
    queryCost: Long,         // 쿼리 실행 시간 (ms)
    dataSize: Int,           // 데이터 크기 (KB)
    updateFrequency: Int     // 초당 업데이트 수
): Double {
    val readBenefit = readFrequency * queryCost / 1000.0
    val updateCost = updateFrequency * 0.1 // 캐시 무효화 비용
    val sizePenalty = dataSize / 100.0
    
    return (readBenefit - updateCost) / sizePenalty
}

// 점수가 10 이상이면 캐시 적용 권장
```

**2) 적용 기준 체크리스트**
```
✅ 캐시 적용 권장:
- 조회 빈도: 초당 10회 이상
- 쿼리 비용: 50ms 이상
- 데이터 변경: 시간당 1회 이하
- 조회/쓰기 비율: 10:1 이상

❌ 캐시 적용 비권장:
- 실시간성이 필수 (주문 상태, 결제 정보)
- 개인화된 데이터 (사용자별로 다름)
- 매우 큰 데이터 (10MB 이상)
- 자주 변경되는 데이터 (초당 10회 이상)
```

**3) ROI 계산**
```
캐시 도입 비용 = Redis 인프라 비용 + 개발 시간
절감 효과 = (DB 부하 감소 + 응답 시간 개선) × 트래픽
ROI = (절감 효과 - 도입 비용) / 도입 비용 × 100

목표: ROI > 200% (2배 이상 효과)
```

**실무 팁**: 
- **초기에는 보수적으로 적용**, 효과를 측정한 후 확장
- A/B 테스트로 캐시 효과 검증

---

## Q29. 캐시 적용하지 말아야 하는 경우 (When NOT to Cache)

**답변:**

**다음 경우에는 캐시를 적용하지 않습니다:**

**1) 실시간성이 필수인 데이터**
- 주문 상태, 결제 진행 상황
- 실시간 재고 (선착순 이벤트)
- **이유**: 캐시 지연으로 인한 사용자 혼란

**2) 개인화된 데이터**
- 사용자별 맞춤 추천 (계산 비용이 낮은 경우)
- 개인 설정 정보 (변경 빈도가 높은 경우)
- **이유**: 캐시 적중률이 낮아 효과 미미

**3) 매우 큰 데이터**
- 전체 상품 목록 (수만 개)
- 대용량 리포트 데이터
- **이유**: 메모리 비용 대비 효과 낮음

**4) 자주 변경되는 데이터**
- 실시간 랭킹 (초단위 변경)
- 활성 사용자 수
- **이유**: 무효화 비용이 조회 이득보다 큼

**5) 보안이 중요한 데이터**
- 인증 토큰 (캐시하면 보안 위험)
- 개인정보 (GDPR 등 규제)
- **이유**: 보안 리스크

**실무 팁**: 
- **의심스러우면 캐시하지 마세요**
- 나중에 추가하는 것이 제거하는 것보다 쉬움

---

## Q30. 단일 지점 장애(SPOF)와 Redis 고가용성 (Single Point of Failure)

**답변:**

**다층 방어 전략**으로 SPOF를 완화합니다.

**1) Redis HA 구성 (1차 방어)**
- **프로덕션**: Redis Cluster (자동 페일오버)
- **스테이징**: Sentinel (구성 단순)
- **목표**: 99.9% 가용성 (연간 8.76시간 다운타임)

**2) 서킷 브레이커 (2차 방어)**
```kotlin
@Bean
fun redisCircuitBreaker(): CircuitBreaker {
    return CircuitBreaker.of("redis", CircuitBreakerConfig.custom()
        .failureRateThreshold(50f)
        .waitDurationInOpenState(Duration.ofSeconds(30))
        .slidingWindowSize(10)
        .build())
}

fun getProduct(id: Long): Product {
    return redisCircuitBreaker.executeSupplier {
        val cached = redisTemplate.opsForValue().get("product:$id")
        cached ?: throw CacheMissException()
    } ?: productRepository.findById(id) // Fallback to DB
}
```

**3) 점진적 성능 저하 (Graceful Degradation)**
- **Level 1**: Redis 정상 → 캐시 사용
- **Level 2**: Redis 지연 → DB 직접 조회, 캐시는 백그라운드
- **Level 3**: Redis 장애 → DB만 사용, Rate Limiting 적용

**4) 다중 Redis 인스턴스 (고가용성)**
- **Primary/Secondary**: 자동 페일오버
- **지역별 분산**: 리전별 Redis 인스턴스

**실무 팁**: 
- **HA 구성은 필수**, 서킷 브레이커는 선택
- 장애 시나리오를 정기적으로 테스트

---

## Q31. 분산 락의 단일 지점 장애 대응 (Distributed Lock SPOF)

**답변:**

**다중 Redis 인스턴스 기반 락**으로 SPOF를 해결합니다.

**1) Redlock 알고리즘**
```kotlin
fun acquireRedlock(lockKey: String, ttl: Long): Boolean {
    val redisInstances = listOf(redis1, redis2, redis3, redis4, redis5)
    val startTime = System.currentTimeMillis()
    var successCount = 0
    
    // 과반수 이상에서 락 획득 필요
    redisInstances.forEach { redis ->
        if (redis.setIfAbsent(lockKey, "locked", ttl)) {
            successCount++
        }
    }
    
    val elapsed = System.currentTimeMillis() - startTime
    return successCount >= (redisInstances.size / 2 + 1) && 
           elapsed < ttl
}
```

**2) DB 락으로 Fallback**
```kotlin
fun acquireLockWithFallback(lockKey: String): Lock {
    return try {
        redissonClient.getLock(lockKey) // Redis 락 시도
    } catch (e: Exception) {
        // Redis 장애 시 DB Named Lock으로 Fallback
        databaseLockService.acquireNamedLock(lockKey)
    }
}
```

**3) 실무 권장사항**
- **대부분의 경우 단일 Redis + Sentinel으로 충분**
- Redlock은 매우 중요한 비즈니스 로직에만 사용
- DB 락 Fallback은 복잡도가 높아 신중히 도입

**실무 팁**: 
- **단일 Redis + 모니터링**이 Redlock보다 실용적
- 락 서버 장애는 드물지만, 발생 시 치명적이므로 대비 필수

---

## Q32. 캐시키 구조화 및 네임스페이스 설계 (Cache Key Structure)

**답변:**

**계층적 네임스페이스 + 일관된 컨벤션**을 사용합니다.

**1) 키 구조 원칙**
```
{서비스}:{도메인}:{타입}:{식별자}:{옵션}

예시:
- joopang:product:detail:123
- joopang:product:list:category:1:page:1:sort:price
- joopang:user:cart:456
- joopang:order:summary:789
```

**2) 키 버전 관리**
```kotlin
object CacheKeys {
    private const val VERSION = "v2"
    
    fun productDetail(id: Long) = "joopang:$VERSION:product:detail:$id"
    fun productList(categoryId: Long, page: Int) = 
        "joopang:$VERSION:product:list:category:$categoryId:page:$page"
}
```

**3) 패턴 기반 조회/삭제**
```kotlin
// 패턴으로 키 검색 (SCAN 사용)
fun deleteByPattern(pattern: String) {
    val keys = mutableListOf<String>()
    var cursor = "0"
    
    do {
        val result = redisTemplate.execute { connection ->
            connection.scan(ScanOptions.scanOptions()
                .match(pattern)
                .count(100)
                .build())
        }
        keys.addAll(result?.map { String(it) } ?: emptyList())
        cursor = result?.nextCursorId?.toString() ?: "0"
    } while (cursor != "0")
    
    if (keys.isNotEmpty()) {
        redisTemplate.delete(keys)
    }
}

// 사용 예시
deleteByPattern("joopang:product:list:category:1:*")
```

**4) 키 길이 최적화**
- **너무 짧으면**: 충돌 위험
- **너무 길면**: 메모리 낭비
- **권장**: 50-100자 이내

**실무 팁**: 
- **일관된 컨벤션**이 가장 중요
- 키 구조 변경 시 마이그레이션 계획 수립

---

## Q33. 캐시키 버전 관리 및 마이그레이션 (Cache Key Versioning)

**답변:**

**키에 버전 포함 + 점진적 마이그레이션**을 사용합니다.

**1) 버전 관리 전략**
```kotlin
// 버전을 키에 포함
object CacheKeys {
    const val CURRENT_VERSION = "v2"
    
    fun productDetail(id: Long, version: String = CURRENT_VERSION) = 
        "joopang:$version:product:detail:$id"
}

// 사용 시
cacheManager.getCache("products")?.put(
    CacheKeys.productDetail(123, "v2"), 
    product
)
```

**2) 점진적 마이그레이션**
```kotlin
fun getProduct(id: Long): Product {
    // 새 버전 먼저 시도
    val v2Key = CacheKeys.productDetail(id, "v2")
    val cached = redisTemplate.opsForValue().get(v2Key) as? Product
    if (cached != null) return cached
    
    // 구 버전 Fallback
    val v1Key = CacheKeys.productDetail(id, "v1")
    val oldCached = redisTemplate.opsForValue().get(v1Key) as? Product
    if (oldCached != null) {
        // 새 버전으로 마이그레이션
        redisTemplate.opsForValue().set(v2Key, oldCached)
        return oldCached
    }
    
    // DB 조회
    return productRepository.findById(id)
}
```

**3) 구 버전 정리**
- 마이그레이션 완료 후 배치 작업으로 구 버전 삭제
- TTL을 짧게 설정하여 자연 만료 유도

**실무 팁**: 
- **버전 변경은 신중하게**, 하위 호환성 유지
- 마이그레이션은 점진적으로, 롤백 계획 수립

---

## Q34. 캐시 계층 구조 및 의존성 관리 (Cache Hierarchy & Dependencies)

**답변:**

**의존성 그래프 기반 자동 무효화**를 구현합니다.

**1) 의존성 정의**
```kotlin
class CacheDependencyManager {
    private val dependencies = mapOf(
        "product:detail" to setOf(
            "product:list:*",
            "category:products:*",
            "search:results:*",
            "recommendation:products:*"
        ),
        "category:detail" to setOf(
            "category:products:*",
            "product:list:category:*"
        )
    )
    
    fun invalidateWithDependencies(key: String) {
        // 직접 키 무효화
        redisTemplate.delete(key)
        
        // 의존성 키들도 무효화
        val dependentPatterns = dependencies[key] ?: emptySet()
        dependentPatterns.forEach { pattern ->
            deleteByPattern(pattern)
        }
    }
}
```

**2) 순환 의존성 방지**
```kotlin
fun validateDependencies() {
    val visited = mutableSetOf<String>()
    val recursionStack = mutableSetOf<String>()
    
    fun hasCycle(node: String): Boolean {
        if (recursionStack.contains(node)) return true
        if (visited.contains(node)) return false
        
        visited.add(node)
        recursionStack.add(node)
        
        dependencies[node]?.forEach { dep ->
            if (hasCycle(dep)) return true
        }
        
        recursionStack.remove(node)
        return false
    }
    
    dependencies.keys.forEach { key ->
        if (hasCycle(key)) {
            throw CircularDependencyException("Circular dependency detected: $key")
        }
    }
}
```

**3) 태그 기반 무효화 (대안)**
```kotlin
// 캐시 저장 시 태그 추가
fun cacheWithTags(key: String, value: Any, tags: Set<String>) {
    redisTemplate.opsForValue().set(key, value)
    tags.forEach { tag ->
        redisTemplate.opsForSet().add("tag:$tag", key)
    }
}

// 태그로 일괄 무효화
fun invalidateByTag(tag: String) {
    val keys = redisTemplate.opsForSet().members("tag:$tag")
    keys?.forEach { redisTemplate.delete(it.toString()) }
}
```

**실무 팁**: 
- **태그 기반이 가장 실용적**, 의존성 그래프는 복잡도 높음
- 순환 의존성은 설계 단계에서 방지

---

## Q35. 분산 락 타임아웃 및 데드락 방지 (Distributed Lock Timeout & Deadlock)

**답변:**

**TTL 기반 자동 해제 + Watchdog 메커니즘**을 사용합니다.

**1) 타임아웃 설정 기준**
```kotlin
fun calculateLockTimeout(operation: String): Long {
    return when (operation) {
        "stock_decrease" -> 5_000L  // 5초 (빠른 작업)
        "order_creation" -> 10_000L  // 10초 (중간 작업)
        "payment_processing" -> 30_000L // 30초 (느린 작업)
        else -> 10_000L
    }
}

val lock = redissonClient.getLock("lock:$key")
lock.lock(calculateLockTimeout("stock_decrease"), TimeUnit.MILLISECONDS)
```

**2) Watchdog 메커니즘 (Redisson)**
- Redisson은 자동으로 락을 갱신 (TTL의 1/3 시점)
- 프로세스가 살아있으면 락 유지, 죽으면 자동 해제

**3) 자체 구현 Watchdog**
```kotlin
class LockWatchdog(
    private val redisTemplate: RedisTemplate<String, String>,
    private val lockKey: String,
    private val ttl: Long
) {
    private var isRunning = true
    private val watchdogThread = Thread {
        while (isRunning) {
            Thread.sleep(ttl / 3) // TTL의 1/3마다 갱신
            if (isRunning) {
                redisTemplate.expire(lockKey, Duration.ofMillis(ttl))
            }
        }
    }
    
    fun start() {
        watchdogThread.start()
    }
    
    fun stop() {
        isRunning = false
        watchdogThread.interrupt()
    }
}
```

**4) 데드락 감지**
- **락 타임아웃**: TTL로 자동 해제
- **락 순서**: 항상 같은 순서로 락 획득 (예: ID 오름차순)
- **타임아웃 모니터링**: 락 대기 시간이 길면 알림

**실무 팁**: 
- **TTL은 작업 시간의 2-3배**로 설정
- Redisson Watchdog 사용 권장 (자동 갱신)

---

## Q36. 분산 락 성능 최적화 및 경합 최소화 (Distributed Lock Performance)

**답변:**

**락 세분화 + 비동기 처리 + 우선순위 큐**로 경합을 최소화합니다.

**1) 락 세분화**
```kotlin
// ❌ 나쁜 예: 전체 재고에 하나의 락
val lock = redissonClient.getLock("lock:stock")

// ✅ 좋은 예: 상품별로 락 분리
val lock = redissonClient.getLock("lock:stock:$productId")
```

**2) 비동기 처리**
```kotlin
fun decreaseStockAsync(productId: Long, quantity: Int) {
    CompletableFuture.supplyAsync {
        val lock = redissonClient.getLock("lock:stock:$productId")
        try {
            lock.lock(3, 10, TimeUnit.SECONDS)
            // 빠른 검증만 수행
            val current = getCurrentStock(productId)
            if (current < quantity) throw InsufficientStockException()
            
            // 실제 DB 업데이트는 락 해제 후
            decreaseStockInternal(productId, quantity)
        } finally {
            lock.unlock()
        }
    }
}
```

**3) 우선순위 큐 (고급)**
```kotlin
// Redis Sorted Set으로 우선순위 큐 구현
fun acquireLockWithPriority(lockKey: String, priority: Int): Boolean {
    val queueKey = "queue:$lockKey"
    val timestamp = System.currentTimeMillis()
    
    // 우선순위와 타임스탬프로 정렬
    redisTemplate.opsForZSet().add(
        queueKey, 
        Thread.currentThread().name,
        priority * 1_000_000.0 + timestamp
    )
    
    // 큐의 첫 번째가 자신이면 락 획득
    val first = redisTemplate.opsForZSet().range(queueKey, 0, 0)?.first()
    return first == Thread.currentThread().name
}
```

**4) 모니터링**
```kotlin
// 락 경합률 모니터링
fun monitorLockContention(lockKey: String) {
    val acquireAttempts = getCounter("lock:attempts:$lockKey")
    val acquireSuccess = getCounter("lock:success:$lockKey")
    val contentionRate = 1.0 - (acquireSuccess / acquireAttempts)
    
    if (contentionRate > 0.3) { // 30% 이상 경합
        alert("High lock contention: $lockKey")
    }
}
```

**실무 팁**: 
- **락 세분화가 가장 효과적**
- 경합률이 30% 이상이면 락 구조 재검토

---

## Q37. 캐시 히트율 최적화 전략 (Cache Hit Rate Optimization)

**답변:**

**TTL 최적화 + 데이터 선별 + 워밍업**으로 히트율을 높입니다.

**1) TTL 최적화**
```kotlin
// 데이터 특성에 따른 TTL 설정
fun getOptimalTTL(dataType: String, updateFrequency: Int): Long {
    return when {
        updateFrequency < 1 -> 3600L // 1시간 (거의 변하지 않음)
        updateFrequency < 10 -> 600L  // 10분 (가끔 변경)
        updateFrequency < 100 -> 60L  // 1분 (자주 변경)
        else -> 10L // 10초 (매우 자주 변경)
    }
}
```

**2) 데이터 선별**
```kotlin
// 인기도 기반 캐싱
fun shouldCache(productId: Long): Boolean {
    val popularity = getProductPopularity(productId)
    val cacheScore = popularity * getQueryCost(productId) / getDataSize(productId)
    return cacheScore > 10.0 // 임계값
}
```

**3) 캐시 워밍업**
```kotlin
@Scheduled(cron = "0 0 * * * *") // 매시간
fun warmupCache() {
    // 인기 상품 Top 1000 미리 로드
    val topProducts = productRepository.findTop1000ByPopularity()
    topProducts.forEach { product ->
        cacheManager.getCache("products")?.put(product.id, product)
    }
}
```

**4) 히트율 진단**
```kotlin
fun diagnoseLowHitRate(): List<String> {
    val issues = mutableListOf<String>()
    
    // TTL이 너무 짧은지 확인
    if (averageTTL < 60) {
        issues.add("TTL이 너무 짧습니다. 평균: ${averageTTL}초")
    }
    
    // 캐시 키가 너무 다양한지 확인
    val uniqueKeys = getUniqueKeyCount()
    if (uniqueKeys > 100_000) {
        issues.add("캐시 키가 너무 많습니다: $uniqueKeys")
    }
    
    // 데이터 변경 빈도 확인
    if (updateFrequency > readFrequency) {
        issues.add("업데이트가 조회보다 많습니다")
    }
    
    return issues
}
```

**실무 팁**: 
- **목표 히트율: 80% 이상**
- 낮은 히트율은 보통 TTL 문제 또는 키 다양성 문제

---

## Q38. 캐시 메모리 사용량 예측 및 계획 (Cache Memory Planning)

**답변:**

**데이터 크기 × 키 수 × 복제 계수**로 예측합니다.

**1) 메모리 사용량 계산**
```kotlin
fun estimateMemoryUsage(
    averageDataSize: Int,    // 평균 데이터 크기 (KB)
    numberOfKeys: Int,       // 예상 키 수
    replicationFactor: Int = 1 // 복제 계수
): Long {
    val baseMemory = averageDataSize * numberOfKeys
    val overhead = baseMemory * 0.3 // Redis 오버헤드 30%
    val replication = baseMemory * (replicationFactor - 1)
    
    return (baseMemory + overhead + replication).toLong()
}

// 예시: 상품 정보 10만 개, 평균 5KB
val estimated = estimateMemoryUsage(5, 100_000, 1)
// 결과: 약 650MB
```

**2) 용량 계획**
```kotlin
class CacheCapacityPlanner {
    fun planCapacity(
        currentKeys: Int,
        growthRate: Double, // 월간 성장률
        months: Int
    ): Long {
        val futureKeys = (currentKeys * Math.pow(1 + growthRate, months.toDouble())).toInt()
        val averageSize = 5 // KB
        return estimateMemoryUsage(averageSize, futureKeys)
    }
}
```

**3) 임계치 기반 대응**
```kotlin
@Scheduled(cron = "0 */5 * * * *") // 5분마다
fun checkMemoryUsage() {
    val usage = getRedisMemoryUsage()
    val maxMemory = getRedisMaxMemory()
    val usageRate = usage.toDouble() / maxMemory
    
    when {
        usageRate > 0.9 -> {
            // 긴급: 캐시 정리 시작
            evictLowPriorityCache()
            alert("Critical: Memory usage ${usageRate * 100}%")
        }
        usageRate > 0.8 -> {
            // 경고: TTL 단축, 낮은 우선순위 데이터 제거
            shortenTTLForLowPriority()
            alert("Warning: Memory usage ${usageRate * 100}%")
        }
        usageRate > 0.7 -> {
            // 모니터링 강화
            increaseMonitoringFrequency()
        }
    }
}
```

**실무 팁**: 
- **80% 사용률을 임계치**로 설정
- 성장률을 고려한 여유 공간 확보 (최소 20%)

---

## Q39. 분산 락과 DB 락의 선택 기준 (Distributed Lock vs DB Lock)

**답변:**

**트래픽과 일관성 요구사항**을 기준으로 선택합니다.

**1) 선택 기준표**
```
분산 락 (Redis) 선택:
- 트래픽: 초당 100 요청 이상
- 다중 서버 환경
- DB 부하 분산 필요
- 빠른 응답 시간 중요

DB 락 선택:
- 트래픽: 초당 100 요청 미만
- 단일 서버 또는 낮은 트래픽
- 강한 일관성 필수
- DB 트랜잭션과 통합 필요
```

**2) 혼용 사용 시나리오**
```kotlin
// 분산 락으로 경합 감소, DB 락으로 최종 검증
fun decreaseStock(productId: Long, quantity: Int) {
    // 1단계: 분산 락으로 빠른 검증
    val distributedLock = redissonClient.getLock("lock:stock:$productId")
    try {
        distributedLock.lock(3, 10, TimeUnit.SECONDS)
        
        // 2단계: DB 락으로 정확한 검증
        val product = productRepository.findByIdWithLock(productId)
        if (product.stock < quantity) {
            throw InsufficientStockException()
        }
        
        product.decreaseStock(quantity)
        productRepository.save(product)
    } finally {
        distributedLock.unlock()
    }
}
```

**3) 성능 비교**
- **분산 락**: 1-5ms (Redis 네트워크 지연)
- **DB 락**: 10-50ms (DB I/O)
- **낙관적 락**: 0ms (락 없음, 충돌 시 재시도)

**실무 팁**: 
- **기본은 낙관적 락**, 충돌이 잦으면 분산 락
- DB 락은 마지막 수단

---

## Q40. 캐시 무효화 타이밍 및 전략 (Cache Invalidation Timing)

**답변:**

**데이터 중요도에 따라 즉시/지연 무효화**를 선택합니다.

**1) 즉시 무효화 (중요 데이터)**
```kotlin
@Transactional
fun updateProductPrice(productId: Long, newPrice: Money) {
    // 1. DB 업데이트
    val product = productRepository.findById(productId)
        .orElseThrow { ProductNotFoundException(productId) }
    product.updatePrice(newPrice)
    productRepository.save(product)
    
    // 2. 트랜잭션 커밋 후 즉시 무효화
    TransactionSynchronizationManager.registerSynchronization(
        object : TransactionSynchronizationAdapter() {
            override fun afterCommit() {
                cacheManager.getCache("products")?.evict(productId)
            }
        }
    )
}
```

**2) 지연 무효화 (덜 중요한 데이터)**
```kotlin
// 배치로 주기적 무효화
@Scheduled(cron = "0 */10 * * * *") // 10분마다
fun invalidateStaleCache() {
    val staleKeys = findStaleCacheKeys()
    staleKeys.forEach { key ->
        cacheManager.getCache("products")?.evict(key)
    }
}
```

**3) 트랜잭션 커밋 전/후**
- **커밋 전 무효화**: 롤백 시 불일치 가능 (비권장)
- **커밋 후 무효화**: 정합성 보장 (권장)

**4) 재시도 전략**
```kotlin
@Retryable(maxAttempts = 3, backoff = Backoff(delay = 100))
fun invalidateCache(key: String) {
    try {
        cacheManager.getCache("products")?.evict(key)
    } catch (e: Exception) {
        // 실패 시 Dead Letter Queue에 저장
        deadLetterQueue.add(InvalidationEvent(key))
        throw e
    }
}
```

**실무 팁**: 
- **중요 데이터는 즉시 무효화**
- 덜 중요한 데이터는 TTL 만료로 자연 무효화

---

## Q41. 캐시와 트랜잭션의 일관성 보장 (Cache & Transaction Consistency)

**답변:**

**트랜잭션 커밋 후 캐시 업데이트**로 일관성을 보장합니다.

**1) 트랜잭션 동기화**
```kotlin
@Transactional
fun updateProduct(product: Product) {
    productRepository.save(product)
    
    // 커밋 후 캐시 업데이트
    TransactionSynchronizationManager.registerSynchronization(
        object : TransactionSynchronizationAdapter() {
            override fun afterCommit() {
                cacheManager.getCache("products")?.put(product.id, product)
            }
            
            override fun afterRollback() {
                // 롤백 시 캐시 무효화 (이전 값 제거)
                cacheManager.getCache("products")?.evict(product.id)
            }
        }
    )
}
```

**2) 2PC와 캐시 통합**
```kotlin
// Phase 1: Prepare
fun prepareCacheUpdate(key: String, value: Any) {
    redisTemplate.opsForValue().set("prepare:$key", value, Duration.ofSeconds(30))
}

// Phase 2: Commit
fun commitCacheUpdate(key: String) {
    val prepared = redisTemplate.opsForValue().get("prepare:$key")
    if (prepared != null) {
        redisTemplate.opsForValue().set(key, prepared)
        redisTemplate.delete("prepare:$key")
    }
}

// Phase 2: Rollback
fun rollbackCacheUpdate(key: String) {
    redisTemplate.delete("prepare:$key")
}
```

**3) 분산 트랜잭션 환경**
- **Saga 패턴**: 각 단계마다 캐시 업데이트
- **보상 트랜잭션**: 롤백 시 캐시도 롤백

**실무 팁**: 
- **기본은 커밋 후 업데이트**
- 2PC는 복잡도가 높아 신중히 도입

---

## Q42. 분산 락의 공정성(Fairness) 보장 (Distributed Lock Fairness)

**답변:**

**FIFO 큐 기반 락**으로 공정성을 보장합니다.

**1) Redis List 기반 FIFO 큐**
```kotlin
fun acquireFairLock(lockKey: String, timeout: Long): Boolean {
    val queueKey = "queue:$lockKey"
    val requestId = UUID.randomUUID().toString()
    
    // 큐에 자신 추가
    redisTemplate.opsForList().rightPush(queueKey, requestId)
    
    val startTime = System.currentTimeMillis()
    while (System.currentTimeMillis() - startTime < timeout) {
        // 큐의 첫 번째가 자신인지 확인
        val first = redisTemplate.opsForList().leftPeek(queueKey)
        if (first == requestId) {
            // 락 획득 시도
            val acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, requestId, Duration.ofSeconds(10))
            if (acquired == true) {
                return true
            }
        }
        Thread.sleep(50) // 짧은 대기
    }
    
    // 타임아웃 시 큐에서 제거
    redisTemplate.opsForList().remove(queueKey, 1, requestId)
    return false
}
```

**2) 기아 방지**
```kotlin
// 우선순위 기반 락 (VIP 사용자 우선)
fun acquirePriorityLock(lockKey: String, priority: Int): Boolean {
    val queueKey = "priority:$lockKey"
    val requestId = UUID.randomUUID().toString()
    
    // 우선순위와 함께 큐에 추가
    redisTemplate.opsForZSet().add(
        queueKey, 
        requestId, 
        priority.toDouble()
    )
    
    // 가장 높은 우선순위가 자신인지 확인
    val top = redisTemplate.opsForZSet().range(queueKey, 0, 0)?.first()
    return top == requestId
}
```

**3) Redisson FairLock**
```kotlin
val fairLock = redissonClient.getFairLock("fairLock:$key")
fairLock.lock(10, TimeUnit.SECONDS) // FIFO 순서 보장
```

**실무 팁**: 
- **대부분의 경우 공정성은 선택사항**
- 공정성이 필요하면 Redisson FairLock 사용

---

## Q43. 캐시 데이터 압축 및 최적화 (Cache Data Compression)

**답변:**

**1KB 이상 데이터에만 압축 적용**합니다.

**1) 압축 기준**
```kotlin
fun shouldCompress(dataSize: Int): Boolean {
    return dataSize > 1024 // 1KB 이상
}

fun compressData(data: ByteArray): ByteArray {
    return if (shouldCompress(data.size)) {
        GzipUtils.compress(data) // gzip 압축
    } else {
        data
    }
}
```

**2) 압축 알고리즘 선택**
```
gzip: 높은 압축률, 느린 속도 (대용량 데이터)
snappy: 낮은 압축률, 빠른 속도 (실시간 데이터)
lz4: 중간 압축률, 매우 빠른 속도 (고빈도 조회)
```

**3) Redis에서의 압축**
```kotlin
@Component
class CompressedRedisTemplate(
    private val redisTemplate: RedisTemplate<String, ByteArray>
) {
    fun setCompressed(key: String, value: Any, ttl: Duration) {
        val json = objectMapper.writeValueAsBytes(value)
        val compressed = if (json.size > 1024) {
            GzipUtils.compress(json)
        } else {
            json
        }
        redisTemplate.opsForValue().set(key, compressed, ttl)
    }
    
    fun getCompressed(key: String): Any? {
        val compressed = redisTemplate.opsForValue().get(key) ?: return null
        val decompressed = if (isCompressed(compressed)) {
            GzipUtils.decompress(compressed)
        } else {
            compressed
        }
        return objectMapper.readValue(decompressed, Any::class.java)
    }
}
```

**실무 팁**: 
- **1KB 이하는 압축하지 않음** (오버헤드가 더 큼)
- 대용량 리스트에만 압축 적용

---

## Q44. 분산 락의 재진입(Reentrancy) 지원 (Distributed Lock Reentrancy)

**답변:**

**재진입 카운터를 Redis에 저장**하여 관리합니다.

**1) 재진입 락 구현**
```kotlin
class ReentrantDistributedLock(
    private val redisTemplate: RedisTemplate<String, String>,
    private val lockKey: String
) {
    private val threadLocal = ThreadLocal<String>()
    
    fun lock(): Boolean {
        val threadId = Thread.currentThread().id.toString()
        threadLocal.set(threadId)
        
        val current = redisTemplate.opsForValue().get(lockKey)
        if (current == threadId) {
            // 이미 락을 보유한 경우 카운터 증가
            val count = redisTemplate.opsForValue().increment("$lockKey:count")
            return true
        }
        
        // 새로 락 획득
        val acquired = redisTemplate.opsForValue()
            .setIfAbsent(lockKey, threadId, Duration.ofSeconds(10))
        if (acquired == true) {
            redisTemplate.opsForValue().set("$lockKey:count", "1")
            return true
        }
        return false
    }
    
    fun unlock() {
        val threadId = threadLocal.get() ?: return
        val current = redisTemplate.opsForValue().get(lockKey)
        
        if (current == threadId) {
            val count = redisTemplate.opsForValue().decrement("$lockKey:count")
            if (count <= 0) {
                redisTemplate.delete(lockKey)
                redisTemplate.delete("$lockKey:count")
            }
        }
    }
}
```

**2) Redisson ReentrantLock**
```kotlin
val reentrantLock = redissonClient.getLock("reentrantLock:$key")
reentrantLock.lock() // 자동으로 재진입 지원
// 같은 스레드에서 다시 lock() 호출 가능
reentrantLock.lock()
reentrantLock.unlock()
reentrantLock.unlock()
```

**3) 재진입의 장단점**
- **장점**: 중첩된 함수 호출에서 편리
- **단점**: 복잡도 증가, 데드락 위험

**실무 팁**: 
- **대부분의 경우 재진입 불필요**
- 필요하면 Redisson 사용 권장

---

## Q45. 캐시 키 기반 권한 및 접근 제어 (Cache Key Access Control)

**답변:**

**사용자 ID를 키에 포함 + 권한 검증**을 적용합니다.

**1) 사용자별 키 구조**
```kotlin
object CacheKeys {
    fun userCart(userId: Long) = "joopang:user:$userId:cart"
    fun userOrders(userId: Long) = "joopang:user:$userId:orders"
    fun userRecommendations(userId: Long) = "joopang:user:$userId:recommendations"
}

// 권한 검증
fun getUserCart(userId: Long, requestedUserId: Long): Cart {
    if (userId != requestedUserId) {
        throw UnauthorizedException()
    }
    return cacheManager.getCache("carts")?.get(userId) as? Cart
        ?: cartRepository.findByUserId(userId)
}
```

**2) 권한 기반 무효화**
```kotlin
fun invalidateUserCache(userId: Long) {
    val patterns = listOf(
        "joopang:user:$userId:*",
        "joopang:cart:$userId:*",
        "joopang:orders:$userId:*"
    )
    patterns.forEach { pattern ->
        deleteByPattern(pattern)
    }
}
```

**3) 다중 테넌트 격리**
```kotlin
object CacheKeys {
    fun tenantKey(tenantId: Long, key: String) = "tenant:$tenantId:$key"
}

// 테넌트별 완전 격리
fun getProduct(tenantId: Long, productId: Long): Product {
    val key = CacheKeys.tenantKey(tenantId, "product:$productId")
    return cacheManager.getCache("products")?.get(key) as? Product
        ?: productRepository.findByTenantIdAndId(tenantId, productId)
}
```

**4) 정보 유출 방지**
- 키에 민감 정보 포함 금지
- 키 패턴으로 데이터 추론 불가능하게 설계

**실무 팁**: 
- **사용자별 데이터는 항상 사용자 ID 포함**
- 권한 검증은 캐시 조회 전에 수행

---

## Q46. 캐시 스노우볼 효과 및 연쇄 무효화 (Cache Snowball Effect)

**답변:**

**무효화 범위 제한 + 배치 처리 + 우선순위**로 완화합니다.

**1) 무효화 범위 제한**
```kotlin
// ❌ 나쁜 예: 모든 관련 캐시 무효화
fun invalidateAll(productId: Long) {
    cacheManager.getCache("products")?.clear() // 전체 삭제
}

// ✅ 좋은 예: 필요한 것만 무효화
fun invalidateSelective(productId: Long) {
    // 상품 상세만 무효화
    cacheManager.getCache("products")?.evict(productId)
    
    // 관련 목록은 TTL 단축으로 자연 만료 유도
    shortenTTL("product:list:category:*", 60) // 1분으로 단축
}
```

**2) 배치 무효화**
```kotlin
// 대량 무효화를 배치로 처리
fun batchInvalidate(keys: List<String>) {
    keys.chunked(100).forEach { chunk ->
        CompletableFuture.runAsync {
            chunk.forEach { key ->
                cacheManager.getCache("products")?.evict(key)
            }
        }
    }
}
```

**3) 무효화 우선순위**
```kotlin
enum class InvalidationPriority {
    CRITICAL,   // 즉시 무효화 (가격, 재고)
    HIGH,       // 빠른 무효화 (상품 정보)
    MEDIUM,     // 배치 무효화 (목록)
    LOW         // TTL 단축 (통계)
}

fun invalidateWithPriority(key: String, priority: InvalidationPriority) {
    when (priority) {
        InvalidationPriority.CRITICAL -> {
            cacheManager.getCache("products")?.evict(key)
        }
        InvalidationPriority.HIGH -> {
            CompletableFuture.runAsync {
                cacheManager.getCache("products")?.evict(key)
            }
        }
        InvalidationPriority.MEDIUM -> {
            invalidateQueue.add(key) // 큐에 추가
        }
        InvalidationPriority.LOW -> {
            shortenTTL(key, 60) // TTL만 단축
        }
    }
}
```

**4) 스노우볼 방지**
- **의존성 체인 최소화**: 직접 의존만 무효화
- **지연 무효화**: 덜 중요한 캐시는 나중에 무효화
- **TTL 활용**: 무효화 대신 TTL 단축

**실무 팁**: 
- **무효화는 최소한으로**, TTL 활용 우선
- 배치 무효화는 트래픽이 낮은 시간대에 수행

---

## Q47. 분산 락의 읽기/쓰기 락 분리 (Read-Write Lock in Distributed Environment)

**답변:**

**Redisson RReadWriteLock**을 사용하거나 자체 구현합니다.

**1) Redisson ReadWriteLock**
```kotlin
val readWriteLock = redissonClient.getReadWriteLock("rwLock:$key")

// 읽기 락 (여러 개 동시 가능)
fun readData(key: String): Data {
    val readLock = readWriteLock.readLock()
    try {
        readLock.lock()
        return cacheManager.getCache("data")?.get(key) as? Data
            ?: dataRepository.findById(key)
    } finally {
        readLock.unlock()
    }
}

// 쓰기 락 (배타적)
fun writeData(key: String, data: Data) {
    val writeLock = readWriteLock.writeLock()
    try {
        writeLock.lock()
        dataRepository.save(data)
        cacheManager.getCache("data")?.put(key, data)
    } finally {
        writeLock.unlock()
    }
}
```

**2) 자체 구현**
```kotlin
class DistributedReadWriteLock(
    private val redisTemplate: RedisTemplate<String, String>,
    private val lockKey: String
) {
    private val readCountKey = "$lockKey:readCount"
    private val writeLockKey = "$lockKey:write"
    
    fun acquireReadLock(): Boolean {
        // 쓰기 락이 없을 때만 읽기 락 획득
        while (redisTemplate.opsForValue().get(writeLockKey) != null) {
            Thread.sleep(10)
        }
        redisTemplate.opsForValue().increment(readCountKey)
        return true
    }
    
    fun releaseReadLock() {
        redisTemplate.opsForValue().decrement(readCountKey)
    }
    
    fun acquireWriteLock(): Boolean {
        // 읽기 락과 쓰기 락이 모두 없을 때만 획득
        while (redisTemplate.opsForValue().get(readCountKey)?.toInt() ?: 0 > 0 ||
               redisTemplate.opsForValue().get(writeLockKey) != null) {
            Thread.sleep(10)
        }
        return redisTemplate.opsForValue()
            .setIfAbsent(writeLockKey, "locked", Duration.ofSeconds(10)) == true
    }
}
```

**3) 우선순위 관리**
- **읽기 우선**: 읽기가 많을 때 유리
- **쓰기 우선**: 쓰기가 중요할 때 유리
- **공정**: FIFO 순서

**4) 기아 문제 해결**
- **타임아웃**: 일정 시간 대기 후 실패
- **우선순위**: 쓰기 락에 우선순위 부여

**실무 팁**: 
- **Redisson 사용 권장** (검증된 구현)
- 읽기가 많고 쓰기가 적을 때 효과적

---

## 마무리 (추가)

이 추가 답변들은 **캐시 적용 기준, 단일 지점 장애, 캐시키 구조화** 등 실무에서 자주 마주치는 핵심 주제들을 다룹니다.

**추가 핵심 원칙:**
1. **캐시는 선택적 적용**: 모든 데이터를 캐싱하지 말 것
2. **SPOF는 다층 방어**: HA 구성 + 서킷 브레이커 + Fallback
3. **키 구조는 일관성**: 체계적인 네이밍과 버전 관리
4. **성능과 복잡도의 균형**: 단순한 해결책 우선, 필요시 고급 기법

실무에서는 **이론보다 경험이 중요**합니다. 작은 것부터 시작하여 점진적으로 개선하세요!

