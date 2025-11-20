# Step 9-10 동시성 개념 정리 및 현재 코드 점검

## 동시성 문제 기본 개념

### 동시성 문제란?
동시성 문제는 여러 트랜잭션이나 스레드가 동시에 같은 데이터에 접근할 때 발생하는 문제입니다. 예상치 못한 결과나 데이터 불일치가 발생할 수 있습니다.

### 주요 동시성 문제 유형

#### 1. Race Condition (경쟁 상태)
- **정의**: 두 개 이상의 트랜잭션이 같은 자원에 동시에 접근하여, 실행 순서에 따라 결과가 달라지는 상황
- **예시**: 재고가 100개인 상품에 두 주문이 동시에 50개씩 차감 요청 → 실제로는 50개만 차감될 수 있음
- **해결**: 락(Lock)을 사용하여 순차적 접근 보장

#### 2. Lost Update (갱신 손실)
- **정의**: 두 트랜잭션이 동시에 같은 데이터를 읽고 수정하여, 하나의 수정 내용이 덮어써지는 문제
- **예시**: 
  - T1: 재고 100개 읽음 → 50개 차감 → 50개 저장
  - T2: 재고 100개 읽음 → 30개 차감 → 70개 저장
  - 결과: T1의 차감이 무시되고 70개만 남음 (실제로는 20개여야 함)
- **해결**: 원자적(Atomic) 업데이트 또는 비관적 락

#### 3. Dirty Read (더티 리드)
- **정의**: 트랜잭션 A가 커밋하지 않은 데이터를 트랜잭션 B가 읽는 문제
- **예시**: T1이 재고를 50으로 변경했지만 롤백 → T2가 50을 읽고 처리 → 실제로는 100이어야 함
- **해결**: Read Committed 이상의 격리 수준 사용

#### 4. Non-repeatable Read (반복 불가능한 읽기)
- **정의**: 같은 트랜잭션 내에서 같은 데이터를 여러 번 읽을 때 값이 달라지는 문제
- **예시**: T1이 재고 100 읽음 → T2가 재고를 50으로 변경 및 커밋 → T1이 다시 읽으면 50
- **해결**: Repeatable Read 이상의 격리 수준 또는 `SELECT FOR UPDATE`

#### 5. Phantom Read (팬텀 리드)
- **정의**: 같은 조건으로 조회했을 때 이전에 없던 새로운 행이 나타나는 문제
- **예시**: T1이 "재고 < 10" 조회 → 5건 → T2가 재고 5인 상품 추가 → T1이 다시 조회 → 6건
- **해결**: Serializable 격리 수준 또는 Gap Lock

#### 6. Write Skew (쓰기 스큐)
- **정의**: 두 트랜잭션이 서로 다른 행을 읽고 조건을 확인한 후, 각자 다른 행을 업데이트하여 전체 제약을 위반하는 문제
- **예시**: 
  - 규칙: 최소 1명의 담당자 필요
  - T1: 담당자 2명 확인 → A를 제거
  - T2: 담당자 2명 확인 → B를 제거
  - 결과: 담당자 0명 (제약 위반)
- **해결**: Serializable 격리 수준 또는 명시적 락

#### 7. Deadlock (교착 상태)
- **정의**: 두 개 이상의 트랜잭션이 서로가 보유한 락을 기다리며 무한 대기하는 상태
- **예시**: 
  - T1: A 락 획득 → B 락 대기
  - T2: B 락 획득 → A 락 대기
- **해결**: 락 순서 일관성 유지, 타임아웃 설정, 데드락 감지 및 재시도

## 데이터베이스 락(Lock) 종류

### 낙관적 락 (Optimistic Lock)
- **개념**: 충돌이 드물다고 가정하고, 데이터 수정 시점에만 충돌 검사
- **방법**: 버전 필드나 타임스탬프를 사용하여 변경 감지
- **장점**: 락 대기 시간 없음, 성능 우수
- **단점**: 충돌 시 재시도 필요, 충돌이 많으면 비효율적
```kotlin
@Version
var version: Long = 0
```

### 비관적 락 (Pessimistic Lock)
- **개념**: 충돌이 자주 발생한다고 가정하고, 데이터 읽기 시점부터 락 획득
- **방법**: 데이터베이스의 락 메커니즘 활용 (`SELECT FOR UPDATE`, `SELECT FOR SHARE`)
- **장점**: 데이터 일관성 강력히 보장
- **단점**: 락 대기 시간 증가, 동시성 성능 저하 가능

