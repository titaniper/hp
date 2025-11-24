아래 답변의 예

## **1. "좋아요 수를 실시간으로 증가시키는 기능을 대규모 트래픽에서 어떻게 구현할 건가?"**

- Race condition
- counter inconsistency
- Redis vs DB
- eventual consistency
- sharding & batching
    
    → 실전 고민 많이 드러남
    

---

## **2. "주문 버튼을 여러 번 눌러도 중복 주문이 되면 안 될 때 어떻게 방지할까?"**

- 분산락
- idempotency key
- DB unique key
- 메시지 큐 중복 처리
    
    → 커머스 실무에서 매우 빈번
    

---

## **3. "한정 수량 타임딜에서 재고 정확하게 차감하려면 어떻게 구현해야 해?"**

- Redis 원자 연산
- Lua script
- Redisson lock
- DB FOR UPDATE vs Outbox pattern
    
    → 모든 면접관이 매우 좋아하는 주제
    

---

## **4. "검색 자동완성 기능을 구현해야 하면 어떤 구조로 만들래?"**

- Trie vs Redis ZSET
- 초성 검색
- prefix match
- 캐시 warming
- 실시간 반영 전략
    
    → 실전과 이론 결합
    

---

## **5. "QR 체크인/현장 체크인을 ‘중복 없이’ 빠르게 처리하려면 어떻게 해야 해?"**

- concurrency control
- atomically check & register
- TTL
- Redis bitmap 가능성
    
    → 고도화 표현 가능
    

---

## **6. "여러 서버에서 동시에 스케줄러가 돌면 작업이 중복 실행될 수 있는데 어떻게 막아?"**

- distributed scheduler
- leader election
- Redis lock
- MYSQL SELECT FOR UPDATE
    
    → 운영 경험 드러나는 질문
    

---

## **7. "장바구니에 담은 상품이 가격 변동되면 어떤 시점에 가격을 다시 반영해야 할까?"**

- 장바구니 가격 vs 실시간 가격
- checkout 시점 검증
- stale cache 문제
- UX와 백엔드 정책 조율
    
    → 실무 정책 고민 포함 (면접관 좋아함)
    

---

## **8. "사용자의 마지막 활동 시간을 저장해야 하는데, 트래픽이 매우 많으면 어떻게 할까?"**

- write amplification
- batch update
- debounce + pipeline
- Redis sorted set
- DB 부하분산
    
    → 읽기/쓰기 아키텍처 감각 평가
    

---

## **9. "주문 상태 변경을 이벤트 기반으로 처리하고 싶을 때, 메시지 중복 / 순서 보장 문제는 어떻게 해결할래?"**

- Kafka exactly-once 불가 → idempotent consumer
- outbox pattern
- retry & DLT
- 이벤트 재생 문제
    
    → 이벤트 기반 아키텍처 이해도 평가
    

---

## **10. "API 요청이 갑자기 100배 증가하는 상황에서 우선적으로 할 수 있는 방어 전략은?"**

- Circuit breaker
- rate limit
- bulkhead
- delayed queue
- degrade mode
    
    → 운영 감각 절대적으로 필요
    

## **11. “로그인 시 비밀번호 5회 오류 → 계정 잠금 기능 어떻게 구현할까?”**

고려 요소:

- race condition
- distributed counter
- TTL + sliding window
- Redis atomic ops

---

## **12. “OTP(일회성 코드) 발급 서비스가 수천 TPS 나오면 어떤 저장 구조를 선택할래?”**

고려 요소:

- Redis TTL
- collision probability
- atomicity
- rate limit
- 메모리 비용 vs 안전성

---

## **13. “상품 리뷰를 정렬 기준(최신/추천/포토)별로 빠르게 제공하려면 어떤 구조가 필요할까?”**

고려 요소:

- denormalization
- 별도 인덱싱 캐시
- ElasticSearch
- pagination 최적화

---

## **14. “유저가 팔로우한 사람들의 새 게시물을 빠르게 보여주려면 어떤 설계가 맞을까?”**

(News Feed Fan-out/Fan-in 설계 문제)

고려 요소:

- push vs pull
- sharding keys
- feed aggregator
- Redis timeline
- eventual consistency

---

## **15. “쿠폰 발급 1만개인데 동시에 20만명이 요청하면 어떻게 처리할래?”**

고려 요소:

- atomic decrement
- 분산락
- DB unique key
- queueing
- oversell 방지

---

## **16. “API 응답이 2초 걸리는 외부 API를 100 TPS로 호출해야 할 때 어떻게 안정화할까?”**

고려 요소:

- connection pool
- bulk request
- local cache
- circuit breaker
- fallback 전략

---

## **17. “사용자가 탈퇴했을 때 데이터 삭제 vs 비식별화는 어떻게 처리할래?”**

고려 요소:

