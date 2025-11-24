# 분산 락 (Distributed Lock) 심층 분석

## 1. 분산 락 개요

분산 락은 여러 서버(인스턴스)가 공통된 자원(데이터베이스, 파일 등)에 접근할 때, 동시성 문제를 해결하기 위해 사용하는 락킹 메커니즘입니다. 단일 서버 내에서는 `synchronized`나 `ReentrantLock`으로 해결 가능하지만, 다중 서버 환경에서는 별도의 외부 저장소(Redis, Zookeeper 등)를 이용해 락을 관리해야 합니다.

## 2. 분산 락과 DB 트랜잭션 (Transaction)

분산 락을 사용할 때 가장 많이 실수하는 부분이 **DB 트랜잭션과의 범위 설정**입니다.

### 2.1 올바른 실행 순서
동시성 이슈를 완벽하게 막기 위해서는 반드시 **락의 범위가 트랜잭션의 범위보다 커야 합니다.**

1.  **락 획득 (Lock Acquire)**
2.  **트랜잭션 시작 (Transaction Start)**
3.  **비즈니스 로직 수행 (Business Logic)**
4.  **트랜잭션 커밋/종료 (Transaction Commit)**
5.  **락 해제 (Lock Release)**

### 2.2 주의할 점 (Why?)
만약 `@Transactional` 내부에서 락을 잡고 해제한다면 다음과 같은 문제가 발생합니다.

*   **문제 상황**:
    1.  Thread A: 락 획득 -> 데이터 수정 -> **락 해제** -> (아직 커밋 전)
    2.  Thread B: **락 획득** -> 데이터 조회 (A가 커밋 전이라 변경 전 데이터 조회 가능성 있음, Isolation Level에 따라 다름) -> 수정 시도
    3.  Thread A: **커밋**
    4.  Thread B: **커밋** (A의 수정 사항을 덮어쓰거나 데이터 불일치 발생)
*   **해결**: AOP 등을 활용하여 트랜잭션이 완전히 끝난 후에 락을 해제하도록 설계해야 합니다.

### 2.3 올바른 구현 예시

```kotlin
@Service
class ProductService(
    private val productRepository: ProductRepository,
    private val redissonClient: RedissonClient
) {
    // ❌ 잘못된 예시: 트랜잭션 내부에서 락 해제
    @Transactional
    fun decreaseStockWrong(productId: Long, quantity: Int) {
        val lock = redissonClient.getLock("lock:stock:$productId")
        try {
            lock.lock(10, TimeUnit.SECONDS)
            val product = productRepository.findById(productId)
                .orElseThrow { ProductNotFoundException(productId) }
            product.decreaseStock(quantity)
            productRepository.save(product)
            // 락이 여기서 해제되면, 트랜잭션이 아직 커밋되지 않았을 수 있음!
        } finally {
            lock.unlock()
        }
    }
    
    // ✅ 올바른 예시: 락 범위가 트랜잭션보다 큼
    fun decreaseStockCorrect(productId: Long, quantity: Int) {
        val lock = redissonClient.getLock("lock:stock:$productId")
        try {
            lock.lock(10, TimeUnit.SECONDS)
            // 트랜잭션은 락 내부에서 시작
            decreaseStockInternal(productId, quantity)
        } finally {
            // 트랜잭션이 완전히 끝난 후 락 해제
            lock.unlock()
        }
    }
    
    @Transactional
    private fun decreaseStockInternal(productId: Long, quantity: Int) {
        val product = productRepository.findById(productId)
            .orElseThrow { ProductNotFoundException(productId) }
        product.decreaseStock(quantity)
        productRepository.save(product)
        // 여기서 트랜잭션 커밋
    }
}
```

### 2.4 Spring AOP를 활용한 자동화

트랜잭션 완료 후 락을 해제하는 로직을 AOP로 자동화할 수 있습니다:

```kotlin
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class DistributedLock(
    val key: String,
    val waitTime: Long = 3,
    val leaseTime: Long = 10
)

@Aspect
@Component
class DistributedLockAspect(
    private val redissonClient: RedissonClient
) {
    @Around("@annotation(distributedLock)")
    fun around(joinPoint: ProceedingJoinPoint, distributedLock: DistributedLock): Any? {
        val lockKey = distributedLock.key
        val lock = redissonClient.getLock(lockKey)
        try {
            val acquired = lock.tryLock(
                distributedLock.waitTime,
                distributedLock.leaseTime,
                TimeUnit.SECONDS
            )
            if (!acquired) {
                throw LockAcquisitionException("Failed to acquire lock: $lockKey")
            }
            return joinPoint.proceed()
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock()
            }
        }
    }
}

// 사용 예시
@Service
class ProductService(
    private val productRepository: ProductRepository
) {
    @DistributedLock(key = "lock:stock:#{#productId}")
    @Transactional
    fun decreaseStock(productId: Long, quantity: Int) {
        val product = productRepository.findById(productId)
            .orElseThrow { ProductNotFoundException(productId) }
        product.decreaseStock(quantity)
        productRepository.save(product)
    }
}
```

## 3. Redis 분산 락 구현 방식 (3가지)

### 3.1 Simple Lock
*   **방식**: `SETNX` 명령어를 사용하여 락 획득을 1회 시도합니다.
*   **특징**: 실패하면 쿨하게 포기하거나 예외를 던집니다.
*   **용도**: 중복 실행 방지 (예: 배치 작업이 동시에 두 번 도는 것 방지).
*   **코드 예시**:
    ```kotlin
    fun executeOnce(taskId: String, task: () -> Unit) {
        val lockKey = "lock:task:$taskId"
        val acquired = redisTemplate.opsForValue()
            .setIfAbsent(lockKey, "locked", Duration.ofMinutes(5))
        if (acquired == true) {
            try {
                task()
            } finally {
                redisTemplate.delete(lockKey)
            }
        } else {
            throw TaskAlreadyRunningException(taskId)
        }
    }
    ```

