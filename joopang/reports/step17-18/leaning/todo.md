# STEP 17-18 해야 할 일 정리

## 📋 STEP 17: 카프카 기초 학습 및 활용

### 1. 개념 학습 & 문서 작성 (1.5h)

- [ ] 카프카 핵심 개념 정리 문서 작성
  - [ ] Broker, Topic, Partition 개념 정리
  - [ ] Producer, Consumer, Consumer Group 개념 정리
  - [ ] Offset, Replication, Rebalancing 개념 정리
  - [ ] 구성요소 간 데이터 흐름 다이어그램
- [ ] 왜 대용량 시스템에서 Kafka를 쓰는지 정리

### 2. 로컬 환경 구축 (1h)

- [ ] Docker Compose로 Kafka + Zookeeper 실행

  ```yaml
  # docker-compose.yml에 kafka, zookeeper 서비스 추가
  ```

- [ ] CLI로 기본 동작 확인
  - [ ] 토픽 생성 (`kafka-topics.sh --create`)
  - [ ] 메시지 발행 (`kafka-console-producer.sh`)
  - [ ] 메시지 소비 (`kafka-console-consumer.sh`)

### 3. Spring Boot 연동 (2h)

- [ ] Spring Kafka 의존성 추가

  ```kotlin
  implementation("org.springframework.kafka:spring-kafka")
  ```

- [ ] Kafka 설정 추가 (application.yml)
- [ ] Producer 구현 (KafkaTemplate 사용)
- [ ] Consumer 구현 (@KafkaListener 사용)
- [ ] 메시지 발행 → 소비 흐름 테스트

### 4. 기존 로직 전환 (2h)

- [ ] 주문 완료 이벤트 → Kafka 메시지 발행으로 전환
  - [ ] 기존: `@TransactionalEventListener` + mockAPI 호출
  - [ ] 변경: `@TransactionalEventListener(AFTER_COMMIT)` + `kafkaProducer.publish()`
- [ ] 데이터 플랫폼 전송 로직 Kafka로 전환

### 📄 STEP 17 산출물

- [ ] `kafka-concepts.md` - 카프카 기본 개념 정리 문서
- [ ] `docker-compose.yml` - Kafka 환경 구성
- [ ] Producer/Consumer 코드
- [ ] 실행 로그 스크린샷

---

## 📋 STEP 18: 카프카를 활용하여 비즈니스 프로세스 개선

### 1. 선착순 쿠폰 발급 설계 (2h)

- [ ] 기존 Redis 기반 로직의 한계점 분석
- [ ] Kafka 기반 설계 문서 작성
  - [ ] 토픽: `coupon-publish-request`
  - [ ] 메시지 키: 쿠폰ID (같은 쿠폰은 같은 파티션 → 순서 보장)
  - [ ] 파티션 전략: 쿠폰별 병렬 처리
- [ ] 시퀀스 다이어그램 작성
- [ ] 구현
  - [ ] 쿠폰 발급 요청 Producer
  - [ ] 쿠폰 발급 처리 Consumer

### 2. 대기열 토큰 활성화 설계 (2h)

- [ ] 기존 대기열 로직 분석
- [ ] Kafka 기반 설계 문서 작성
  - [ ] 토픽: `waiting-token`
  - [ ] N초당 M개 메시지 컨슘 전략
  - [ ] 파티션 전략
    - [ ] 전체 대기열: 파티션 1개 (순서 보장 우선)
    - [ ] 콘서트별 대기열: 콘서트ID를 키로 (선택)
- [ ] 시퀀스 다이어그램 작성

### 3. (도전) Transactional Outbox Pattern 적용 (2h)

- [ ] Outbox 테이블 설계

  ```sql
  CREATE TABLE outbox_event (
    id BIGINT PRIMARY KEY,
    aggregate_type VARCHAR(255),
    aggregate_id VARCHAR(255),
    event_type VARCHAR(255),
    payload TEXT,
    status VARCHAR(50), -- INIT, PUBLISHED
    created_at TIMESTAMP,
    published_at TIMESTAMP
  );
  ```

- [ ] 메인 트랜잭션에서 Outbox 데이터 적재
- [ ] Outbox → Kafka 발행 Consumer 구현
- [ ] 미발행 이벤트 재처리 배치/스케줄러 구현

### 📄 STEP 18 산출물

- [ ] `coupon-kafka-design.md` - 쿠폰 발급 설계 문서
- [ ] `queue-kafka-design.md` - 대기열 설계 문서
- [ ] 시퀀스 다이어그램 (Mermaid)
- [ ] 성능 개선 비교표 (Redis vs Kafka)

---

## ⏰ 시간 배분 (총 10h)

| 작업 | 예상 시간 |
|------|----------|
| 개념 학습 & 문서 작성 | 1.5h |
| 로컬 환경 구축 | 1h |
| Spring Boot 연동 | 2h |
| 기존 로직 전환 | 2h |
| 쿠폰 발급 설계 & 구현 | 2h |
| 대기열 설계 | 1.5h |

---

## ✅ P/F 체크리스트

### STEP 17 통과 기준

- [ ] 카프카 핵심 개념 문서 작성 완료
- [ ] 어플리케이션에서 메시지 발행/소비 동작 확인
- [ ] 주문 완료(커밋) 후 Kafka 메시지 발행 구현

### STEP 18 통과 기준

- [ ] 비즈니스 프로세스에 Kafka 적절히 활용한 설계
- [ ] 설계 문서와 동일하게 구현

---

## 🚀 Quick Start

```bash
# 1. Kafka 실행
cd joopang
docker-compose up -d kafka zookeeper

# 2. 토픽 확인
docker exec -it kafka kafka-topics.sh --list --bootstrap-server localhost:9092

# 3. 애플리케이션 실행
./gradlew bootRun

# 4. 테스트
./gradlew test
```

---

## 📌 참고 자료

- [Spring Kafka Docs](https://docs.spring.io/spring-kafka/reference/)
- [Kafka 공식 문서](https://kafka.apache.org/documentation/)
- [Transactional Outbox Pattern](https://microservices.io/patterns/data/transactional-outbox.html)