- 실제 삭제 위험성
- transactional boundary
- FK constraints
- soft delete vs anonymization
- GDPR/Korean PIPA

---

## **18. “주문 상태가 ‘결제 완료 → 상품 준비 → 배송 중 → 배송 완료’ 순으로만 흐르도록 강제하려면 어떻게 할까?”**

고려 요소:

- state machine
- invalid transition 방지
- DB constraints
- 메시지 재처리 대비
- 멱등성 처리

---

## **19. “리스트 API에서 페이징 페이지 번호가 매우 크면 성능 문제가 생기는데 어떻게 해결할까?”**

고려 요소:

- offset pagination 문제
- cursor-based pagination
- index 최적화
- ElasticSearch Scroll/Point in Time

---

## **20. “사용자 알림(푸시/이메일)을 대량으로 빠져나가야 할 때 어떤 큐/구조를 사용해 처리할래?”**

고려 요소:

- Kafka or SQS
- consumer scaling
- retry / dead letter
- exactly-once impossible → idempotent handler
- 구성 API → 비동기

## **1. 좋아요 / 찜수 / 좋아요 상품 표시 로직 (고트래픽 기준)**

> 질문:“하루 수십만~수백만 요청이 들어오는 서비스에서,‘좋아요 수’와 ‘내가 좋아요 누른 상품인지 여부’를어떻게 저장·조회·캐시할 건지 설계해보세요.”
> 
- 보고 싶은 포인트:
    - 카운터 분산 처리 (Redis, DB, CQRS)
    - 실시간성 vs 일관성 트레이드오프
    - 유저별 마킹을 어디에 저장할지(캐시/DB/검색엔진)

---

## **2. 인기 상품 / 랭킹(조회·구매·좋아요 기반) 설계**

> 질문:“조회수/주문수/좋아요수 등을 기준으로 실시간 인기 상품 랭킹을 만들어메인에 노출하려고 합니다. 트래픽이 매우 큰 상황에서 어떤 구조로 설계하시겠어요?”
> 
- 보고 싶은 포인트:
    - 실시간 집계 vs 배치 집계 vs 스트리밍(Kafka, Flink 등)
    - Redis Sorted Set, ES Aggregation, ClickHouse 같은 OLAP 활용
    - Hot key, 랭킹 캐시 갱신 주기 설계

---

## **3. 이벤트 오픈 순간 ‘폭발 트래픽’ 방어 (타임딜/오픈런)**

> 질문:“오전 10시에 딱 열리는 타임딜에 동시 10만 명이 들어와 상품 상세/구매 API를 호출합니다.서버와 DB, 캐시가 버티도록 어떤 식으로 방어 전략을 세울 건가요?”
> 
- 보고 싶은 포인트:
    - 캐시 레이어 설계(사전 warm-up, read-only 페이지)
    - Rate limiting, Queueing, 대기열 페이지
    - DB write 스파이크 흡수 전략

---

## **4. Cache Stampede / Thundering Herd 방지**

> 질문:“대용량 트래픽 상황에서 인기 상품 상세 페이지 캐시가 만료될 때수많은 요청이 동시에 DB를 치는 캐시 스탬피드를 어떻게 막을 수 있을까요?”
> 
- 보고 싶은 포인트:
    - Mutex lock, single flight, dog-pile 방지 전략
    - 조기 갱신(early refresh), TTL+Jitter
    - stale-while-revalidate 패턴

---

## **5. 유저별 Personalized 데이터(장바구니/추천) 고트래픽 처리**

> 질문:“유저별 장바구니/추천 상품처럼 개인화 된 데이터를수십만 동시 접속 상황에서 빠르게 제공하려면 어떤 저장·캐시 전략을 쓰시겠어요?”
> 
- 보고 싶은 포인트:
    - user-based key 설계, Redis hash/list/set
    - session sticky vs stateless + Redis
    - 개인화 데이터의 만료/동기화 전략

---

## **6. Read/Write 분리와 일관성 이슈**

> 질문:“DB read/write 분리를 해서 read replica를 여러 개 두려 합니다.대용량 트래픽에서 이 구조를 쓸 때 어떤 문제들이 생기고, 어떻게 대응하시겠어요?”
> 
- 보고 싶은 포인트:
    - replica lag로 인한 stale read
    - ‘방금 쓴 데이터가 안 보이는 문제’ 처리 (read-your-write)
    - 특정 요청만 master로 보내는 전략, session stickiness, CQRS 등

---

## **7. 트래킹 로그 / 이벤트 데이터 수집 아키텍처**

> 질문:“페이지뷰, 클릭, 좋아요, 장바구니 담기 같은 이벤트가초당 수만 건 발생합니다. 이걸 수집하고 나중에 분석·추천에활용하고 싶을 때, 어떤 파이프라인으로 구성할 건가요?”
> 
- 보고 싶은 포인트:
    - Kafka, Kinesis, Pulsar 등 메시지 브로커
    - 로그 수집기(Fluentbit, Filebeat, Logstash)
    - OLAP/데이터레이크(ClickHouse, BigQuery, S3+Parquet 등)

