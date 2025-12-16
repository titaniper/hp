# Kafka 학습 키워드 & 개념 정리

## 📚 핵심 키워드 목록

### 1. Kafka 기본 구성요소

| 키워드 | 개념 |
|--------|------|
| **Broker** | Kafka 서버의 단위. Producer의 메시지를 받아 offset 지정 후 디스크에 저장하고, Consumer 요청에 응답 |
| **Topic** | 메시지를 분류하는 논리적 단위. N개의 Partition으로 구성됨 |
| **Partition** | Topic을 물리적으로 나눈 단위. 순서 보장의 기본 단위이며, 병렬 처리의 핵심 |
| **Producer** | 메시지를 Kafka 브로커에 발행(적재)하는 서비스 |
| **Consumer** | 브로커에 적재된 메시지를 읽어오는(소비) 서비스 |
| **Consumer Group** | 같은 토픽을 구독하는 Consumer들의 논리적 그룹. 서비스 단위로 생성 |
| **Offset** | 파티션 내 메시지의 위치를 나타내는 순차적 ID. Consumer가 어디까지 읽었는지 추적 |
| **Message (Key, Value)** | Kafka에서 취급하는 데이터 단위. Key는 파티션 결정에 사용 |

### 2. 클러스터 & 고가용성

| 키워드 | 개념 |
|--------|------|
| **Cluster** | 여러 Broker를 묶어 고가용성(HA)을 확보한 구성 |
| **Replication** | 파티션의 복제본을 만들어 장애 대비. Leader/Follower 구조 |
| **Leader Replica** | 실제 읽기/쓰기를 담당하는 파티션 복제본 |
| **Follower Replica** | Leader를 복제하며 장애 시 승격 대기하는 복제본 |
| **Controller** | 클러스터 내에서 파티션 리더 선출 등 관리 역할을 하는 브로커 |
| **Rebalancing** | Consumer Group 내 파티션 소유권을 재분배하는 과정 |

### 3. 메시지 흐름 & 처리 전략

| 키워드 | 개념 |
|--------|------|
| **키 기반 파티셔닝** | 메시지 Key의 해시값으로 파티션 결정 → 같은 Key는 같은 파티션으로 |
| **Round-Robin** | Key가 없을 때 균등 분배를 위한 파티션 할당 방식 |
| **순서 보장** | 같은 파티션 내에서만 순서 보장됨 (FIFO) |
| **병렬 처리** | 파티션 수만큼 Consumer를 늘려 병렬 처리량 증가 |
| **At-least-once** | 메시지가 최소 1번 이상 전달됨을 보장 (중복 가능) |
| **Exactly-once** | 메시지가 정확히 1번만 처리됨을 보장 (트랜잭션 필요) |

### 4. 트랜잭션 & 이벤트 패턴

| 키워드 | 개념 |
|--------|------|
| **Transactional Outbox Pattern** | DB 트랜잭션과 메시지 발행의 원자성을 보장하는 패턴 |
| **Outbox Table** | 발행할 이벤트를 저장하는 별도 테이블. 메인 트랜잭션과 함께 커밋 |
| **이벤트 발행 보장** | Outbox + 배치/폴링으로 미발행 메시지 재처리 |
| **After Commit** | 트랜잭션 커밋 이후 이벤트 발행하여 데이터 일관성 보장 |
| **멱등성 (Idempotency)** | 같은 메시지를 여러 번 처리해도 결과가 동일함을 보장 |

### 5. Spring Kafka 연동

| 키워드 | 개념 |
|--------|------|
| **KafkaTemplate** | Spring에서 메시지 발행을 위한 템플릿 클래스 |
| **@KafkaListener** | 메시지 소비를 위한 어노테이션 |
| **@TransactionalEventListener** | 트랜잭션 이벤트 리스너. AFTER_COMMIT 등 시점 지정 가능 |
| **ConsumerConfig** | Consumer 설정 (group-id, auto-offset-reset 등) |
| **ProducerConfig** | Producer 설정 (acks, retries 등) |

---

## 🎯 학습 체크리스트

### STEP 17: 기초 학습
- [ ] Kafka 설치 (Docker Compose)
- [ ] CLI로 토픽 생성, 메시지 발행/소비 실습
- [ ] Spring Kafka Producer 구현
- [ ] Spring Kafka Consumer 구현
- [ ] 주문 완료 후 Kafka 메시지 발행 구현
- [ ] After Commit 기반 이벤트 발행 패턴 적용

### STEP 18: 비즈니스 프로세스 개선
- [ ] 선착순 쿠폰 발급 시나리오 설계
- [ ] 키 기반 파티셔닝으로 동시성 제어
- [ ] 대기열 토큰 활성화 설계
- [ ] Transactional Outbox Pattern 이해 및 적용
- [ ] 시퀀스 다이어그램 작성

---

## 📖 참고 명령어

```bash
# 토픽 생성
kafka-topics.sh --create --topic my-topic --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1

# 토픽 목록 조회
kafka-topics.sh --list --bootstrap-server localhost:9092

# 메시지 발행
kafka-console-producer.sh --topic my-topic --bootstrap-server localhost:9092

# 메시지 소비
kafka-console-consumer.sh --topic my-topic --bootstrap-server localhost:9092 --from-beginning

# Consumer Group 확인
kafka-consumer-groups.sh --bootstrap-server localhost:9092 --list

# Offset 리셋
kafka-consumer-groups.sh --bootstrap-server localhost:9092 --group my-group --reset-offsets --to-earliest --topic my-topic --execute
```

---

## 🔗 핵심 개념 연결 다이어그램

```
[Producer] 
    │
    ▼ (publish)
[Broker Cluster]
    ├── Topic A
    │   ├── Partition 0 (Leader) ──► Follower
    │   ├── Partition 1 (Leader) ──► Follower
    │   └── Partition 2 (Leader) ──► Follower
    │
    ▼ (consume)
[Consumer Group A]     [Consumer Group B]
    ├── Consumer 1         ├── Consumer 1
    ├── Consumer 2         └── Consumer 2
    └── Consumer 3
```

**Key Point**: 같은 메시지를 여러 Consumer Group이 각각 소비 가능!
