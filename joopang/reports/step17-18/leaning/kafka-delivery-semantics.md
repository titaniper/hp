# 카프카 전달 보장 (Delivery Semantics)

---

## 개요

카프카에서 메시지 전달 보장은 **데이터 손실**과 **중복 처리** 사이의 트레이드오프다.

```
┌──────────────────────────────────────────────────────────────────────┐
│                    전달 보장 스펙트럼                                  │
├──────────────────────────────────────────────────────────────────────┤
│                                                                       │
│    손실 가능                                           중복 가능       │
│    ◄────────────────────────────────────────────────────────────►    │
│                                                                       │
│    ┌─────────────┐      ┌─────────────┐      ┌─────────────┐         │
│    │ At-most-    │      │ Exactly-    │      │ At-least-   │         │
│    │ once        │      │ once        │      │ once        │         │
│    │             │      │             │      │             │         │
│    │  손실: ✓    │      │  손실: ✗    │      │  손실: ✗    │         │
│    │  중복: ✗    │      │  중복: ✗    │      │  중복: ✓    │         │
│    └─────────────┘      └─────────────┘      └─────────────┘         │
│                                                                       │
│         간단                  복잡                 중간               │
│                                                                       │
└──────────────────────────────────────────────────────────────────────┘
```

---

## At-most-once (최대 한 번)

### 설명

메시지를 최대 한 번만 전달하며, 실패 시 손실 가능. 컨슈머가 데이터를 읽고 처리하기 전 오프셋을 커밋한다.

```
┌──────────────────────────────────────────────────────────────────────┐
│                    At-most-once 동작 흐름                             │
├──────────────────────────────────────────────────────────────────────┤
│                                                                       │
│  [정상 케이스]                                                         │
│                                                                       │
│    Consumer                 Kafka                                    │
│       │                       │                                       │
│       │ ① poll() 메시지 가져오기 │                                      │
│       │◄──────────────────────│  offset: 100                         │
│       │                       │                                       │
│       │ ② commit offset 101   │  ← 처리 전에 먼저 커밋!               │
│       │──────────────────────►│                                       │
│       │                       │                                       │
│       │ ③ process message     │                                       │
│       │      ✅ 성공          │                                       │
│       │                       │                                       │
│                                                                       │
│  [실패 케이스 - 메시지 손실]                                           │
│                                                                       │
│    Consumer                 Kafka                                    │
│       │                       │                                       │
│       │ ① poll() 메시지 가져오기 │                                      │
│       │◄──────────────────────│  offset: 100                         │
│       │                       │                                       │
│       │ ② commit offset 101   │  ← 먼저 커밋                          │
│       │──────────────────────►│                                       │
│       │                       │                                       │
│       │ ③ process message     │                                       │
│       │      💥 실패/크래시!   │                                       │
│       │                       │                                       │
│       │ ④ 재시작 후 poll()    │                                       │
│       │◄──────────────────────│  offset: 101 (100은 건너뜀!)         │
│       │                       │                                       │
│       │      ❌ 메시지 100 손실│                                       │
│                                                                       │
└──────────────────────────────────────────────────────────────────────┘
```

### 장점
- 구현이 단순하고 지연이 최소화됨
- 중복 처리가 발생하지 않음
- 처리량이 가장 높음

### 단점
- 메시지 손실 위험이 있어 중요 데이터에는 부적합
- 장애 발생 시 데이터 복구 불가

### 사용 사례

```
┌──────────────────────────────────────────────────────────────────────┐
│                 At-most-once 적합한 사용 사례                          │
├──────────────────────────────────────────────────────────────────────┤
│                                                                       │
│  ✅ 적합한 경우                                                        │
│  ─────────────────                                                    │
│  • 실시간 메트릭/로그 수집 (일부 손실 허용)                             │
│  • 캐시 갱신 이벤트                                                    │
│  • 실시간 대시보드 업데이트                                            │
│  • IoT 센서 데이터 (고빈도, 일부 손실 OK)                               │
│                                                                       │
│  예시: 웹사이트 방문자 추적                                            │
│  ┌────────────────────────────────────────────────────────────┐      │
│  │  방문자 100만 명 중 100명 데이터 손실                        │      │
│  │  → 0.01% 오차, 분석에 거의 영향 없음                         │      │
│  │  → 단순한 구현으로 높은 처리량 달성                           │      │
│  └────────────────────────────────────────────────────────────┘      │
│                                                                       │
│  ❌ 부적합한 경우                                                      │
│  ─────────────────                                                    │
│  • 금융 거래                                                          │
│  • 주문 처리                                                          │
│  • 재고 관리                                                          │
│  • 법적 감사 로그                                                      │
│                                                                       │
└──────────────────────────────────────────────────────────────────────┘
```

### 설정 예시