### 3.2 Spin Lock
*   **방식**: 락 획득에 실패하면 일정 시간 대기(Sleep) 후 다시 시도하는 것을 반복(Polling)합니다.
*   **특징**: `Lettuce` 클라이언트가 주로 이 방식을 사용합니다.
*   **단점**: 락을 기다리는 동안 지속적으로 Redis에 요청을 보내므로 **Redis에 부하**를 줄 수 있습니다.
*   **코드 예시**:
    ```kotlin
    fun acquireLockWithRetry(lockKey: String, timeout: Duration): Boolean {
        val endTime = System.currentTimeMillis() + timeout.toMillis()
        while (System.currentTimeMillis() < endTime) {
            val acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "locked", Duration.ofSeconds(10))
            if (acquired == true) {
                return true
            }
            Thread.sleep(100) // 100ms 대기 후 재시도
        }
        return false
    }
    ```

### 3.3 Pub/Sub (Redisson)
*   **방식**: Redis의 Pub/Sub 기능을 활용합니다. 락을 점유 중인 클라이언트가 락을 해제하면, 대기 중인 클라이언트에게 "락이 해제되었다"는 메시지를 보냅니다.
*   **특징**: `Redisson` 라이브러리가 이 방식을 지원합니다.
*   **장점**: 대기 중인 클라이언트가 지속적으로 재시도 요청을 보내지 않아도 되므로 **Redis 부하가 적습니다.**
*   **코드 예시**:
    ```kotlin
    @Service
    class ProductService(
        private val redissonClient: RedissonClient
    ) {
        fun decreaseStock(productId: Long, quantity: Int) {
            val lock = redissonClient.getLock("lock:stock:$productId")
            try {
                // 최대 3초 대기, 10초 후 자동 해제
                if (lock.tryLock(3, 10, TimeUnit.SECONDS)) {
                    // 비즈니스 로직 수행
                    // ...
                } else {
                    throw LockAcquisitionException("Failed to acquire lock")
                }
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock()
                }
            }
        }
    }
    ```

## 4. 분산 락 적용 범위

*   **적절한 범위**:
    *   **데이터 정합성이 필수적인 경우**: 재고 차감, 선착순 쿠폰 발급, 포인트 사용/충전.
    *   **DB 락으로 해결하기 어려운 경우**: 여러 마이크로서비스에 걸친 트랜잭션 제어, DB 외의 외부 리소스 동시 접근 제어.
*   **최소화**: 락을 잡고 있는 시간은 시스템 전체의 병목이 될 수 있으므로, **임계 영역(Critical Section)을 최소화**해야 합니다. (예: 외부 API 호출 등 시간이 오래 걸리는 작업은 락 범위 밖으로 빼는 것이 좋음)

## 5. 장단점 및 한계

### 5.1 장점
*   **DB 부하 감소**: 요청이 DB에 도달하기 전에 Redis 단에서 제어하므로, 불필요한 DB Connection 점유나 I/O를 원천 차단할 수 있습니다.
*   **강력한 원자성**: Redis의 싱글 스레드 특성을 이용해 여러 인스턴스 간의 완벽한 동기화를 보장합니다.

### 5.2 단점 및 한계
*   **관리 포인트 증가**: DB 외에 Redis라는 인프라를 추가로 관리해야 합니다.
*   **SPOF (Single Point of Failure)**: 락을 관리하는 Redis 서버가 다운되면, 서비스 전체의 로직이 마비될 수 있습니다. (Redis Cluster, Sentinel 등으로 고가용성 확보 필요)
*   **구현 복잡도**: 트랜잭션 범위, 락 타임아웃(TTL), 예외 처리 등을 꼼꼼하게 설계해야 합니다.

### 5.3 Redlock 알고리즘 (분산 환경에서의 고가용성 락)

단일 Redis 인스턴스를 사용하면 SPOF(Single Point of Failure) 문제가 발생할 수 있습니다. Redlock은 여러 독립적인 Redis 마스터 노드를 사용하여 락의 신뢰성을 높이는 알고리즘입니다.

*   **동작 원리**:
    1. 클라이언트가 현재 시간을 기록합니다.
    2. N개의 독립적인 Redis 마스터 노드에 순차적으로 락 획득을 시도합니다. (각 노드에 `SET key value NX PX TTL` 명령 실행)
    3. **과반수 이상(N/2 + 1)**의 노드에서 락을 획득하고, 전체 소요 시간이 락 TTL보다 짧으면 락 획득 성공.
    4. 락 해제 시 모든 노드에 해제 명령을 전송합니다.
*   **장점**: 단일 Redis 장애 시에도 락이 유지될 수 있습니다.
*   **단점**: 구현이 복잡하고, 네트워크 지연으로 인한 정확도 문제가 있을 수 있습니다.
*   **실무 고려사항**: Redisson 라이브러리가 Redlock을 지원하지만, 대부분의 경우 Redis Sentinel이나 Cluster로 고가용성을 확보하는 것이 더 실용적입니다.

### 5.4 결론: 분산 락으로 모든 동시성 문제가 해결되는가?
분산 락은 **애플리케이션 레벨**에서 동시성을 제어하는 강력한 도구입니다. 특히 **DB Connection 고갈 방지**와 **부하 분산** 측면에서 매우 효과적입니다. 하지만 인프라 의존성이 높아지므로, 단순한 로직이라면 DB의 낙관적 락(Optimistic Lock) 등을 먼저 고려하고, 트래픽이 많거나 충돌이 잦은 경우에 분산 락을 도입하는 것이 좋습니다.