---

## **8. 외부 API 연동이 병목인 서비스에서의 고트래픽 처리**

> 질문:“결제사/배송사/지도 API처럼 응답이 느린 외부 API를대용량 트래픽에서 연동해야 할 때, 시스템이 무너지지 않게어떤 보호 장치를 설계하시겠습니까?”
> 
- 보고 싶은 포인트:
    - Circuit Breaker, Bulkhead, Timeout, Retry with backoff
    - Async 처리 & Queue, fallback (임시 데이터/캐시)
    - rate limit, connection pool 관리

---

## **9. 대용량 배치 작업과 실시간 트래픽 공존**

> 질문:“밤마다 수백만 건 단위의 배치 작업(정산, 통계 집계 등)을 돌리면서도낮에는 실시간 트래픽이 많은 서비스입니다. 배치 때문에 실시간 API가 느려지지 않게어떤 식으로 설계/분리하시겠어요?”
> 
- 보고 싶은 포인트:
    - 배치 DB와 서비스 DB 분리, 리소스 격리
    - 롤링 배치, 우선순위/쿼터 설정
    - 락/인덱스/쿼리로 인한 경합 줄이기

---

## **10. 고트래픽 상태에서의 배포 전략 (무중단·안전 배포)**

> 질문:“항상 트래픽이 높은 서비스에서 새로운 버전을 배포할 때,장애나 대량 에러 없이 롤아웃/롤백하려면 어떤 전략을 사용하시겠습니까?”
> 
- 보고 싶은 포인트:
    - Blue-Green / Canary / Progressive Delivery
    - 트래픽 분산, Health check, readinessProbe/livenessProbe
    - DB 마이그레이션 전략(Backward compatible schema)

---

## **21. "작은 스타트업에서 동시성 제어가 필요할 때 Redis 분산락 vs DB 비관적락 중 어떤 걸 선택할까?"**

고려 요소:

- Redis 분산락의 비용 vs DB 비관적락의 부하
- DB 트랜잭션 락으로 인한 커넥션 풀 고갈
- 스케일 아웃 관점에서의 차이 (RDB는 마스터-리더 구조 제약)
- 오토 스케일링 환경에서의 적합성
- 리소스 비용과 운영 복잡도 트레이드오프

---

## **22. "필터가 많은 상품 리스트 페이지(PLP)와 상품 상세 페이지(PDP)에서 캐시 전략을 어떻게 다르게 설계할까?"**

고려 요소:

- PLP: 필터 조합이 많아 캐시 히트율 저하 → 알고리즘 기반 추천으로 캐시 적용 어려움
- PDP: 한 번 조회 후 재조회 확률 높음 → 캐시 적용 유리
- 첫 페이지 캐시 전략
- 캐시 직접 수정 vs 무효화 전략
- 실시간성 요구사항에 따른 TTL 설계

---

## **23. "분산락 내부에서 원자성과 정합성을 보장하려면 어떤 패턴을 사용해야 할까?"**

고려 요소:

- 분산락 획득 → 비즈니스 로직 실행 → DB 커밋의 원자성 보장
- Kafka 트랜잭션을 통한 분산 트랜잭션 처리
- Outbox pattern 활용
- 실패 시 롤백 전략
- 분산 환경에서의 일관성 보장 방법

---

## **24. "CUD(Create/Update/Delete) 작업이 매우 빈번한 서비스에서 캐시 무효화 전략은 어떻게 설계할까?"**

고려 요소:

- 캐시 무효화 빈도가 높을 경우 캐시 효과 저하
- TTL 기반 만료 전략 vs 명시적 무효화
- 캐시 운영 비용 vs 성능 향상 효과
- Write-through vs Write-behind 패턴
- 캐시 무효화로 인한 추가 부하 고려

---

## **25. "분산락의 한계와 대안: 정확한 순차 보장이 필요한 경우 어떻게 처리할까?"**

고려 요소:

- 분산락은 완전한 순차 보장 불가
- DB 비관적락의 순차 보장 vs 성능 저하
- 메시지 큐를 통한 순차 처리
- 단일 컨슈머 패턴
- 순차성 요구사항의 실제 필요성 재검토

---

## **26. "'지금 이 상품을 n명이 보고 있어요' 기능을 대규모 트래픽에서 어떻게 구현할까?"**

고려 요소:

- 실시간 조회수 집계 (조회수, 구매수, 찜수와 연관)
- 이벤트 기반 처리 (비동기 집계)
- Redis를 활용한 실시간 카운터
- 세션 기반 중복 제거
- TTL을 통한 자동 정리
- 데이터 완성도와 실시간성의 트레이드오프

    