```java
// Consumer 설정 - At-most-once
Properties props = new Properties();
props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "100"); // 빠른 자동 커밋

// 또는 수동으로
consumer.poll(Duration.ofMillis(100));
consumer.commitSync();  // 먼저 커밋
processRecords(records); // 나중에 처리
```

---

## At-least-once (최소 한 번)

### 설명

최소 한 번 전달을 보장한다. 컨슈머는 처리가 끝난 뒤 오프셋을 커밋하거나, 프로듀서는 acks=all로 브로커 확인을 받는다.

```
┌──────────────────────────────────────────────────────────────────────┐
│                    At-least-once 동작 흐름                            │
├──────────────────────────────────────────────────────────────────────┤
│                                                                       │
│  [정상 케이스]                                                         │
│                                                                       │
│    Consumer                 Kafka                                    │
│       │                       │                                       │
│       │ ① poll() 메시지 가져오기 │                                      │
│       │◄──────────────────────│  offset: 100                         │
│       │                       │                                       │
│       │ ② process message     │                                       │
│       │      ✅ 성공          │                                       │
│       │                       │                                       │
│       │ ③ commit offset 101   │  ← 처리 후에 커밋!                    │
│       │──────────────────────►│                                       │
│       │                       │                                       │
│                                                                       │
│  [실패 케이스 - 메시지 중복]                                           │
│                                                                       │
│    Consumer                 Kafka                                    │
│       │                       │                                       │
│       │ ① poll() 메시지 가져오기 │                                      │
│       │◄──────────────────────│  offset: 100                         │
│       │                       │                                       │
│       │ ② process message     │                                       │
│       │      ✅ 성공          │                                       │
│       │                       │                                       │
│       │ ③ commit offset 101   │                                       │
│       │──────── 💥 ──────────►│  커밋 실패/크래시!                    │
│       │                       │                                       │
│       │ ④ 재시작 후 poll()    │                                       │
│       │◄──────────────────────│  offset: 100 (다시 전달!)             │
│       │                       │                                       │
│       │ ⑤ process message     │  ← 같은 메시지 재처리 (중복!)          │
│       │      ⚠️ 중복 처리     │                                       │
│                                                                       │
└──────────────────────────────────────────────────────────────────────┘
```

### Producer 측 At-least-once

```
┌──────────────────────────────────────────────────────────────────────┐
│                    Producer acks 설정별 동작                          │
├──────────────────────────────────────────────────────────────────────┤
│                                                                       │
│  [acks=0] - Fire and Forget (At-most-once)                           │
│  ───────────────────────────────────────                              │
│                                                                       │
│    Producer              Leader           Follower                   │
│       │                    │                  │                       │
│       │──── message ──────►│                  │                       │
│       │    (ACK 안기다림)   │                  │                       │
│       │                    │                  │                       │
│       └── 바로 다음 전송 ──►│                  │                       │
│                                                                       │
│    ⚠️ 브로커 장애 시 메시지 손실 가능                                  │
│                                                                       │
├──────────────────────────────────────────────────────────────────────┤
│                                                                       │
│  [acks=1] - Leader ACK                                               │
│  ──────────────────────                                               │
│                                                                       │
│    Producer              Leader           Follower                   │
│       │                    │                  │                       │
│       │──── message ──────►│                  │                       │
│       │                    │── replicate ───►│                       │
│       │◄─────── ACK ───────│                  │                       │
│       │                    │                  │                       │
│                                                                       │
│    ⚠️ 리더 장애 시 복제 전 데이터 손실 가능                            │
│                                                                       │
├──────────────────────────────────────────────────────────────────────┤
│                                                                       │
│  [acks=all (-1)] - 모든 ISR ACK (At-least-once)                       │
│  ─────────────────────────────────────────────                        │
│                                                                       │
│    Producer              Leader           Follower                   │
│       │                    │                  │                       │
│       │──── message ──────►│                  │                       │
│       │                    │── replicate ───►│                       │
│       │                    │◄──── ACK ───────│                       │
│       │◄─────── ACK ───────│                  │                       │
│       │                    │                  │                       │
│                                                                       │
│    ✅ min.insync.replicas=2와 함께 사용 시 데이터 손실 방지            │
│                                                                       │
└──────────────────────────────────────────────────────────────────────┘
```

### 장점
- 데이터 손실 없이 내구성을 확보
- 구현이 비교적 단순
- 대부분의 사용 사례에 적합

### 단점
- 오류나 재시도 시 중복 처리가 발생할 수 있음
- Idempotent 처리가 필요함

### 중복 처리 해결 패턴

