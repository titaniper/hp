# Redisson RLock `waitTime` / `holdTime`(=`leaseTime`) 이해

Redisson은 Redis를 기반으로 한 고수준 분산 락 구현체다. `RLock.tryLock(waitTime, leaseTime, unit)`을 많이 사용하는데, 파라미터 의미와 내부 동작을 정확히 이해하지 못하면 기대와 다른 동작(타임아웃, 조기 해제, Stampede 등)이 발생한다. 이 문서는 `waitTime`, `holdTime(=leaseTime)` 개념과 Redisson 내부 메커니즘을 정리한다.

## 1. API 개요
- `lock()`
  - 즉시 락을 획득하려 시도하고 성공하면 기본 TTL(기본 30초)을 걸고 Watchdog이 주기적으로 연장한다.
- `tryLock()`
  - **오버로드 A**: `tryLock()` → 락이 비어 있으면 즉시 true, 아니면 false.
  - **오버로드 B**: `tryLock(waitTime, leaseTime, unit)` → 지정 시간 동안 대기하며 락 획득을 재시도하고, 성공 시 leaseTime 만큼 점유한다. 대부분 이 오버로드에서 `waitTime`, `holdTime`을 설정한다.

## 2. 파라미터 의미
| 파라미터 | 설명 | 동작 시나리오 |
| --- | --- | --- |
| `waitTime` | 락을 얻기 위해 **최대 얼마 동안 기다릴지**를 지정한다. 0 또는 음수면 즉시 시도 후 실패한다. | `waitTime=2s`라면 2초 동안 Pub/Sub 알림을 기다리거나 일정 간격으로 재시도한 뒤 실패 시 `false`를 반환한다. |
| `leaseTime` (`holdTime`) | 락을 획득한 **이후 유지할 시간**이다. 만료되면 자동으로 해제된다. Watchdog이 비활성화되고, 명시 시간 내에 비즈니스 로직을 끝내야 한다. | `leaseTime=5s`면 5초 후 Redis TTL이 만료되어 락이 풀린다. |

> **holdTime = leaseTime**: 일부 문서나 코드에서 holdTime이라는 변수명으로 사용하지만 Redisson 구현에서는 `leaseTime` 파라미터로 노출된다. 동일 개념이다.

## 3. 내부 동작(요약)
1. **획득 시도**
   - Lua 스크립트로 `HSET` + `PEXPIRE`를 수행한다. 락 키 구조는 `lock:{name}` 해시에 `field = <clientId:threadId>`, `value = 재진입 카운트` 형식이다.
   - 이미 같은 스레드가 잡고 있다면 카운트를 증가시키고 TTL만 갱신한다(재진입 지원).
2. **대기(waitTime)**
   - 락이 다른 스레드에 의해 점유된 경우, Redisson은 `Pub/Sub` 채널(`redisson_lock__channel:{name}`)을 구독하고 대기한다.
   - `waitTime` 내에서 해지 알림을 받거나, 주기적으로 재시도한다. 시간 초과 시 false.
3. **해제(Unlock)**
   - Lua 스크립트로 카운트를 감소시키고, 0이면 해시를 삭제한 뒤 **Pub/Sub** 채널에 `unlock` 이벤트를 publish 한다.
4. **Watchdog**
   - `lock()` 또는 `tryLock(waitTime, -1, unit)`처럼 leaseTime을 지정하지 않은 경우 `lockWatchdogTimeout`(기본 30초)이 TTL로 걸리고, 백그라운드 스케줄러가 TTL의 1/3마다 `PEXPIRE`를 갱신한다.
   - `leaseTime`을 명시하면 Watchdog은 비활성화되고, TTL 만료 시점에 자동 해제된다.

## 4. 파라미터 선택 가이드
- `waitTime`
  - 사용자 경험 관점에서 허용 가능한 대기 시간으로 설정한다. 예: `/coupon` 락에서 2초 이상 대기하면 UX가 나빠지므로 `LOCK_WAIT_SECONDS = 2`(`src/main/kotlin/io/joopang/services/coupon/infrastructure/CouponLockManagerImpl.kt:55-57`).
  - 0 이하로 두면 스핀락처럼 동작하며 Redis 부하가 커지므로 가급적 Pub/Sub 대기를 활용한다.
- `leaseTime`
  - 비즈니스 로직 최대 실행 시간보다 길어야 한다. 너무 짧으면 작업 도중 락이 풀려 일관성 깨짐.
  - Watchdog을 쓰지 않거나 트랜잭션 경계에 맞춰 명시적으로 제어하고 싶을 때만 설정한다. 그렇지 않으면 `lockWatchdogTimeout`(기본 30초, `redisson.lockWatchdogTimeout`으로 조정 가능)을 이용해 자동 연장을 맡기는 편이 안전하다.
- `InterruptedException`
  - `waitTime` 동안 대기하는 동안 인터럽트가 들어오면 예외가 발생하므로 호출부에서 인터럽트 플래그를 복구하거나 적절히 처리해야 한다(현재 `CouponLockManagerImpl` 참고).

## 5. 내부 컴포넌트 상세
- **Redis 해시 키 구조**
  - Key: `lock:{name}`
  - Field: `<clientUUID>:<threadId>`
  - Value: 재진입 카운트
  - TTL: `leaseTime` 또는 `lockWatchdogTimeout`
- **Unlock Pub/Sub**
  - 채널 이름: `redisson_lock__channel:{name}`
  - 해제 시 메시지를 발행하고, 대기 중인 클라이언트는 해당 메시지를 받아 즉시 재시도한다. 이 구조 덕분에 단순한 `SETNX` 스핀락 대비 Redis 부하가 적다.
- **Watchdog 스케줄러**
  - 클라이언트 JVM 내부에서 `HashedWheelTimer` 기반으로 동작한다. 락을 가진 스레드가 살아 있는지 주기적으로 확인하고 TTL을 연장한다.
  - 애플리케이션이 크래시하거나 네트워크가 끊기면 Watchdog 갱신이 멈추고 TTL 만료로 락이 자동 해제된다.

## 6. 실무 체크포인트
1. **장기 작업 주의**: 배치/ETL처럼 오래 걸리는 작업은 `leaseTime`을 충분히 길게 두거나, 기본 `lock()` + Watchdog을 사용하고 작업 완료 시 명시적으로 `unlock()` 호출.
2. **장애 대비**: Redis 단일 노드 환경에서는 Redisson의 락도 싱글 포인트가 되므로 Sentinel/Cluster 구성 필요. RedLock은 운영 부담이 크므로 권장하지 않음(`reports/step11-12/learning/distributed_lock.md` 참고).
3. **모니터링**: `waitTime` 초과 비율, 평균 획득 대기 시간, 타임아웃 발생 수를 메트릭으로 수집하면 holdTime 조정 근거가 된다.
4. **복합 시나리오**: 읽기/쓰기 분리나 선착순 이벤트처럼 공정성이 필요하면 `RLock` 대신 `RFairLock`, `RReadWriteLock` 사용을 고려한다. 이때도 `waitTime`/`leaseTime` 파라미터가 동일하게 적용된다.

## 7. 추가 레퍼런스
- `src/main/kotlin/io/joopang/config/RedissonConfig.kt`: 클러스터/싱글 인스턴스 구성과 `Config.lockWatchdogTimeout` 설정 위치.
- `src/main/kotlin/io/joopang/services/coupon/infrastructure/CouponLockManagerImpl.kt`: `tryLock(waitTime, leaseTime)` 사용 예제.
- `reports/step11-12/learning/distributed_lock.md`: 다양한 Redis 락 패턴과 비교.
