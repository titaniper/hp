
# AI
## super claude 설치
```
python3 -m venv path/to/venv
source path/to/venv/bin/activate
python3 -m pip install xyz
npm install -g @bifrost_inc/superclaude && superclaude install
```

# Kotlin Spring 동시성 제어 방식 비교 보고서

## 목차
1. [동시성 제어가 필요한 이유](#1-동시성-제어가-필요한-이유)
2. [동시성 제어 방식 개요](#2-동시성-제어-방식-개요)
3. [방식별 상세 분석](#3-방식별-상세-분석)
4. [성능 및 확장성 비교](#4-성능-및-확장성-비교)
5. [시나리오별 권장사항](#5-시나리오별-권장사항)
6. [현재 프로젝트 적용 사례](#6-현재-프로젝트-적용-사례)

---

## 1. 동시성 제어가 필요한 이유

### 1.1 Race Condition (경쟁 조건)
여러 스레드가 동시에 같은 자원을 수정할 때 실행 순서에 따라 결과가 달라지는 문제

```kotlin
// 문제 상황 예시
Thread A: val current = 1000  // 현재 포인트 읽기
Thread B: val current = 1000  // 현재 포인트 읽기
Thread A: save(current + 100) // 1100 저장
Thread B: save(current + 200) // 1200 저장 (Thread A의 +100이 손실됨!)
// 결과: 1300이 아닌 1200
```

### 1.2 Lost Update (갱신 분실)
동시에 수행된 업데이트 중 하나가 손실되는 문제

### 1.3 Dirty Read (더티 리드)
다른 트랜잭션이 커밋하지 않은 데이터를 읽는 문제

---

## 2. 동시성 제어 방식 개요

| 방식 | 범위 | 잠금 시점 | 확장성 | 주요 사용처 |
|------|------|----------|--------|------------|
| **synchronized** | JVM 단일 프로세스 | 메서드/블록 진입 시 | 단일 서버 | 간단한 임계 영역 |
| **ReentrantLock** | JVM 단일 프로세스 | 명시적 lock() 호출 시 | 단일 서버 | 복잡한 락 제어 필요 시 |
| **Database 비관적 락** | DB 레벨 | SELECT ... FOR UPDATE | 다중 서버 가능 | 긴 트랜잭션, 충돌 빈번 |
| **Database 낙관적 락** | 애플리케이션 레벨 | 업데이트 시 버전 체크 | 다중 서버 가능 | 충돌 드문 읽기 위주 |
| **Redis 분산 락** | 캐시 서버 레벨 | 락 키 획득 시 | 다중 서버 | MSA, 분산 환경 |
| **Coroutine Mutex** | 코루틴 컨텍스트 | suspend 함수 내 | 단일 서버 | 코루틴 기반 비동기 |

---

## 3. 방식별 상세 분석

### 3.1 synchronized 키워드

#### 개요
Java의 가장 기본적인 동기화 메커니즘으로, 모니터 락을 사용하여 임계 영역을 보호합니다.

#### 구현 예시
```kotlin
class PointService {
    @Synchronized
    fun charge(userId: Long, amount: Long): UserPoint {
        val current = userPointTable.selectById(userId)
        val updated = current.charge(amount)
        return userPointTable.insertOrUpdate(userId, updated.point)
    }
}
```

#### 장점
- ✅ **간결한 문법**: 어노테이션 하나로 동기화 가능
- ✅ **자동 락 해제**: 메서드 종료 시 자동으로 락 해제 (finally 불필요)
- ✅ **JVM 최적화**: HotSpot JVM의 락 최적화 (Biased Locking, Lock Coarsening) 지원
- ✅ **추가 의존성 불필요**: Java 기본 기능

#### 단점
- ❌ **세밀한 제어 불가**: 타임아웃, 인터럽트 처리 불가
- ❌ **공정성 보장 안 됨**: 대기 중인 스레드 중 어느 것이 먼저 획득할지 불확정
- ❌ **전체 메서드 잠금**: 메서드 단위 동기화 시 불필요한 코드도 잠금
- ❌ **단일 서버 한정**: 여러 서버 인스턴스 간 동기화 불가능
- ❌ **모든 사용자 직렬화**: 사용자 ID 관계없이 모든 요청이 순차 처리됨 (처리량 저하)

#### 적합한 상황
- 단일 서버 환경
- 짧고 간단한 임계 영역
- 사용자 구분 없이 전역 자원 보호 필요

---

### 3.2 ReentrantLock (현재 프로젝트 적용)

#### 개요
`java.util.concurrent.locks` 패키지의 명시적 락 구현체로, synchronized보다 유연한 제어가 가능합니다.

#### 구현 예시
```kotlin
class PointService {
    // 사용자별로 독립적인 락 관리
    private val userLocks = ConcurrentHashMap<Long, ReentrantLock>()

    fun charge(userId: Long, amount: Long): UserPoint {
        return withUserLock(userId) {
            val current = userPointTable.selectById(userId)
            val updated = current.charge(amount)
            userPointTable.insertOrUpdate(userId, updated.point)
        }
    }

    private fun <T> withUserLock(userId: Long, block: () -> T): T {
        val lock = userLocks.computeIfAbsent(userId) { ReentrantLock() }
        lock.lock()
        return try {
            block()
        } finally {
            lock.unlock()
            // 대기 스레드 없으면 메모리에서 제거
            if (!lock.hasQueuedThreads()) {
                userLocks.remove(userId, lock)
            }
        }
    }
}
```

#### 장점
- ✅ **사용자별 독립 락**: 다른 사용자 요청은 병렬 처리 가능 (높은 처리량)
- ✅ **타임아웃 지원**: `tryLock(timeout)` - 무한 대기 방지
- ✅ **인터럽트 대응**: `lockInterruptibly()` - 대기 중 인터럽트 가능
- ✅ **공정성 옵션**: `ReentrantLock(fair = true)` - FIFO 순서 보장
- ✅ **조건 변수**: `Condition` 객체로 복잡한 대기/신호 구현 가능
- ✅ **락 상태 조회**: `isLocked()`, `hasQueuedThreads()` 등 모니터링 가능
- ✅ **메모리 효율**: 사용하지 않는 락 자동 제거

#### 단점
- ❌ **명시적 관리 필요**: `unlock()` 누락 시 데드락 위험 (반드시 finally 블록 사용)
- ❌ **복잡도 증가**: synchronized보다 코드가 길고 복잡
- ❌ **단일 서버 한정**: 여러 서버 인스턴스 간 동기화 불가능
- ❌ **메모리 오버헤드**: 사용자별 Lock 객체 생성 (수천만 사용자 시 주의)

#### 적합한 상황
- 단일 서버 환경
- 사용자별/리소스별 독립 동기화 필요
- 타임아웃, 공정성 등 고급 제어 필요
- 높은 동시 처리량 요구 (다른 사용자는 병렬 처리)

---

### 3.3 Database 비관적 락 (Pessimistic Lock)

#### 개요
데이터베이스의 행 수준 잠금을 사용하여 트랜잭션 시작부터 종료까지 데이터를 독점적으로 사용합니다.

#### 구현 예시
```kotlin
// JPA Repository
interface UserPointRepository : JpaRepository<UserPointEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM UserPointEntity u WHERE u.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): UserPointEntity?
}

// Service
@Transactional
fun charge(userId: Long, amount: Long): UserPoint {
    // SELECT ... FOR UPDATE 쿼리 실행
    val entity = userPointRepository.findByIdForUpdate(userId)
        ?: throw IllegalArgumentException("User not found")

    entity.point += amount
    return entity.toDomain()
    // 트랜잭션 종료 시 자동으로 락 해제
}
```

#### 실행되는 SQL
```sql
SELECT * FROM user_point WHERE id = 1 FOR UPDATE;
-- 이 행을 다른 트랜잭션이 수정하지 못하도록 잠금
```

#### 장점
- ✅ **다중 서버 지원**: DB가 동기화 포인트 역할 (분산 환경 가능)
- ✅ **데이터 정합성 보장**: 트랜잭션 ACID 속성 활용
- ✅ **충돌 사전 방지**: 락 획득 실패 시 대기 (충돌 회피)
- ✅ **JPA 통합**: Spring Data JPA의 `@Lock` 어노테이션으로 간편 사용
- ✅ **복잡한 비즈니스 로직**: 여러 테이블 참조 시에도 일관성 유지

#### 단점
- ❌ **DB 부하**: 락 획득/해제가 DB에 부담 (커넥션 점유 시간 증가)
- ❌ **데드락 위험**: 여러 행을 잠그는 경우 데드락 가능성 (순서 주의 필요)
- ❌ **성능 저하**: 대기 시간이 길면 전체 처리량 감소
- ❌ **타임아웃 관리**: DB 설정에 따라 무한 대기 또는 타임아웃
- ❌ **읽기 성능 영향**: 읽기 쿼리도 대기해야 함 (Shared Lock 제외)

#### 적합한 상황
- 다중 서버 환경 (로드 밸런서 뒤 여러 인스턴스)
- 충돌이 빈번하게 발생하는 경우
- 긴 트랜잭션이 필요한 복잡한 비즈니스 로직
- 데이터 정합성이 최우선인 금융/결제 시스템

---

### 3.4 Database 낙관적 락 (Optimistic Lock)

#### 개요
데이터를 읽을 때는 락을 걸지 않고, 업데이트 시점에 버전 번호를 체크하여 충돌을 감지합니다.

#### 구현 예시
```kotlin
// Entity에 @Version 추가
@Entity
data class UserPointEntity(
    @Id val id: Long,
    var point: Long,

    @Version  // JPA가 자동으로 버전 관리
    var version: Long = 0
)

// Service
@Transactional
fun charge(userId: Long, amount: Long): UserPoint {
    val entity = userPointRepository.findById(userId)
        .orElseThrow { IllegalArgumentException("User not found") }

    entity.point += amount

    // JPA가 UPDATE 시 자동으로 버전 체크
    // UPDATE user_point SET point = ?, version = version + 1
    // WHERE id = ? AND version = ?

    return entity.toDomain()
}
```

#### 실행되는 SQL
```sql
-- 읽기 (락 없음)
SELECT id, point, version FROM user_point WHERE id = 1;

-- 쓰기 (버전 체크)
UPDATE user_point
SET point = 1100, version = 2
WHERE id = 1 AND version = 1;  -- 버전이 다르면 0건 업데이트

-- 0건 업데이트 시 JPA가 OptimisticLockException 발생
```

#### 장점
- ✅ **읽기 성능 우수**: 락 없이 읽기 가능 (SELECT 속도 빠름)
- ✅ **다중 서버 지원**: DB 레벨에서 버전 체크 (분산 환경 가능)
- ✅ **데드락 없음**: 락을 걸지 않으므로 데드락 불가능
- ✅ **확장성 좋음**: 충돌이 드문 경우 높은 처리량
- ✅ **JPA 자동화**: `@Version` 어노테이션만으로 자동 관리

#### 단점
- ❌ **충돌 시 재시도 필요**: `OptimisticLockException` 발생 시 로직 재실행
- ❌ **충돌 빈번 시 비효율**: 계속 실패하면 오히려 성능 저하
- ❌ **복잡한 에러 처리**: 재시도 로직 구현 필요
- ❌ **충돌 감지 지연**: 업데이트 시점까지 충돌을 모름

#### 재시도 구현 예시
```kotlin
@Service
class PointService {
    @Retryable(
        value = [OptimisticLockException::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 100)
    )
    @Transactional
    fun charge(userId: Long, amount: Long): UserPoint {
        // 낙관적 락 로직
    }
}
```

#### 적합한 상황
- 다중 서버 환경
- 충돌이 드물고 읽기가 많은 경우
- 짧은 트랜잭션
- 높은 동시성과 처리량 요구

---

### 3.5 Redis 분산 락 (Distributed Lock)

#### 개요
Redis의 원자적 연산을 활용하여 여러 서버 인스턴스 간 동기화를 수행합니다.

#### 구현 예시 (Redisson 사용)
```kotlin
// build.gradle.kts
dependencies {
    implementation("org.redisson:redisson-spring-boot-starter:3.23.0")
}

// Service
@Service
class PointService(
    private val redissonClient: RedissonClient
) {
    fun charge(userId: Long, amount: Long): UserPoint {
        val lockKey = "point:lock:$userId"
        val lock = redissonClient.getLock(lockKey)

        // 락 획득 시도 (최대 10초 대기, 20초 후 자동 해제)
        val acquired = lock.tryLock(10, 20, TimeUnit.SECONDS)
        if (!acquired) {
            throw IllegalStateException("Lock acquisition failed")
        }

        return try {
            val current = userPointTable.selectById(userId)
            val updated = current.charge(amount)
            userPointTable.insertOrUpdate(userId, updated.point)
        } finally {
            lock.unlock()
        }
    }
}
```

#### Redisson의 락 메커니즘
```lua
-- Redis에서 실행되는 Lua 스크립트 (원자적 실행)
if redis.call('exists', KEYS[1]) == 0 then
    redis.call('hset', KEYS[1], ARGV[2], 1)
    redis.call('pexpire', KEYS[1], ARGV[1])
    return nil
end
return redis.call('pttl', KEYS[1])
```

#### 장점
- ✅ **다중 서버 지원**: 여러 인스턴스가 Redis를 통해 동기화
- ✅ **높은 성능**: Redis의 인메모리 특성으로 빠른 락 획득/해제
- ✅ **TTL 자동 관리**: 락 점유 시간 제한 (데드락 방지)
- ✅ **Watch Dog**: Redisson이 자동으로 락 갱신 (긴 작업도 안전)
- ✅ **다양한 락 타입**: Fair Lock, MultiLock, ReadWriteLock 등 지원
- ✅ **MSA 친화적**: 서비스 간 동기화 가능

#### 단점
- ❌ **외부 의존성**: Redis 서버 필요 (장애 시 전체 락 불가)
- ❌ **네트워크 오버헤드**: Redis 왕복 시간 발생
- ❌ **복잡도 증가**: Redis 설치, 관리, 모니터링 필요
- ❌ **비용**: Redis 인프라 운영 비용
- ❌ **일관성 이슈**: Redis 장애 시 데이터 불일치 가능 (Redis는 영속성 보장 약함)

#### Redis 클러스터 모드 (고가용성)
```kotlin
// Redis Sentinel 또는 Cluster 사용
@Configuration
class RedisConfig {
    @Bean
    fun redissonClient(): RedissonClient {
        val config = Config()
        config.useClusterServers()
            .addNodeAddress("redis://127.0.0.1:7001", "redis://127.0.0.1:7002")
        return Redisson.create(config)
    }
}
```

#### 적합한 상황
- MSA (마이크로서비스 아키텍처)
- 다중 서버 환경 (Auto Scaling)
- 서비스 간 동기화 필요
- 높은 처리량과 낮은 레이턴시 요구
- Redis 인프라 운영 가능한 조직

---

### 3.6 Kotlin Coroutine Mutex

#### 개요
코루틴 환경에서 non-blocking 방식으로 동기화를 수행하는 경량 뮤텍스입니다.

#### 구현 예시
```kotlin
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Service
class PointService {
    // 사용자별 Mutex 관리
    private val userMutexes = ConcurrentHashMap<Long, Mutex>()

    suspend fun charge(userId: Long, amount: Long): UserPoint {
        val mutex = userMutexes.computeIfAbsent(userId) { Mutex() }

        return mutex.withLock {
            // suspend 함수 내에서 비동기 작업 가능
            val current = userPointRepository.findById(userId)
            val updated = current.charge(amount)
            userPointRepository.save(updated)
        }
    }
}

// Controller (Coroutine 지원)
@RestController
class PointController(private val pointService: PointService) {

    @PostMapping("/point/{id}/charge")
    suspend fun charge(
        @PathVariable id: Long,
        @RequestBody request: ChargeRequest
    ): UserPoint {
        return pointService.charge(id, request.amount)
    }
}
```

#### 장점
- ✅ **Non-Blocking**: 스레드를 블로킹하지 않음 (높은 동시성)
- ✅ **경량**: 코루틴은 스레드보다 메모리 사용량 적음
- ✅ **구조적 동시성**: 코루틴 스코프와 통합 (취소 전파)
- ✅ **Kotlin Native 지원**: Kotlin 멀티플랫폼에서도 사용 가능
- ✅ **Suspend 함수**: 비동기 IO와 자연스럽게 결합

#### 단점
- ❌ **Coroutine 필수**: 전체 애플리케이션이 코루틴 기반이어야 함
- ❌ **단일 서버 한정**: 분산 환경 지원 안 됨
- ❌ **JVM 전용 아님**: 코루틴 런타임 이해 필요
- ❌ **Spring 통합 미성숙**: Spring MVC/WebFlux와 혼용 시 주의

#### 적합한 상황
- Kotlin Coroutine 기반 애플리케이션
- Spring WebFlux (리액티브 스택)
- 높은 동시 요청 처리 (C10K 문제)
- Non-blocking IO 필요

---

## 4. 성능 및 확장성 비교

### 4.1 처리량 비교 (Throughput)

| 방식 | 단일 서버 TPS | 다중 서버 확장성 | 지연 시간 |
|------|--------------|----------------|---------|
| synchronized (전역) | 낮음 (모든 요청 직렬화) | 불가능 | 매우 낮음 |
| ReentrantLock (사용자별) | **높음** (사용자별 병렬) | 불가능 | 매우 낮음 |
| DB 비관적 락 | 중간 (DB 병목) | 가능 | 중간 |
| DB 낙관적 락 | 높음 (충돌 드물 때) | 가능 | 낮음 |
| Redis 분산 락 | **높음** | **가능** | 낮음~중간 |
| Coroutine Mutex | **매우 높음** (Non-blocking) | 불가능 | 매우 낮음 |

### 4.2 메모리 사용량

| 방식 | 메모리 오버헤드 | 비고 |
|------|----------------|------|
| synchronized | 최소 (객체 헤더의 Monitor) | - |
| ReentrantLock | 중간 (Lock 객체) | 사용자당 ~64 bytes |
| DB 락 | 없음 (DB 서버에서 관리) | - |
| Redis 락 | 중간 (Redis 키) | 사용자당 ~100 bytes (Redis) |
| Coroutine Mutex | 최소 (경량 객체) | 사용자당 ~32 bytes |

### 4.3 충돌 빈도별 권장 방식

```
충돌 빈도 낮음 ──────────────────────────────────> 충돌 빈도 높음
    |                                                    |
낙관적 락 (최선)                                  비관적 락 (최선)
    |                                                    |
    └─ 읽기가 많고 쓰기가 적음                          └─ 쓰기가 많고 충돌 빈번
    └─ 재시도 비용이 낮음                               └─ 대기 시간이 허용됨
```

### 4.4 시스템 아키텍처별 선택

#### 단일 서버 (Monolith)
```
간단한 로직: synchronized
복잡한 로직: ReentrantLock (사용자별)
코루틴 앱: Coroutine Mutex
```

#### 다중 서버 (Scale-out)
```
읽기 많음: DB 낙관적 락
쓰기 많음: DB 비관적 락
초고성능: Redis 분산 락
```

#### MSA (Microservices)
```
서비스 간 동기화: Redis 분산 락
서비스 내 동기화: ReentrantLock + Redis (하이브리드)
```

---

## 5. 시나리오별 권장사항

### 5.1 포인트 충전 시스템 (현재 프로젝트)

**요구사항**
- 사용자별 독립적인 처리 (높은 병렬성)
- 데이터 정합성 보장 (충전 금액 손실 방지)
- 단일 서버 환경

**권장 방식: ReentrantLock (사용자별)** ✅
- 다른 사용자 요청은 병렬 처리로 높은 처리량
- 같은 사용자는 순차 처리로 데이터 정합성 보장
- 외부 의존성 없이 간단한 구현

### 5.2 재고 차감 시스템 (E-Commerce)

**요구사항**
- 상품별 재고 정확성 (overselling 방지)
- 다중 서버 환경 (Black Friday 트래픽 대응)
- 높은 동시 주문 처리

**권장 방식: Redis 분산 락** ✅
- 다중 서버 간 동기화 가능
- 높은 처리량 (Redis 인메모리 성능)
- 상품별 독립 락 (다른 상품은 병렬 처리)

**대안: DB 비관적 락**
- Redis 인프라 부담 시 선택
- 트랜잭션과 자연스럽게 통합

### 5.3 좋아요/조회수 시스템 (SNS)

**요구사항**
- 초당 수만 건 요청 처리
- 약간의 데이터 불일치 허용
- 다중 서버 환경

**권장 방식: DB 낙관적 락 + 비동기 처리** ✅
- 읽기 성능 우수 (락 없음)
- 충돌 시 재시도 (대부분 성공)
- 배치 처리로 DB 부하 분산

**대안: Redis Increment**
```kotlin
redisTemplate.opsForValue().increment("likes:$postId")
// 주기적으로 DB에 동기화
```

### 5.4 은행 계좌 이체 시스템

**요구사항**
- 절대적인 데이터 정합성 (금액 손실 불가)
- 복잡한 트랜잭션 (송금/수신 계좌 동시 수정)
- 다중 서버 환경

**권장 방식: DB 비관적 락 + 2PC (Two-Phase Commit)** ✅
- 트랜잭션 ACID 보장
- 데드락 방지 위한 락 순서 통일 (계좌 ID 오름차순)
- 감사 로그 자동 생성 (DB 트리거)

```kotlin
@Transactional
fun transfer(fromId: Long, toId: Long, amount: Long) {
    // 데드락 방지: 항상 작은 ID부터 락 획득
    val ids = listOf(fromId, toId).sorted()

    val from = accountRepository.findByIdForUpdate(ids[0])!!
    val to = accountRepository.findByIdForUpdate(ids[1])!!

    from.withdraw(amount)
    to.deposit(amount)
}
```

### 5.5 분산 작업 스케줄러 (Batch Job)

**요구사항**
- 동일 작업 중복 실행 방지
- 여러 워커 노드에서 작업 분배
- 작업 실패 시 재할당

**권장 방식: Redis 분산 락 + TTL** ✅
- 작업별 락 (다른 작업은 병렬 실행)
- TTL로 장애 워커의 락 자동 해제
- Redisson의 Fair Lock으로 공정한 작업 분배

---

## 6. 현재 프로젝트 적용 사례

### 6.1 프로젝트 개요
Spring Boot + Kotlin 기반 포인트 관리 시스템

### 6.2 선택한 방식: ReentrantLock (사용자별)

#### 선택 이유
1. **높은 처리량**: 다른 사용자의 요청은 병렬 처리 가능
2. **간단한 구현**: 외부 의존성 없이 JDK 기본 API 사용
3. **단일 서버 환경**: 현재 요구사항에 충분
4. **메모리 효율**: 사용하지 않는 락 자동 제거

#### 구현 코드 분석
```kotlin
// src/main/kotlin/io/hhplus/tdd/point/PointService.kt:59
private val userLocks = ConcurrentHashMap<Long, ReentrantLock>()

private fun <T> withUserLock(userId: Long, block: () -> T): T {
    // 1. 사용자별 락 획득 또는 생성 (원자적 연산)
    val lock = userLocks.computeIfAbsent(userId) { ReentrantLock() }

    // 2. 락 획득 (다른 스레드가 점유 중이면 대기)
    lock.lock()

    return try {
        // 3. 비즈니스 로직 실행
        block()
    } finally {
        // 4. 락 해제 (반드시 실행)
        lock.unlock()

        // 5. 메모리 정리: 대기 스레드 없으면 맵에서 제거
        if (!lock.hasQueuedThreads()) {
            userLocks.remove(userId, lock)
        }
    }
}
```

#### 동시성 테스트 결과
```kotlin
// src/test/kotlin/io/hhplus/tdd/point/PointConcurrencyIntegrationTest.kt:67
@Test
fun `concurrent charges for same user accumulate correctly`() {
    // 50개 스레드가 동시에 100 포인트 충전
    // 예상: 5,000 포인트 (50 * 100)
    // 실제: 5,000 포인트 (데이터 손실 없음 ✅)
}
```

### 6.3 성능 특성

#### 벤치마크 (가상 시나리오)
```
단일 사용자 충전 (순차 처리):
- 1,000 requests: ~300ms
- TPS: ~3,333

다중 사용자 충전 (병렬 처리):
- 100 users * 10 requests: ~300ms
- TPS: ~3,333 (사용자별 병렬로 확장)
```

#### 메모리 사용량
```
- Lock 객체: ~64 bytes/user
- 100,000 active users: ~6.4 MB
- 무시 가능한 수준
```

### 6.4 장점

✅ **높은 처리량**
- User A와 User B의 요청이 동시에 들어오면 병렬 처리
- N개 사용자 = N배 처리량 (스레드 풀 허용 범위 내)

✅ **데이터 정합성**
- 같은 사용자의 요청은 순차 처리
- Race Condition 완전 방지

✅ **간단한 구현**
- 외부 의존성 없음 (Redis, DB 락 불필요)
- 코드 복잡도 낮음

✅ **메모리 효율**
- 사용하지 않는 락 자동 제거
- 장기간 운영 시에도 메모리 누수 없음

### 6.5 단점

❌ **단일 서버 한정**
- 여러 서버 인스턴스에서는 동작하지 않음
- Scale-out 시 Redis 분산 락으로 전환 필요

❌ **서버 재시작 시 락 소멸**
- 락 상태가 메모리에만 존재
- 재시작 시 진행 중인 작업 추적 불가 (DB 트랜잭션으로 보완)

❌ **Lock 누수 위험**
- `unlock()` 누락 시 데드락
- finally 블록 사용으로 완화

### 6.6 향후 확장 계획

#### Phase 1: 단일 서버 (현재)
```kotlin
ReentrantLock (사용자별) ✅
```

#### Phase 2: 다중 서버 (Scale-out)
```kotlin
// Redis 분산 락으로 전환
@Service
class PointService(
    private val redissonClient: RedissonClient
) {
    fun charge(userId: Long, amount: Long): UserPoint {
        val lock = redissonClient.getLock("point:$userId")
        lock.lock()
        try {
            // 기존 로직
        } finally {
            lock.unlock()
        }
    }
}
```

#### Phase 3: 초고성능 (MSA)
```kotlin
// Redis + DB 낙관적 락 하이브리드
// 1차: Redis 락으로 동시성 제어
// 2차: DB 버전으로 최종 정합성 보장
```

---

## 7. 결론 및 권장사항

### 7.1 의사결정 트리

```
단일 서버? ──YES──> 사용자별 독립 처리 필요? ──YES──> ReentrantLock ✅
    |                                         └─NO──> synchronized
    └─NO
       |
       ├─> 충돌 드뭄? ──YES──> DB 낙관적 락 ✅
       |                └─NO──> 충돌 빈번? ──YES──> DB 비관적 락 ✅
       |
       └─> 초고성능 필요? ──YES──> Redis 분산 락 ✅
```

### 7.2 핵심 원칙

1. **Start Simple**: synchronized로 시작 → 병목 발견 시 최적화
2. **Measure First**: 추측 말고 프로파일링 → 데이터 기반 결정
3. **Scale When Needed**: 단일 서버로 충분하면 복잡도 추가 금지
4. **Test Concurrency**: 동시성 테스트는 필수 (단위 테스트만으로 부족)

### 7.3 마이그레이션 가이드

#### ReentrantLock → Redis 분산 락
```kotlin
// Before (현재)
private val userLocks = ConcurrentHashMap<Long, ReentrantLock>()

// After (분산 환경)
@Autowired private lateinit var redissonClient: RedissonClient

fun charge(userId: Long, amount: Long): UserPoint {
    val lock = redissonClient.getLock("point:$userId")
    lock.lock(10, TimeUnit.SECONDS)  // 타임아웃 추가
    try {
        // 기존 로직 그대로
    } finally {
        lock.unlock()
    }
}
```

**변경 포인트**
- Lock 생성: `ReentrantLock()` → `redissonClient.getLock(key)`
- Lock 해제: finally 블록 유지 (동일)
- 타임아웃: 추가 (Redis 네트워크 장애 대비)

---

## 8. 참고 자료

### 공식 문서
- [Java Concurrency (Oracle)](https://docs.oracle.com/javase/tutorial/essential/concurrency/)
- [Kotlin Coroutines (JetBrains)](https://kotlinlang.org/docs/coroutines-guide.html)
- [Spring Data JPA Locking](https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html)
- [Redisson Documentation](https://redisson.org/)

### 성능 벤치마크
- [Distributed Lock Benchmark](https://github.com/redisson/redisson/wiki/9.-Distributed-locks-and-synchronizers)
- [JMH (Java Microbenchmark Harness)](https://github.com/openjdk/jmh)

### 추천 도서
- "Java Concurrency in Practice" (Brian Goetz)
- "Designing Data-Intensive Applications" (Martin Kleppmann)

---

## Point Service Enhancements (기존 내용)

- 포인트는 100원 단위로만 충전/사용 가능합니다.
- 최대 보유 포인트는 1,000,000원이며 초과 시 예외가 발생합니다.
- 잔고 부족, 단위 위반, 최대치 초과 등 모든 정책 실패는 HTTP 400 응답으로 매핑됩니다.

## Test Coverage (기존 내용)

- **단위 테스트**: `PointServiceTest`
  - 정상 시나리오 + 정책 위반(단위/최대치/잔고 부족) 검증.
- **통합 테스트**: `PointControllerIntegrationTest`
  - 포인트 조회, 충전, 사용, 내역 조회 REST API 및 예외 케이스 검증.
- **동시성 통합 테스트**: `PointConcurrencyIntegrationTest`
  - 동일 사용자에 대한 동시 충전 요청이 누락 없이 반영되는지 확인.