```
┌──────────────────────────────────────────────────────────────────────┐
│                    중복 처리 해결 패턴                                 │
├──────────────────────────────────────────────────────────────────────┤
│                                                                       │
│  [1. Idempotent Consumer 패턴]                                        │
│  ─────────────────────────────                                        │
│                                                                       │
│    메시지에 고유 ID 포함 → 처리 전 중복 체크                            │
│                                                                       │
│    ┌──────────────┐    ┌──────────────┐    ┌──────────────┐          │
│    │   Message    │    │   Consumer   │    │   Database   │          │
│    │  id: "abc123"│───►│  중복 체크    │───►│ processed_ids│          │
│    └──────────────┘    └──────┬───────┘    └──────────────┘          │
│                               │                                       │
│                    ┌──────────┴──────────┐                           │
│                    │                     │                           │
│               중복 아님              이미 처리됨                       │
│                    │                     │                           │
│                    ▼                     ▼                           │
│               처리 수행               Skip (무시)                      │
│                                                                       │
│    // Java 예시                                                       │
│    if (!processedIds.contains(message.id)) {                         │
│        processMessage(message);                                       │
│        processedIds.add(message.id);                                 │
│    }                                                                  │
│                                                                       │
├──────────────────────────────────────────────────────────────────────┤
│                                                                       │
│  [2. Upsert 패턴]                                                     │
│  ────────────────                                                     │
│                                                                       │
│    같은 키로 여러 번 저장해도 결과가 동일                               │
│                                                                       │
│    -- SQL 예시                                                        │
│    INSERT INTO orders (order_id, status, amount)                     │
│    VALUES ('ord-123', 'COMPLETED', 50000)                            │
│    ON DUPLICATE KEY UPDATE                                           │
│        status = VALUES(status),                                       │
│        amount = VALUES(amount);                                       │
│                                                                       │
│    -- 또는 PostgreSQL                                                 │
│    INSERT INTO orders (order_id, status, amount)                     │
│    VALUES ('ord-123', 'COMPLETED', 50000)                            │
│    ON CONFLICT (order_id) DO UPDATE SET                              │
│        status = EXCLUDED.status,                                      │
│        amount = EXCLUDED.amount;                                      │
│                                                                       │
├──────────────────────────────────────────────────────────────────────┤
│                                                                       │
│  [3. Deduplication Window 패턴]                                       │
│  ──────────────────────────────                                       │
│                                                                       │
│    최근 N분/시간 내의 메시지 ID를 캐시에 보관                           │
│                                                                       │
│    ┌─────────────────────────────────────────────────┐               │
│    │                 Redis                           │               │
│    │  ┌───────────────────────────────────────────┐ │               │
│    │  │  processed:abc123  →  1  (TTL: 1 hour)    │ │               │
│    │  │  processed:def456  →  1  (TTL: 1 hour)    │ │               │
│    │  │  processed:ghi789  →  1  (TTL: 1 hour)    │ │               │
│    │  └───────────────────────────────────────────┘ │               │
│    └─────────────────────────────────────────────────┘               │
│                                                                       │
│    // Java + Redis 예시                                               │
│    String key = "processed:" + message.getId();                      │
│    Boolean isNew = redis.setIfAbsent(key, "1", Duration.ofHours(1)); │
│    if (Boolean.TRUE.equals(isNew)) {                                 │
│        processMessage(message);                                       │
│    }                                                                  │
│                                                                       │
└──────────────────────────────────────────────────────────────────────┘
```

### 설정 예시

```java
// Producer 설정 - At-least-once
Properties producerProps = new Properties();
producerProps.put(ProducerConfig.ACKS_CONFIG, "all");
producerProps.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
producerProps.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);

// Consumer 설정 - At-least-once
Properties consumerProps = new Properties();
consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");  // 수동 커밋

// 처리 로직
while (true) {
    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
    for (ConsumerRecord<String, String> record : records) {
        processRecord(record);  // 먼저 처리
    }
    consumer.commitSync();      // 처리 후 커밋
}
```

---

## Exactly-once (정확히 한 번)

### 설명

중복 없이 정확히 한 번 처리를 보장한다. 카프카는 프로듀서 멱등성 + 트랜잭션 + 컨슈머 read-process-write를 조합해 구현한다.

