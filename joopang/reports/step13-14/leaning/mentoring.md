# 시니어 엔지니어 멘토링 질문 리스트

## 1. Redis 기반 선착순 쿠폰 발급 (`reports/step13-14/reports/redis-first-come-coupon.md`)
- Lua 스크립트로 `SADD`/`DECR`/`ZADD`를 한 번에 처리하려고 하는데, 분산 환경에서 서버 시간이 서로 다르면 Sorted Set 순서를 어떻게 보정하는 것이 실무적으로 안전한지(예: Redis `TIME` 활용 vs NTP 동기화)?
- `coupon:queue` → Stream → RDB 저장으로 이어질 때 실패/중복 재처리를 어떻게 설계해야 아이템포턴시가 보장되는지, 시니어 분들은 어떤 키 체계나 메시지 메타데이터를 표준으로 삼는지 궁금합니다.
- `coupon:issued:{templateId}` Set의 크기가 수백만 건이 될 때 메모리 사용량을 어떻게 제어하는지, Bloom Filter나 HyperLogLog로 대체해 본 경험이 있는지 조언받고 싶습니다.
- Redis를 1차 데이터 소스로 쓰면서 AOF/Replica 복구 전략을 설계해야 하는데, 실제로 어떤 모니터링 시그널(Replication offset, Stream PEL 등)을 필수로 보고 있는지 궁금합니다.

## 2. Redis 기반 상품 랭킹 (`reports/step13-14/reports/redis-product-ranking.md`)
- Sorted Set을 시간 단위 키로 쪼개려는데, TTL 기반 슬라이딩 윈도우가 요구사항을 못 맞출 경우 `ZREMRANGEBYSCORE` 전략을 어떻게 운영 레벨에서 자동화하는지 경험을 듣고 싶습니다.
- 판매량/매출/뷰 같은 여러 지표를 결합해 하나의 점수를 만들 때, 실무에서는 `ZINTERSTORE` 가중합을 주기적으로 계산하는지, 아니면 Kafka/배치에서 미리 계산해 넣는지 베스트 프랙티스가 뭔지 궁금합니다.
- Redis 랭킹을 주 데이터로 쓸 때 장애 시 DB fallback을 어떻게 감지/전환하는지(Feature flag? Circuit breaker?) 선배님들의 패턴을 알고 싶습니다.

## 3. spec.md 기반 추가 미션 관련 질문 (`reports/step13-14/leaning/spec.md`)
- 선착순 발급 외에 spec에 언급된 대기열 토큰/활성 토큰을 어떤 Redis 자료구조 조합으로 구현하는 게 운영하기 쉬울지 (예: Stream vs Sorted Set vs List) 논의하고 싶습니다.
- 동시성 이슈에 대해 낙관/비관/분산락 순으로 검토하라고 되어 있는데, 실제로는 어떤 지표나 증상을 기준으로 락 전략을 교체하는지 사례가 궁금합니다.
- Redis를 보조 데이터 소스로 사용할 때 RDB와의 정합성을 주기적으로 검증하는 자동화 패턴(Checksum, CDC 재처리 등)을 어떤 주기로 두는지 조언을 듣고 싶습니다.

## 4. 테스트 & 운영 관점
- `k6` 부하 테스트 시 Redis 기반 로직(쿠폰 또는 랭킹)이 병목인지 RDB인지 구분하기 위해 시니어 분들은 어떤 프로파일링/트레이싱 도구를 우선 적용하는지?
- Redis Stream 컨슈머 그룹을 운영할 때 PEL이 쌓이는 상황을 어떻게 모니터링/자동 복구하는지(Dead Letter Queue, 재시작 정책 등) 실무 팁이 있으면 듣고 싶습니다.

## 5. Next Step 관련
- 두 리포트 모두 NEXT 항목에 배치/모니터링 작업이 있는데, 이런 infra 작업을 도메인 팀에서 직접 소유할지, 플랫폼 팀과 협업할지에 대한 조직적 기준이나 경험이 궁금합니다.
