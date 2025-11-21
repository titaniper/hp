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
| 7. Deadlock | 서로가 가진 락을 기다리다가 영원히 대기하는 상태. | 주문 흐름에서 `ProductLockManager`→`ProductRepository.consumeStock`→`OrderRepository.save` 순서로만 락을 획득하므로 데드락 가능성은 낮다. 하지만 `productLockManager`는 JVM 락이고 `consumeStock`은 DB 락을 획득하므로, 순서가 바뀌면 deadlock이 발생할 수 있다. 현재 코드는 MySQL deadlock 예외를 잡아 재시도하지 않으므로, 문제 발생 시 그대로 실패한다. Deadlock 감지 및 재시도 로직(backoff) 도입 권장. | ⚠ 재시도 로직 없음 |

## 실무 적용 가이드 (FAQ)

### 1. 쿠폰 발급 시스템에서 DB 락 / 분산락을 반드시 사용해야 할까?

**요약:**

- *“쿠폰 발급 API마다 매번 `select for update` 같은 락을 거는 패턴”* 은 **잘 사용하지 않는 편**이며,
- 대신 **원자적 연산(조건부 UPDATE)**, **DB 제약조건**, **캐시/Redis** 등으로 해결하는 경우가 더 많습니다.
- **분산락(예: Redis lock)** 도 “정말 불가피한 경우”가 아니면 잘 사용하지 않으며, 사용하더라도 **짧은 시간/작은 범위**로만 사용합니다.

#### 1-1. 쿠폰/재고 시스템에서 흔한 패턴들

##### 1. 조건부 UPDATE로 처리 (DB가 알아서 row lock)

```sql
UPDATE coupon
SET remain_count = remain_count - 1
WHERE coupon_id = ? AND remain_count > 0;
```

- 영향을 받은 row 수가 1이면: **발급 성공**
- 0이면: **이미 소진됨**
- DB는 이 UPDATE 하는 순간 **해당 row에 대한 락을 획득하지만**, 개발자가 직접 `select ... for update` 등으로 미리 락을 잡지는 않습니다.
- 이 방식이 **가장 흔한 패턴**입니다. (단순하며 트래픽 처리 효율이 좋음)

##### 2. “유저당 1장만” 같은 조건 → 유니크 제약으로 처리

```sql
CREATE UNIQUE INDEX ux_user_coupon ON user_coupon (user_id, coupon_id);
```

- 동시 요청이 들어와도 DB가 알아서 하나는 성공, 나머지는 **unique violation** 발생 → 애플리케이션에서 잡아서 “이미 발급됨” 으로 처리합니다.
- 별도의 락이나 분산락 없이, **DB의 제약조건을 동시성 제어 수단으로 사용하는 패턴**입니다.

##### 3. Redis/캐시 선차감 패턴

- 대량 트래픽 / 한정 쿠폰 같은 경우:
  - Redis에서 `DECR` 로 먼저 차감 (원자 연산)
  - 0 아래로 떨어지면 실패로 처리
  - 실제 DB에는 비동기/배치로 적재하거나, 트랜잭션 안에서 한 번 더 검증
- 이때도 보통 **개별 키에 대한 분산락을 따로 잡지 않고**, Redis의 `INCR/DECR/SETNX` 같은 원자 연산으로 해결하는 편입니다.

#### 1-2. 그럼 **DB 락 / 분산락은 언제 사용하는가?**

**DB 락(pessimistic lock, `select for update`)** 은 대체로:

- **트래픽이 많지 않고**,
- **비즈니스적으로 “꼭 순서 보장이 필요”** 하거나,
- **동시성 충돌 시 롤백 비용이 너무 큰 경우**
  에 사용하는 편이며, 정말 “핫한 쿠폰 발급 API” 같은 곳에는 잘 사용하지 않습니다. (성능 저하 우려)

**분산락(Redis RedLock, Zookeeper 등)** 은:

- *“정말로 전역에서 한 번에 딱 하나의 프로세스만 이 작업을 해야 한다”*
- 예: 배치/스케줄러가 동일 시간에 여러 인스턴스에서 돌 수 있는데, **동시에 두 번 실행되면 문제가 되는 경우**