```
┌──────────────────────────────────────────────────────────────────────┐
│                    Exactly-once 구현 3요소                            │
├──────────────────────────────────────────────────────────────────────┤
│                                                                       │
│  ┌─────────────────────────────────────────────────────────────────┐ │
│  │                                                                 │ │
│  │     ┌──────────────────┐                                       │ │
│  │     │  1. Idempotent   │  Producer 재시도 시 중복 방지          │ │
│  │     │     Producer     │  (PID + Sequence Number)              │ │
│  │     └────────┬─────────┘                                       │ │
│  │              │                                                 │ │
│  │              ▼                                                 │ │
│  │     ┌──────────────────┐                                       │ │
│  │     │  2. Transaction  │  여러 파티션에 원자적 쓰기             │ │
│  │     │     (Atomic)     │  (All or Nothing)                    │ │
│  │     └────────┬─────────┘                                       │ │
│  │              │                                                 │ │
│  │              ▼                                                 │ │
│  │     ┌──────────────────┐                                       │ │
│  │     │  3. Consumer     │  커밋된 메시지만 읽기                  │ │
│  │     │  read_committed  │  (isolation.level)                   │ │
│  │     └──────────────────┘                                       │ │
│  │                                                                 │ │
│  └─────────────────────────────────────────────────────────────────┘ │
│                                                                       │
│  💡 세 가지가 모두 갖춰져야 End-to-End Exactly-once 달성              │
│                                                                       │
└──────────────────────────────────────────────────────────────────────┘
```

### 1. Idempotent Producer (멱등성 프로듀서)

```
┌──────────────────────────────────────────────────────────────────────┐
│                    Idempotent Producer 동작 원리                      │
├──────────────────────────────────────────────────────────────────────┤
│                                                                       │
│  [문제 상황: 네트워크 오류로 재전송]                                    │
│                                                                       │
│    Producer                        Broker                            │
│       │                              │                                │
│       │ ① send(msg-A)               │                                │
│       │─────────────────────────────►│  저장 완료                     │
│       │                              │                                │
│       │    💥 ACK 유실               │                                │
│       │         X◄──────────────────│                                │
│       │                              │                                │
│       │ ② retry send(msg-A)         │                                │
│       │─────────────────────────────►│  중복 저장?                    │
│       │                              │                                │
│                                                                       │
│  [해결: PID + Sequence Number]                                        │
│  ─────────────────────────────                                        │
│                                                                       │
│    Producer (PID: 42)              Broker                            │
│       │                              │                                │
│       │ ① send(msg-A, seq=0)        │                                │
│       │─────────────────────────────►│  저장: PID=42, seq=0          │
│       │                              │                                │
│       │    💥 ACK 유실               │                                │
│       │         X◄──────────────────│                                │
│       │                              │                                │
│       │ ② retry send(msg-A, seq=0)  │                                │
│       │─────────────────────────────►│  seq=0 이미 있음              │
│       │                              │  → 무시 (중복 방지!)           │
│       │◄─────────────────────────────│  ACK 반환                     │
│       │                              │                                │
│                                                                       │
│  ┌────────────────────────────────────────────────────────────┐      │
│  │  Broker 내부 상태                                          │      │
│  │  ┌───────────────────────────────────────────────────────┐│      │
│  │  │  PID: 42                                              ││      │
│  │  │  ├── Partition 0: last_seq = 3                        ││      │
│  │  │  ├── Partition 1: last_seq = 7                        ││      │
│  │  │  └── Partition 2: last_seq = 1                        ││      │
│  │  └───────────────────────────────────────────────────────┘│      │
│  │                                                            │      │
│  │  새 메시지의 seq가 last_seq + 1이 아니면:                   │      │
│  │  • seq <= last_seq → 중복, 무시                            │      │
│  │  • seq > last_seq + 1 → 순서 오류, 예외                    │      │
│  └────────────────────────────────────────────────────────────┘      │
│                                                                       │
└──────────────────────────────────────────────────────────────────────┘
```

### 2. Transactional Producer (트랜잭션 프로듀서)

```
┌──────────────────────────────────────────────────────────────────────┐
│                    Kafka Transaction 흐름                             │
├──────────────────────────────────────────────────────────────────────┤
│                                                                       │
│  [Consume-Transform-Produce 패턴]                                     │
│                                                                       │
│   Input Topic        Application         Output Topic                │
│   ┌─────────┐       ┌───────────┐       ┌─────────┐                  │
│   │orders   │──────►│ Transform │──────►│invoices │                  │
│   │         │       │           │       │         │                  │
│   │         │       │           │──────►│analytics│                  │
│   └─────────┘       └───────────┘       └─────────┘                  │
│                                                                       │
│   이 모든 작업이 원자적(Atomic)으로 처리되어야 함                       │
│   → 일부만 성공하면 데이터 불일치 발생!                                 │
│                                                                       │
├──────────────────────────────────────────────────────────────────────┤
│                                                                       │
│  [Transaction 흐름 상세]                                               │
│                                                                       │
│    Producer                    Transaction         Broker            │
│                                Coordinator                           │
│       │                            │                   │              │
│       │ ① initTransactions()      │                   │              │
│       │───────────────────────────►│                   │              │
│       │                            │  PID 할당         │              │
│       │◄───────────────────────────│                   │              │
│       │                            │                   │              │
│       │ ② beginTransaction()      │                   │              │
│       │───────────────────────────►│                   │              │
│       │                            │                   │              │
│       │ ③ send(topic-A, msg1)     │                   │              │
│       │──────────────────────────────────────────────►│              │
│       │                            │                   │  ← 미커밋   │
│       │ ④ send(topic-B, msg2)     │                   │              │
│       │──────────────────────────────────────────────►│              │
│       │                            │                   │  ← 미커밋   │
│       │                            │                   │              │
│       │ ⑤ sendOffsetsToTransaction(offsets, groupId)  │              │
│       │───────────────────────────►│                   │              │
│       │                            │  오프셋도 트랜잭션에 포함         │
│       │                            │                   │              │
│       │ ⑥ commitTransaction()     │                   │              │
│       │───────────────────────────►│                   │              │
│       │                            │  COMMIT 마커 기록 │              │
│       │                            │─────────────────►│              │
│       │                            │                   │  ← 전체 커밋│
│       │◄───────────────────────────│                   │              │
│       │                            │                   │              │
│                                                                       │
│  [실패 시: abortTransaction()]                                        │
│  → 모든 메시지에 ABORT 마커 → Consumer가 무시                          │
│                                                                       │
└──────────────────────────────────────────────────────────────────────┘
```

