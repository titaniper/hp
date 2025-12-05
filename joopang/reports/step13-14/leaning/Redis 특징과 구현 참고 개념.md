# Redis 특징과 구현 시 참고 개념

## Redis의 주요 특징
- **인메모리 기반**: 모든 데이터가 RAM에 상주하므로 초당 수십만 QPS, 하지만 메모리 비용과 eviction 정책 설계가 필수.
- **단일 쓰레드 이벤트 루프**: 명령 실행이 원자적이며 컨텍스트 전환 비용이 없다. 대신 느린 명령 하나가 전체 처리량을 막는다.
- **다양한 자료구조**: 문자열부터 스트림까지 고수준 추상화를 제공하여 애플리케이션 복잡도를 줄인다.
- **쉬운 복제/클러스터링**: 마스터-레플리카 구성과 샤딩 클러스터를 모두 지원, 수평 확장이 비교적 단순.
- **스크립팅/모듈**: Lua 스크립트로 원자적 연산을 커스터마이즈할 수 있고, 모듈로 새로운 자료구조(예: RedisJSON)를 추가 가능.

## 설계 시 고려할 추가 개념
### 1. Persistence 전략
| 방식 | 설명 | 장단점 |
| --- | --- | --- |
| RDB (스냅샷) | 일정 주기로 메모리 덤프 | 빠른 복구, I/O 스파이크 발생 가능 |
| AOF | 명령 로그를 Append | 더 작은 RPO, 파일 커짐 → 주기적 리라이트 필요 |
| 혼합 (6.0+) | RDB+증분AOF 결합 | 빠른 재시작 + 낮은 손실, 설정 복잡 |
- 운영에서는 `AOF + 매일 RDB 백업` 패턴이 일반적.

### 2. 복제 & 고가용성
- **레플리카 지연**: 네트워크/디스크에 따라 수백 ms 지연. 강한 일관성이 필요하면 쓰기 후 읽기 대상은 마스터로 고정하거나 `WAIT` 명령으로 동기화 레플리카 수를 보장.
- **Sentinel**: 마스터 장애 감지 및 자동 페일오버. 최소 3개 이상의 Sentinel 프로세스를 독립 호스트에 배치.
- **Cluster**: 16384 해시 슬롯으로 키를 분배. 키스페이스 조인 필요 시 `HASH TAG` 패턴(`key{userId}:field`).

### 3. 네트워크/프로토콜 최적화
- **Pipeline**: 다중 요청을 묶어 RTT를 줄임. 대량 쓰기 시 필수.
- **Client-side caching**: Redis 6 이후 `CLIENT TRACKING`으로 변경 알림을 받아 캐시 일관성을 유지.
- **I/O Thread**: 6.0+ 버전에서 네트워크 I/O만 멀티스레드화. CPU 코어 수에 맞춰 `io-threads`를 조정.

### 4. 메모리 및 데이터 관리
- **maxmemory 정책**: `volatile-lru`, `allkeys-lfu` 등. 서비스별로 다르게 운영하려면 인스턴스를 분리.
- **Keyspace Events**: 키 만료/생성을 Pub/Sub으로 수신하여 캐시 동기화. 이벤트 종류별로 선택적 활성화.
- **Modules**: RedisBloom, RedisSearch 등 고급 기능을 별도 서비스 없이 도입 가능. 단, 업그레이드 및 호환성 체크 필요.

### 5. 보안/운영 가이드
- 인증(`requirepass`) 및 ACL을 사용하고, 외부 노출 시 TLS/Tunneling 적용.
- 모니터링 지표: `used_memory`, `connected_clients`, `evicted_keys`, `rejected_connections`, `latency spikes`. 장애 재현을 위해 `LATENCY DOCTOR` 활용.
- 백업 파일은 압축/암호화하여 저장하고, 재해 복구 훈련 시 실제로 복구 절차를 검증.

## 구현 시 체크리스트
1. 예상 피크 메모리 + 30% 버퍼가 확보되어 있는가?
2. 키 네임스페이스를 모듈/도메인 단위로 분리했는가?
3. 장애 시 복구 목표(RTO/RPO)에 맞는 Persistence 전략을 설정했는가?
4. 느린 명령(예: `ZRANGE` 대용량) 감지 알람이 존재하는가?
5. 샤딩/클러스터 환경에서 멱등성 보장 로직이 준비되어 있는가?

