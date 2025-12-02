# Redis 기반 선착순 쿠폰 발급 리포트

1. **어떤 리포트인지**
   - 선착순 쿠폰 발급을 RDB + 분산락 대신 Redis 자료구조/명령으로 재설계한 실험 및 통합 전략 보고서다.

2. **배경**
   - 현재 `CouponService.issueCoupon`은 레디슨 분산락으로 템플릿 단위 임계영역을 감싸고 RDB의 `incrementIssuedQuantity`를 증가시키는 구조다 (`src/main/kotlin/io/joopang/services/coupon/application/CouponService.kt:26-84`).
   - 락 경합과 `findAllByUserId` 호출이 누적되면 TPS가 급락하고, 락 타임아웃 시 재시도가 필요하다.

3. **목적**
   - 레디스가 제공하는 원자 연산, 정렬된 집합, Lua 스크립트를 이용하여 선착순 요청을 큐잉하고, 중복 발급을 Redis 단계에서 차단함으로써 RDBMS 쓰기 병목을 줄인다.
   - 레디슨 락을 제거하고 쿠폰 템플릿별 초당 수만 건 발급 요청을 처리 가능한 구조를 만든다.

4. **문제해결**
   - **핵심 자료구조**
     | 키 | 타입 | 설명 |
     | --- | --- | --- |
     | `coupon:queue:{templateId}` | Sorted Set | `score=요청도착시각`, `member=userId`. 선착순 순위 계산 및 TTL 관리.
     | `coupon:issued:{templateId}` | Set | 이미 발급된 사용자 집합. `SADD` 실패 시 중복 발급 차단.
     | `coupon:stock:{templateId}` | String | 남은 재고 카운터. `DECR`로 원자 감소.
     | `coupon:issue-stream` | Stream | 발급 성공 이벤트를 내려 보냄 → Spring Batch/Consumer가 RDB `couponRepository.save` 실행.
   - **발급 Lua 스크립트** (원자성 확보)
     ```lua
     local queueKey = KEYS[1]
     local issuedKey = KEYS[2]
     local stockKey = KEYS[3]
     local userId = ARGV[1]
     local score = ARGV[2]
     if redis.call('SISMEMBER', issuedKey, userId) == 1 then
       return {err="DUPLICATE"}
     end
     local stock = tonumber(redis.call('GET', stockKey) or "0")
     if stock <= 0 then
       return {err="SOLD_OUT"}
     end
     redis.call('DECR', stockKey)
     redis.call('SADD', issuedKey, userId)
     redis.call('ZADD', queueKey, score, userId)
     return {ok="QUEUED"}
     ```
   - **처리 흐름**
     1. API 계층에서 위 Lua 스크립트를 실행해 큐입/중복 차단을 한 번에 처리.
     2. `ZPOPMIN coupon:queue:{templateId}`로 순위가 확정된 사용자 묶음을 워커(코루틴/스케줄러)가 주기적으로 가져와 `coupon:issue-stream`에 `XADD`.
     3. 스트림 컨슈머 그룹이 `CouponService` 대신 `couponRepository.save`를 비동기 실행하고, 성공 시 `XACK`. 실패 시 `PEL` 모니터링 + 재처리.
     4. 최종적으로 RDB 내 `issued_quantity`는 배치/프로시저로 동기화하거나, Redis 카운터를 기준으로 주기적 `incrementIssuedQuantity`를 합산.
   - **대기열/우선순위 확장**
     - 좌석 대기열, 웨이팅 등과 동일하게 `score=serverTimeMillis`로 순차 제공 가능.
     - 토큰 버킷처럼 `stock` 키 대신 `coupon:window:{minute}`를 사용하면 속도 제한도 가능.
   - **API 수정 포인트**
     - 기존 락 애노테이션 제거 후 RedisTemplate/Redisson `evalsha` 호출.
     - 발급 완료 콜백은 기존 DTO `IssueCouponOutput`에 Redis 순위, 예상 처리 지연(ms) 필드를 추가하여 사용자 경험을 개선.

5. **테스트**
   - **Lua 단위 테스트**: Embedded Redis + `RedisScript`로 `DUPLICATE`, `SOLD_OUT` 케이스 검증.
   - **동시 부하 테스트**: `k6/scenarios/coupon-issue.js`를 수정해 10k RPS로 ZSET/Lua 처리 TPS 측정, 기존 락 대비 지연 비교.
   - **통합 테스트**: Redis 발급 큐 → Stream 컨슈머 → `couponRepository.save`까지 이어지는 E2E를 `@SpringBootTest` + Testcontainers Redis로 검증.

6. **한계점**
   - Redis 재시작 시 `coupon:queue`/`stock` 복구를 위해 AOF + replica 구성이 필수.
   - 스트림 컨슈머 장애 시 `PEL`이 쌓여 발급 지연이 발생할 수 있으며 운영 모니터링이 필요하다.
   - Redis만으로는 영구 감사 로그를 보장하지 않으므로 최종 진실은 여전히 RDB에 존재해야 한다.

7. **결론**
   - Redis Sorted Set + Set + Lua 조합으로 선착순/중복 방지 요구사항을 분산락 없이 해결할 수 있으며, 실시간 대기열·좌석 같은 로직까지 재사용 가능하다.
   - 기존 `CouponService`는 RDB 확정 저장만 담당하여 단순화되고, 발급 API는 밀리초 단위 응답을 제공한다.

8. **NEXT**
   1. Redis Lua 스크립트 / key schema를 모듈화한 `CouponIssueCoordinator` 작성.
   2. 스트림 컨슈머(Spring Batch or Coroutine) 구현 + 재처리 DLQ 구축.
   3. 운영 대시보드에 `issuedKey` cardinality, `queue` length, Stream lag 메트릭을 추가.
   4. 하루 마감 시 Redis 상태를 RDB와 대조하는 배치 작성으로 데이터 이중화.