### 3. Consumer read_committed

```
┌──────────────────────────────────────────────────────────────────────┐
│                    isolation.level 설정 비교                          │
├──────────────────────────────────────────────────────────────────────┤
│                                                                       │
│  [read_uncommitted] (기본값)                                          │
│  ────────────────────────────                                         │
│                                                                       │
│    Partition Log:                                                     │
│    ┌────┬────┬────┬────┬────┬────┬────┬────┐                         │
│    │ m1 │ m2 │ m3 │ m4 │ m5 │ m6 │ m7 │ m8 │                         │
│    │ ✓  │ ✓  │TXN │TXN │ ✓  │TXN │TXN │ .. │                         │
│    └────┴────┴────┴────┴────┴────┴────┴────┘                         │
│                          ▲                                           │
│                          │                                           │
│                    진행 중인 트랜잭션                                   │
│                                                                       │
│    Consumer: m1, m2, m3, m4, m5, m6, m7 모두 읽음                     │
│    → 커밋 안 된 메시지도 읽음 (일관성 X)                                │
│                                                                       │
├──────────────────────────────────────────────────────────────────────┤
│                                                                       │
│  [read_committed] (Exactly-once 필수)                                 │
│  ────────────────────────────────────                                 │
│                                                                       │
│    Partition Log:                                                     │
│    ┌────┬────┬────┬────┬────┬────┬────┬────┐                         │
│    │ m1 │ m2 │ m3 │ m4 │ m5 │ m6 │ m7 │ m8 │                         │
│    │ ✓  │ ✓  │TXN │TXN │ ✓  │TXN │TXN │ .. │                         │
│    └────┴────┴────┴────┴────┴────┴────┴────┘                         │
│              │                                                        │
│              │◄──── LSO (Last Stable Offset)                         │
│              │      커밋된 마지막 오프셋                               │
│                                                                       │
│    Consumer: m1, m2 만 읽음                                           │
│    → 트랜잭션 커밋될 때까지 대기                                       │
│    → 커밋되면 m3, m4 읽기 가능                                         │
│    → ABORT되면 m3, m4 건너뜀                                          │
│                                                                       │
└──────────────────────────────────────────────────────────────────────┘
```

### 필수 조건 및 설정

```
┌──────────────────────────────────────────────────────────────────────┐
│                    Exactly-once 필수 설정                             │
├──────────────────────────────────────────────────────────────────────┤
│                                                                       │
│  [Producer 설정]                                                      │
│  ────────────────                                                     │
│                                                                       │
│  enable.idempotence=true          # 멱등성 활성화                     │
│  transactional.id=my-txn-id-001   # 트랜잭션 ID (앱 재시작 시 동일)   │
│  acks=all                         # 자동 설정됨                       │
│  retries=Integer.MAX_VALUE        # 자동 설정됨                       │
│  max.in.flight.requests.per.connection=5  # 자동 설정됨               │
│                                                                       │
│  [Consumer 설정]                                                      │
│  ────────────────                                                     │
│                                                                       │
│  isolation.level=read_committed   # 커밋된 메시지만 읽기              │
│  enable.auto.commit=false         # 수동 오프셋 관리                  │
│                                                                       │
│  [Broker 설정]                                                        │
│  ────────────────                                                     │
│                                                                       │
│  transaction.state.log.replication.factor=3   # 트랜잭션 로그 복제    │
│  transaction.state.log.min.isr=2              # 최소 ISR              │
│  min.insync.replicas=2                        # 데이터 토픽 ISR       │
│                                                                       │
└──────────────────────────────────────────────────────────────────────┘
```