## Redis 심화 개념 및 성능 최적화

실무에서 Redis를 단순 캐시 이상으로 활용하기 위해 반드시 알아야 할 핵심 개념들입니다.

### A. Single Thread & Atomicity (원자성)
Redis는 기본적으로 **싱글 스레드(Single Thread)** 이벤트 루프 모델을 사용합니다.
- **의미**: 한 번에 하나의 명령어만 실행합니다. 즉, 명령어 A가 실행되는 동안 명령어 B는 대기해야 합니다.
- **장점**: 복잡한 동기화(Lock) 없이도 **Race Condition(경쟁 상태)**을 방지할 수 있습니다.
- **주의**: `KEYS`, `FLUSHALL` 같이 오래 걸리는 명령어를 실행하면 전체 서비스가 멈출 수 있습니다(Block).

### B. Lua Script (복합 연산의 원자성 보장)
여러 명령어를 묶어서 실행해야 하는데, 중간에 다른 클라이언트의 명령이 끼어들면 안 될 때 사용합니다.
- **특징**: 스크립트 전체가 하나의 명령어처럼 원자적으로 실행됩니다.
- **용도**: 선착순 쿠폰 발급, 재고 차감 등 정합성이 중요한 로직.

**[예시: 선착순 쿠폰 발급 (중복 방지 + 재고 확인 + 발급)]**
```lua
-- KEYS[1]: 중복 발급 확인용 Set (coupon:issued)
-- KEYS[2]: 재고 카운터 (coupon:stock)
-- ARGV[1]: 사용자 ID

-- 1. 이미 발급받았는지 확인
if redis.call('SISMEMBER', KEYS[1], ARGV[1]) == 1 then
    return "DUPLICATE"
end

-- 2. 재고가 있는지 확인
local stock = tonumber(redis.call('GET', KEYS[2]) or "0")
if stock <= 0 then
    return "SOLD_OUT"
end

-- 3. 재고 감소 및 발급 처리
redis.call('DECR', KEYS[2])
redis.call('SADD', KEYS[1], ARGV[1])
return "SUCCESS"
```
> 이 스크립트는 실행 도중 다른 요청이 끼어들 수 없으므로, 완벽하게 안전합니다.

### C. Pipeline (네트워크 최적화)
여러 명령어를 한 번에 보내고, 응답도 한 번에 받습니다.
- **문제**: 명령어 1개마다 네트워크 왕복 시간(RTT)이 발생합니다. (1000개 명령 = 1000번 RTT)
- **해결**: 파이프라인을 쓰면 1000개 명령을 1번의 RTT로 처리할 수 있어 처리량이 수십 배 증가합니다.
- **용도**: 대량의 랭킹 업데이트, 초기 데이터 적재(Bulk Loading).

### D. Transaction (MULTI / EXEC)
데이터베이스의 트랜잭션과 유사하지만, **Rollback이 안 된다**는 차이가 있습니다.
- `MULTI`: 트랜잭션 시작. 이후 명령어는 큐에 쌓임.
- `EXEC`: 쌓인 명령어 일괄 실행.
- `DISCARD`: 트랜잭션 취소.
- **한계**: `MULTI` 중에 에러가 나도 나머지 명령어는 계속 실행됩니다. 따라서 복잡한 로직은 Lua Script가 더 선호됩니다.

### E. TTL & Eviction (만료 및 메모리 관리)
Redis는 메모리 기반이므로 용량 관리가 필수입니다.
- **TTL (Time To Live)**: `EXPIRE` 명령으로 키의 수명을 설정합니다. (예: 세션 30분, 일간 랭킹 24시간)
- **Eviction Policy (퇴출 정책)**: 메모리가 가득 찼을 때 어떤 키를 지울지 결정합니다.
    - `allkeys-lru`: 모든 키 중에서 가장 오랫동안 안 쓴 것 삭제 (캐시 용도 추천)
    - `volatile-lru`: TTL이 설정된 키 중에서만 삭제
    - `noeviction`: 삭제 안 함 (에러 발생, DB 용도)

## 참고 자료
- Redis 엔터프라이즈 아키텍처 가이드
- antirez 블로그: 이벤트 루프와 I/O thread 디자인
- `redis.io/docs` 의 Data Types, Develop, Operate 섹션