같은 상황에서 주로 사용하며, “유저 쿠폰 1장씩 발급” 같이 **소규모, 고빈도** 작업에 분산락을 사용하는 것은 **보통 좋지 않은 선택**입니다.

> **정리**: 쿠폰 발급은 “락”보다는 “원자적 조건부 연산 + 제약조건 + (필요시) 캐시/Redis 원자 연산”으로 해결하는 것이 일반적입니다.

### 2. 낙관적 락은 사실상 DB 락이 아닌 것인가?

개념을 분리해서 이해하는 것이 좋습니다.

#### 2-1. 낙관적 락(Optimistic Lock)의 개념

낙관적 락은 보통 다음과 같은 패턴으로 구현합니다:

1. row를 읽을 때 `version` 컬럼도 같이 읽음
2. 업데이트할 때:

   ```sql
   UPDATE coupon
   SET remain_count = ?, version = version + 1
   WHERE coupon_id = ? AND version = ?;
   ```

3. 영향을 받은 row 수가 1 → **성공**
   0 → **누군가 먼저 수정하여 내가 읽을 때와 상태가 달라짐 → 충돌 → 재시도 or 실패 처리**

**핵심 포인트:**

- 낙관적 락은 **“선점해서 잡고 있는 락” 이 아니라 “나중에 충돌 났는지 검사하는 기법”**입니다.
- 따라서 “락을 건다”기보다는 **“버전 기반 충돌 감지”**에 가깝습니다.

#### 2-2. 그러면 이것이 DB 락인가, 아닌가?

엄밀히 말하면:

- **낙관적 락은 “전통적인 의미의 DB 락(뮤텍스처럼 오래 잡고 있는 락)”은 아닙니다.**
  - 미리 `select ... for update` 를 해서 행을 홀딩하지도 않고,
  - 애플리케이션 레벨에서 “동시 수정 여부를 검사”하는 로직에 가깝습니다.
- 하지만 **UPDATE 자체는 결국 DB 내부에서 row-level 락을 아주 짧은 시간 동안 잡습니다.**
  - 모든 UPDATE/INSERT/DELETE는 DB가 내부적으로 row/page 락을 잠깐 잡았다가 커밋하면서 해제합니다.
  - 이건 “낙관적 락이라서 특별히 락이 없다”가 아니라, 모든 쓰기 연산에 공통적인 동작입니다.

**정리하자면:**

- 우리가 흔히 말하는 “DB 락을 사용한다 / 안 한다”에서의 **락**은 → **pessimistic locking (`select ... for update` 로 미리 잡아두는 것)** 을 가리키는 경우가 많습니다.
- **낙관적 락은 그런 의미의 락은 아니며, 충돌 감지 전략**입니다.
- 그럼에도 불구하고, DB 내부적으로는 UPDATE 시 짧게 row lock을 거는 것은 맞습니다. (이는 낙관적/비관적과 무관하게 항상 필요한 최소한의 락입니다.)

## 결론 및 권장 개선 사항

1. **분산 락 도입**: JVM 내부 락 대신 Redis/ZooKeeper/DB 락을 사용하거나, 완전히 DB 내 원자 연산만으로 동시성을 제어하도록 개선합니다.
2. **락/트랜잭션 실패 로그**: `consumeStock`, `incrementIssuedQuantity` 등에서 실패 시 경고와 컨텍스트를 남겨 문제 분석을 돕고, 필요 시 재시도 전략(예: 지수 백오프)을 적용합니다.
3. **테스트 강화**: 현재 통합 테스트는 단일 프로세스에서만 검증하므로, 분산 환경을 가정한 시나리오(멀티 JVM 시뮬레이션 또는 Chaos 테스트)를 고려합니다.
4. **도메인별 동시성 정책 문서화**: 어떤 쿼리가 snapshot 일관성을 요구하고 어떤 것은 eventually consistent해도 되는지 명시하면, 향후 유지보수 시 실수가 줄어듭니다.