### 완전한 코드 예시

```java
// Exactly-once Consume-Transform-Produce 패턴
public class ExactlyOnceProcessor {
    
    private final KafkaProducer<String, String> producer;
    private final KafkaConsumer<String, String> consumer;
    
    public ExactlyOnceProcessor() {
        // Producer 설정
        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        producerProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        producerProps.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, "order-processor-001");
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        
        this.producer = new KafkaProducer<>(producerProps);
        this.producer.initTransactions();  // 트랜잭션 초기화
        
        // Consumer 설정
        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "order-processor-group");
        consumerProps.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        
        this.consumer = new KafkaConsumer<>(consumerProps);
        this.consumer.subscribe(Collections.singletonList("input-topic"));
    }
    
    public void process() {
        while (true) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
            
            if (records.isEmpty()) continue;
            
            try {
                // 트랜잭션 시작
                producer.beginTransaction();
                
                for (ConsumerRecord<String, String> record : records) {
                    // Transform
                    String transformed = transform(record.value());
                    
                    // Produce to output topics
                    producer.send(new ProducerRecord<>("output-topic-1", record.key(), transformed));
                    producer.send(new ProducerRecord<>("output-topic-2", record.key(), transformed));
                }
                
                // Consumer 오프셋도 트랜잭션에 포함
                Map<TopicPartition, OffsetAndMetadata> offsets = getOffsetsToCommit(records);
                producer.sendOffsetsToTransaction(offsets, consumer.groupMetadata());
                
                // 트랜잭션 커밋 (모든 메시지 + 오프셋 원자적 커밋)
                producer.commitTransaction();
                
            } catch (Exception e) {
                // 실패 시 트랜잭션 중단
                producer.abortTransaction();
                // 오프셋 커밋 안 됐으므로 다음 poll()에서 재처리
            }
        }
    }
    
    private String transform(String input) {
        // 비즈니스 로직
        return input.toUpperCase();
    }
    
    private Map<TopicPartition, OffsetAndMetadata> getOffsetsToCommit(
            ConsumerRecords<String, String> records) {
        Map<TopicPartition, OffsetAndMetadata> offsets = new HashMap<>();
        for (TopicPartition partition : records.partitions()) {
            List<ConsumerRecord<String, String>> partitionRecords = records.records(partition);
            long lastOffset = partitionRecords.get(partitionRecords.size() - 1).offset();
            offsets.put(partition, new OffsetAndMetadata(lastOffset + 1));
        }
        return offsets;
    }
}
```

### 주의사항

