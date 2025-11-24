# 분산 환경에서의 동시성 제어 (Concurrency Control in Distributed Environments)

## 1. 개요

단일 서버 환경과 달리, 분산 환경에서는 여러 노드가 동시에 데이터를 조작할 수 있어 데이터의 일관성(Consistency)을 유지하는 것이 훨씬 복잡합니다. 네트워크 지연, 부분 실패(Partial Failure), 시계 동기화 문제 등 분산 시스템 고유의 특성으로 인해 기존의 단일 DB 트랜잭션 방식만으로는 동시성 문제를 완벽하게 해결하기 어렵습니다.

## 2. 주요 과제

* **Race Condition (경쟁 상태)**: 여러 프로세스가 동시에 같은 데이터에 접근하여 조작할 때, 실행 순서에 따라 결과가 달라지는 현상.
* **Data Consistency (데이터 일관성)**: 모든 노드가 동일한 시점에 동일한 데이터를 바라보아야 함.
* **Deadlock (교착 상태)**: 서로 다른 노드가 서로의 자원을 기다리며 무한 대기하는 상태.

## 3. 해결 전략 및 패턴

### 3.1 분산 락 (Distributed Lock)

여러 서버에서 공유 자원에 접근할 때, 중앙화된 락 관리 시스템을 통해 순차적 접근을 보장하는 방식입니다.

* **Redis (SETNX/Redisson)**: 
  * **SETNX 방식**: `SET key value NX PX TTL` 명령어로 원자적 락 획득. 빠르고 간편하지만 단일 Redis 인스턴스 장애 시 락 정보 유실 가능.
  * **Redisson**: Pub/Sub 기반 락으로 Redis 부하 최소화. Watchdog으로 자동 갱신 지원.
  * **Redlock**: 여러 독립적인 Redis 마스터 노드를 사용하여 고가용성 확보. (과반수 이상 노드에서 락 획득 필요)
* **Zookeeper**: Znode의 순차적 생성 기능을 이용. 신뢰성이 높지만 구축 및 관리가 복잡함. 세션 타임아웃으로 자동 락 해제.
* **MySQL (Named Lock)**: `GET_LOCK('lock_name', timeout)` 함수를 이용해 DB 레벨에서 락을 관리. DB 커넥션에 종속적이며, 커넥션 종료 시 자동 해제.
  * **예시**: `SELECT GET_LOCK('stock_lock', 10);` - 10초 대기 후 락 획득 시도

### 3.2 낙관적 락 (Optimistic Locking)

충돌이 드물 것이라고 가정하고, 데이터를 업데이트할 때 버전(Version) 정보를 확인하는 방식입니다.

* **JPA `@Version`**: 엔티티에 버전 필드를 두고 업데이트 시 버전을 체크. 충돌 시 `OptimisticLockException` 발생 후 재시도 로직 필요.
* **CAS (Compare-And-Swap)**: 현재 값이 예상하는 값과 같을 때만 업데이트 수행.

### 3.3 비관적 락 (Pessimistic Locking)

충돌이 빈번할 것이라고 가정하고, 데이터를 읽을 때부터 락을 걸어 다른 트랜잭션의 접근을 차단합니다.

* **DB `SELECT ... FOR UPDATE`**: 데이터를 읽는 시점에 배타 락(Exclusive Lock)을 획득. 성능 저하 및 데드락 위험이 있음.

## 4. 분산 트랜잭션 관리

### 4.1 2PC (Two-Phase Commit)

모든 참여 노드가 트랜잭션을 커밋할 준비가 되었는지 확인(Prepare)한 후, 모두 준비되면 커밋(Commit)하는 방식.

* **장점**: 강력한 일관성 보장.
* **단점**: 코디네이터 장애 시 전체 블로킹(Blocking) 발생, 성능 저하.

### 4.2 Saga Pattern

긴 트랜잭션을 여러 개의 로컬 트랜잭션으로 쪼개고, 순차적으로 실행하는 방식. 실패 시 보상 트랜잭션(Compensating Transaction)을 실행하여 롤백 처리.

* **Choreography**: 각 서비스가 이벤트를 발행하고 구독하며 트랜잭션 진행.
* **Orchestration**: 중앙 오케스트레이터가 트랜잭션 흐름을 제어.
* **특징**: 결과적 일관성(Eventual Consistency)을 지향.

## 5. 일관성 모델 (Consistency Models)

* **Strong Consistency (강한 일관성)**: 모든 읽기 요청이 가장 최근의 쓰기 결과를 반환함을 보장. (성능 희생)
* **Eventual Consistency (결과적 일관성)**: 일시적으로 데이터가 불일치할 수 있으나, 시간이 지나면 결국 일관성이 맞춰짐. (가용성 및 성능 중시)

## 6. 실무 적용 예시

### 6.1 재고 차감 (분산 락 사용)

```kotlin
@Service
class InventoryService(
    private val inventoryRepository: InventoryRepository,
    private val redissonClient: RedissonClient
) {
    fun decreaseStock(productId: Long, quantity: Int) {
        val lock = redissonClient.getLock("lock:stock:$productId")
        try {
            if (!lock.tryLock(3, 10, TimeUnit.SECONDS)) {
                throw LockAcquisitionException("Failed to acquire lock")
            }
            decreaseStockInternal(productId, quantity)
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock()
            }
        }
    }
    
    @Transactional
    private fun decreaseStockInternal(productId: Long, quantity: Int) {
        val inventory = inventoryRepository.findByProductId(productId)
            .orElseThrow { InventoryNotFoundException(productId) }
        inventory.decrease(quantity)
        inventoryRepository.save(inventory)
    }
}
```

### 6.2 상품 정보 수정 (낙관적 락 사용)

```kotlin
@Entity
class Product(
    @Id val id: Long,
    var name: String,
    @Version var version: Long = 0  // 낙관적 락용 버전 필드
)

@Service
class ProductService(
    private val productRepository: ProductRepository
) {
    fun updateProduct(id: Long, updateRequest: ProductUpdateRequest) {
        var retries = 3
        while (retries > 0) {
            try {
                val product = productRepository.findById(id)
                    .orElseThrow { ProductNotFoundException(id) }
                product.update(updateRequest)
                productRepository.save(product)
                return
            } catch (e: OptimisticLockingFailureException) {
                retries--
                if (retries == 0) throw e
                Thread.sleep(100) // 짧은 대기 후 재시도
            }
        }
    }
}
```

## 7. 결론

분산 환경에서의 동시성 제어는 시스템의 요구사항(일관성 vs 가용성 vs 성능)에 따라 적절한 전략을 선택해야 합니다.

* **재고 차감**과 같이 데이터 정확성이 중요한 경우: **분산 락 (Redis/Zookeeper)** 또는 **비관적 락**.
* **충돌이 적은 일반적인 업데이트**: **낙관적 락**.
* **마이크로서비스 간 데이터 동기화**: **Saga Pattern (Eventual Consistency)**.
* **선택 기준**:
  * **트래픽이 높고 충돌이 잦음**: 분산 락 (DB 부하 분산)
  * **트래픽이 낮고 충돌이 드묾**: 낙관적 락 (성능 우선)
  * **강한 일관성 필수**: 비관적 락 또는 분산 락
  * **약간의 불일치 허용 가능**: 결과적 일관성 (Saga)