#### FOR UPDATE (배타적 락, Exclusive Lock)
```sql
SELECT * FROM product WHERE id = 1 FOR UPDATE;
```
- **용도**: 읽고 수정할 데이터에 사용
- **특징**: 
  - 다른 트랜잭션은 해당 행을 읽거나 수정할 수 없음 (락 대기)
  - 읽기도 `FOR SHARE`나 `FOR UPDATE` 없이는 대기
  - Write Lock, X-Lock이라고도 함
- **사용 시나리오**: 재고 차감, 잔액 변경 등 수정이 필요한 경우

#### FOR SHARE (공유 락, Shared Lock)
```sql
SELECT * FROM product WHERE id = 1 FOR SHARE;
```
- **용도**: 읽기만 하되, 다른 트랜잭션의 수정을 막고 싶을 때
- **특징**:
  - 여러 트랜잭션이 동시에 `FOR SHARE`로 읽기 가능
  - `FOR UPDATE`나 일반 UPDATE는 대기해야 함
  - Read Lock, S-Lock이라고도 함
- **사용 시나리오**: 일관된 스냅샷이 필요하지만 수정은 하지 않는 경우

#### SKIP LOCKED
```sql
SELECT * FROM task WHERE status = 'PENDING' FOR UPDATE SKIP LOCKED LIMIT 1;
```
- **용도**: 락이 걸린 행을 건너뛰고 다음 행 처리
- **특징**: 대기 없이 사용 가능한 행만 가져옴
- **사용 시나리오**: 작업 큐, 티켓팅 시스템 등

#### NOWAIT
```sql
SELECT * FROM product WHERE id = 1 FOR UPDATE NOWAIT;
```
- **용도**: 락을 즉시 획득하지 못하면 바로 실패
- **특징**: 대기하지 않고 예외 발생
- **사용 시나리오**: 빠른 실패가 필요한 경우

### JPA Lock Modes
```kotlin
// 비관적 읽기 락 (FOR SHARE)
@Lock(LockModeType.PESSIMISTIC_READ)
fun findById(id: Long): Product?

// 비관적 쓰기 락 (FOR UPDATE)
@Lock(LockModeType.PESSIMISTIC_WRITE)
fun findById(id: Long): Product?

// 낙관적 락
@Lock(LockModeType.OPTIMISTIC)
fun findById(id: Long): Product?
```

### 분산 락 (Distributed Lock)
- **개념**: 여러 서버/프로세스 간의 동시성 제어
- **구현**: Redis (Redlock), ZooKeeper, Consul 등
- **필요성**: 애플리케이션이 다중 인스턴스로 배포된 경우
- **예시**:
```kotlin
// Redis를 활용한 분산 락
@RedisLock(key = "#productId", waitTime = 5000, leaseTime = 3000)
fun consumeStock(productId: Long, quantity: Int)
```

## 트랜잭션 격리 수준 (Isolation Level)

| 격리 수준 | Dirty Read | Non-repeatable Read | Phantom Read |
|-----------|------------|---------------------|--------------|
| READ UNCOMMITTED | 발생 | 발생 | 발생 |
| READ COMMITTED | 방지 | 발생 | 발생 |
| REPEATABLE READ | 방지 | 방지 | 발생 가능* |
| SERIALIZABLE | 방지 | 방지 | 방지 |

*MySQL InnoDB는 REPEATABLE READ에서도 Phantom Read를 Gap Lock으로 대부분 방지

---