```
┌──────────────────────────────────────────────────────────────────────┐
│                    Exactly-once 주의사항                              │
├──────────────────────────────────────────────────────────────────────┤
│                                                                       │
│  ⚠️ 트랜잭션 타임아웃                                                 │
│  ───────────────────                                                  │
│  transaction.max.timeout.ms=900000 (15분, 기본값)                    │
│  → 처리 시간이 타임아웃 초과 시 트랜잭션 자동 중단                      │
│  → 대량 배치 처리 시 주의                                             │
│                                                                       │
│  ⚠️ transactional.id 관리                                            │
│  ─────────────────────────                                            │
│  • 앱 인스턴스마다 고유해야 함                                         │
│  • 재시작 시 동일한 ID 사용 (펜싱 메커니즘)                            │
│  • 예: "order-processor-{hostname}-{partition}"                      │
│                                                                       │
│  ⚠️ Zombie Fencing                                                    │
│  ─────────────────                                                    │
│  ┌──────────────────────────────────────────────────────────────┐    │
│  │  Producer A (epoch=1)    Transaction Coordinator             │    │
│  │       │                         │                            │    │
│  │       │ ① beginTransaction()   │                            │    │
│  │       │────────────────────────►│                            │    │
│  │       │                         │                            │    │
│  │       │    💥 장애 발생          │                            │    │
│  │       X                         │                            │    │
│  │                                 │                            │    │
│  │  Producer A' (재시작, epoch=2)   │                            │    │
│  │       │ ② initTransactions()   │                            │    │
│  │       │────────────────────────►│  epoch=2로 업데이트        │    │
│  │       │                         │                            │    │
│  │  Producer A (좀비)              │                            │    │
│  │       │ ③ commitTransaction()  │                            │    │
│  │       │────────────────────────►│  epoch=1 < 2              │    │
│  │       │◄────────────────────────│  ❌ PRODUCER_FENCED!       │    │
│  │       │                         │                            │    │
│  │  💡 이전 인스턴스의 미완료 트랜잭션이 커밋되는 것을 방지          │    │
│  └──────────────────────────────────────────────────────────────┘    │
│                                                                       │
│  ⚠️ 성능 오버헤드                                                     │
│  ─────────────────                                                    │
│  • 트랜잭션 마커 쓰기로 인한 지연 증가 (~5-10ms)                       │
│  • 트랜잭션 코디네이터 통신 오버헤드                                   │
│  • read_committed 컨슈머는 LSO까지만 읽어 약간의 지연                  │
│                                                                       │
│  ⚠️ 외부 시스템 연동                                                  │
│  ─────────────────                                                    │
│  • DB, API 호출 등은 Kafka 트랜잭션에 포함 안 됨!                      │
│  • Outbox 패턴 또는 Saga 패턴으로 보완 필요                            │
│                                                                       │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 전략 비교

```
┌──────────────────────────────────────────────────────────────────────┐
│                    전달 보장 전략 종합 비교                            │
├──────────────────────────────────────────────────────────────────────┤
│                                                                       │
│  ┌──────────────┬────────┬────────┬────────┬──────────────────────┐  │
│  │ 전략          │ 손실   │ 중복   │ 복잡도  │ 사용 예               │  │
│  ├──────────────┼────────┼────────┼────────┼──────────────────────┤  │
│  │ At-most-once │ 가능   │ 없음   │ 낮음   │ 캐시 갱신, 메트릭     │  │
│  │ At-least-once│ 없음   │ 가능   │ 중간   │ ETL, 이벤트 처리     │  │
│  │ Exactly-once │ 없음   │ 없음   │ 높음   │ 금융, 재고 관리      │  │
│  └──────────────┴────────┴────────┴────────┴──────────────────────┘  │
│                                                                       │
│                                                                       │
│  [성능 비교]                                                          │
│  ─────────────                                                        │
│                                                                       │
│  처리량 (msg/s)                                                       │
│  ┌────────────────────────────────────────────────────────────────┐  │
│  │ At-most-once   ████████████████████████████████████  100,000   │  │
│  │ At-least-once  ██████████████████████████████        75,000    │  │
│  │ Exactly-once   ████████████████████                  50,000    │  │
│  └────────────────────────────────────────────────────────────────┘  │
│                                                                       │
│  지연 시간 (p99)                                                      │
│  ┌────────────────────────────────────────────────────────────────┐  │
│  │ At-most-once   ██                    2ms                       │  │
│  │ At-least-once  ████                  5ms                       │  │
│  │ Exactly-once   ██████████            15ms                      │  │
│  └────────────────────────────────────────────────────────────────┘  │
│                                                                       │
│                                                                       │
│  [복잡도 비교]                                                        │
│  ─────────────                                                        │
│                                                                       │
│  At-most-once                                                        │
│  ├── Producer: acks=0 또는 1                                         │
│  └── Consumer: auto.commit=true                                      │
│                                                                       │
│  At-least-once                                                       │
│  ├── Producer: acks=all, retries                                     │
│  ├── Consumer: auto.commit=false, 수동 커밋                           │
│  └── Application: Idempotent 처리 구현                                │
│                                                                       │
│  Exactly-once                                                        │
│  ├── Producer: idempotence + transaction                             │
│  ├── Consumer: read_committed                                        │
│  ├── Broker: transaction state log 설정                              │
│  └── Application: Consume-Transform-Produce 패턴                     │
│                                                                       │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 운영 고려

### 전략 선택 가이드

```
┌──────────────────────────────────────────────────────────────────────┐
│                    전달 보장 전략 선택 가이드                          │
├──────────────────────────────────────────────────────────────────────┤
│                                                                       │
│                     시작                                              │
│                       │                                              │
│                       ▼                                              │
│            ┌─────────────────────┐                                   │
│            │ 데이터 손실이        │                                   │
│            │ 허용되나요?          │                                   │
│            └──────────┬──────────┘                                   │
│                       │                                              │
│           Yes         │         No                                   │
│       ┌───────────────┴───────────────┐                              │
│       │                               │                              │
│       ▼                               ▼                              │
│  ┌──────────┐                ┌─────────────────────┐                 │
│  │At-most-  │                │ 중복 처리가         │                 │
│  │once      │                │ 허용되나요?          │                 │
│  └──────────┘                └──────────┬──────────┘                 │
│                                         │                            │
│                            Yes          │         No                 │
│                        ┌────────────────┴────────────────┐           │
│                        │                                 │           │
│                        ▼                                 ▼           │
│                 ┌──────────────┐                 ┌──────────────┐    │
│                 │ Idempotent   │                 │ Exactly-once │    │
│                 │ 처리가 가능? │                 │ (트랜잭션)   │    │
│                 └───────┬──────┘                 └──────────────┘    │
│                         │                                            │
│            Yes          │         No                                 │
│        ┌────────────────┴────────────────┐                           │
│        │                                 │                           │
│        ▼                                 ▼                           │
│  ┌──────────────┐                 ┌──────────────┐                   │
│  │ At-least-   │                 │ Exactly-once │                   │
│  │ once +      │                 │ (트랜잭션)   │                   │
│  │ Idempotent  │                 └──────────────┘                   │
│  └──────────────┘                                                    │
│                                                                       │
└──────────────────────────────────────────────────────────────────────┘
```

### 운영 Best Practices

```
┌──────────────────────────────────────────────────────────────────────┐
│                    운영 Best Practices                                │
├──────────────────────────────────────────────────────────────────────┤
│                                                                       │
│  📋 일반 권장사항                                                      │
│  ─────────────────                                                    │
│  ✓ At-least-once를 기본 전략으로 선택                                  │
│  ✓ 중복 허용이 어려운 핵심 비즈니스에만 Exactly-once 적용               │
│  ✓ 컨슈머에서 Idempotent 처리 로직 구현 (방어적 설계)                  │
│                                                                       │
│  📋 모니터링 항목                                                      │
│  ─────────────────                                                    │
│  ✓ Consumer Lag: 처리 지연 감지                                       │
│  ✓ Transaction 상태 로그 크기                                         │
│  ✓ Producer 재시도 횟수                                               │
│  ✓ Commit 실패 비율                                                   │
│                                                                       │
│  📋 버전 호환성                                                        │
│  ─────────────────                                                    │
│  ✓ Exactly-once: Kafka 0.11+ 필수                                    │
│  ✓ 브로커/프로듀서/컨슈머 버전 통일                                    │
│  ✓ Client 라이브러리 업데이트 시 호환성 확인                           │
│                                                                       │
│  📋 장애 대응                                                          │
│  ─────────────────                                                    │
│  ✓ DLQ (Dead Letter Queue) 설정                                      │
│  ✓ 재처리 프로세스 정의                                               │
│  ✓ 알림 및 자동 복구 메커니즘 구축                                     │
│                                                                       │
└──────────────────────────────────────────────────────────────────────┘
```

### Kafka Streams에서의 Exactly-once

```
┌──────────────────────────────────────────────────────────────────────┐
│                    Kafka Streams Exactly-once                         │
├──────────────────────────────────────────────────────────────────────┤
│                                                                       │
│  Kafka Streams는 Exactly-once를 단순한 설정으로 제공                   │
│                                                                       │
│  Properties props = new Properties();                                │
│  props.put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG,                │
│            StreamsConfig.EXACTLY_ONCE_V2);  // Kafka 2.5+            │
│                                                                       │
│  ┌─────────────────────────────────────────────────────────────┐     │
│  │                                                             │     │
│  │    Input Topic ──► Kafka Streams ──► Output Topic          │     │
│  │                         │                                   │     │
│  │                         ▼                                   │     │
│  │                    State Store                              │     │
│  │                   (RocksDB 등)                              │     │
│  │                                                             │     │
│  │    모든 과정이 원자적으로 처리됨:                             │     │
│  │    • Input offset commit                                   │     │
│  │    • State store update                                    │     │
│  │    • Output message produce                                │     │
│  │                                                             │     │
│  └─────────────────────────────────────────────────────────────┘     │
│                                                                       │
│  💡 직접 구현보다 Kafka Streams 사용을 권장                            │
│                                                                       │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 정리: 체크리스트

```
┌──────────────────────────────────────────────────────────────────────┐
│                    전달 보장 구현 체크리스트                           │
├──────────────────────────────────────────────────────────────────────┤
│                                                                       │
│  📋 At-least-once (권장 기본값)                                        │
│  □ Producer: acks=all 설정                                           │
│  □ Producer: retries 충분히 설정                                      │
│  □ Consumer: enable.auto.commit=false                                │
│  □ Consumer: 처리 완료 후 수동 커밋                                    │
│  □ Application: Idempotent 처리 로직 구현                             │
│                                                                       │
│  📋 Exactly-once (필요시)                                             │
│  □ Producer: enable.idempotence=true                                 │
│  □ Producer: transactional.id 설정                                   │
│  □ Consumer: isolation.level=read_committed                          │
│  □ Broker: transaction.state.log.replication.factor=3                │
│  □ Broker: min.insync.replicas=2                                     │
│  □ Code: beginTransaction → send → sendOffsetsToTransaction → commit │
│                                                                       │
│  📋 공통 운영                                                          │
│  □ 모니터링: Consumer Lag, 재시도 횟수, 커밋 실패율                    │
│  □ 알림: 임계치 초과 시 알림 설정                                      │
│  □ DLQ: 처리 실패 메시지 격리                                          │
│  □ 테스트: 장애 시나리오 테스트 (네트워크, 브로커 다운 등)              │
│                                                                       │
└──────────────────────────────────────────────────────────────────────┘
```