| 구분 | 개념 요약 | 현재 구현 점검 | 비고 |
|------|-----------|----------------|------|
| **1. Race Condition** | 두 트랜잭션이 같은 자원을 동시 접근 → 실행 순서에 따라 결과가 달라짐. | `ProductLockManagerImpl`/`CouponLockManagerImpl`(src/main/kotlin/io/joopang/services/product/infrastructure/ProductLockManagerImpl.kt, src/main/kotlin/io/joopang/services/coupon/infrastructure/CouponLockManagerImpl.kt)로 JVM 내부 락을 걸어 순서를 보장하고 `consumeStock` native update로 최종값을 강제. **문제점:** 락이 JVM 프로세스 단위이므로 애플리케이션이 다중 인스턴스로 배포되면 레이스 컨디션을 완전히 막지 못한다. → Redis/DB 기반 분산락 또는 DB 단독 원자 연산만으로 제어 필요. | ⚠ 단일 인스턴스 전제 필요 |
| **2. Lost Update** | 서로 다른 트랜잭션이 read→write를 반복하며 마지막 write가 덮어써져 이전 업데이트가 사라짐. | `ProductRepository.consumeStock`(native `update … where stock >= :quantity`)가 재고 차감을 원자적으로 실행하고 실패 시 예외 처리 → 동일 아이템에 대해 마지막 write가 덮어쓰는 문제는 없음. 단, 다른 도메인(예: 뷰카운트)에는 동일한 보호가 없으므로 동일 패턴 적용 여부 확인 필요. | ✅ 재고 영역 해결, 다른 속성은 미적용 |
| **3. Dirty Read** | 트랜잭션 A가 커밋하지 않은 값을 B가 읽음. | Spring `@Transactional` + MySQL 기본 Repeatable Read로 Dirty Read는 발생하지 않는다. 모든 서비스 쓰기 메서드에 트랜잭션이 걸려 있으므로 read-only 트랜잭션에서 커밋 전 상태를 반환하지 않는다. | ✅ |
| **4. Non-repeatable Read** | 동일 트랜잭션 내 같은 쿼리를 두 번 실행했는데 중간에 값이 바뀌어 다른 결과를 얻음. | Repeatable Read가 기본이지만, 명시적으로 `SELECT ... FOR UPDATE`를 사용하지 않는 조회(`ProductRepository.findById`)는 다른 트랜잭션의 커밋 후 값이 바뀔 수 있다. 주문 흐름에서는 write 전에 비관적 락을 사용하므로 문제없지만, 일반 조회 API는 "최신 값"을 반환해도 무방하다는 전제가 필요하다. | ⚠ 비critical, 필요 시 `@Lock`/`FOR UPDATE` 고려 |
| **5. Phantom Read** | 동일 조건 조회가 두 번째에는 추가 로우를 반환. | MySQL Repeatable Read에서 phantom read는 gap lock으로 막히지만, InnoDB 옵션에 따라 insert gap lock 회피가 가능하다. 현재 코드는 phantom read를 적극적으로 제어하지 않는다. 예를 들어 `CouponService.getUserCoupons`는 호출 사이에 새로운 쿠폰이 생길 수 있다. 기능 요구사항상 허용 가능하지만, 통계/집계 쿼리라면 Snapshot isolation 또는 `SERIALIZABLE`이 필요하다. | ⚠ 요구사항별 확인 필요 |
| **6. Write Skew** | 두 트랜잭션이 조건을 검사하고 모두 true라 판단, 동시에 write하여 제약을 깨뜨리는 경우. | 대표적으로 "동시에 주문 가능한 재고"를 count로 검사하는 로직이 없다. 재고는 `consumeStock`에서 직접 차감하므로 Write Skew는 발생하지 않는다. 다만, 다른 영역(예: on-call 담당자, 특정 리소스 예약)은 여전히 취약할 수 있으므로 동일 패턴을 적용해야 한다. | ✅ (재고)에 한함 |
| **7. Deadlock** | 서로가 가진 락을 기다리다가 영원히 대기하는 상태. | 주문 흐름에서 `ProductLockManager`→`ProductRepository.consumeStock`→`OrderRepository.save` 순서로만 락을 획득하므로 데드락 가능성은 낮다. 하지만 `productLockManager`는 JVM 락이고 `consumeStock`은 DB 락을 획득하므로, 순서가 바뀌면 deadlock이 발생할 수 있다. 현재 코드는 MySQL deadlock 예외를 잡아 재시도하지 않으므로, 문제 발생 시 그대로 실패한다. Deadlock 감지 및 재시도 로직(backoff) 도입 권장. | ⚠ 재시도 로직 없음 |

## 결론 및 권장 개선 사항
1. **분산 락 도입**: JVM 내부 락 대신 Redis/ZooKeeper/DB 락을 사용하거나, 완전히 DB 내 원자 연산만으로 동시성을 제어하도록 개선합니다.
2. **락/트랜잭션 실패 로그**: `consumeStock`, `incrementIssuedQuantity` 등에서 실패 시 경고와 컨텍스트를 남겨 문제 분석을 돕고, 필요 시 재시도 전략(예: 지수 백오프)을 적용합니다.
3. **테스트 강화**: 현재 통합 테스트는 단일 프로세스에서만 검증하므로, 분산 환경을 가정한 시나리오(멀티 JVM 시뮬레이션 또는 Chaos 테스트)를 고려합니다.
4. **도메인별 동시성 정책 문서화**: 어떤 쿼리가 snapshot 일관성을 요구하고 어떤 것은 eventually consistent해도 되는지 명시하면, 향후 유지보수 시 실수가 줄어듭니다.
