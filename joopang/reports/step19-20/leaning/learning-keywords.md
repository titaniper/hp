# 🔑 학습 키워드 & 참고 자료

## 📌 핵심 키워드 목록

---

### 🔴 장애 대응 관련

| 키워드 | 설명 |
|--------|------|
| **장애 (Failure)** | 운영 중인 서버가 정상 작동이 불가능한 상태 |
| **SPOF** | Single Point Of Failure, 단일 장애 지점 |
| **OOM** | Out Of Memory Error, 메모리 부족 오류 |
| **Unhandled Exception** | 처리되지 않은 예외로 인한 APP Crash |
| **MTTD** | Mean Time To Detect, 장애 감지 평균 시간 |
| **MTTR** | Mean Time To Recover, 장애 복구 평균 시간 |
| **Postmortem** | 장애 회고 문서 |
| **SLA** | Service Level Agreement, 서비스 수준 협약 |
| **SLO** | Service Level Objective, 서비스 수준 목표 |
| **High Availability** | 고가용성, 지속적 정상 운영 가능 성질 |

---

### 🟠 모니터링 도구 관련

| 키워드 | 설명 |
|--------|------|
| **APM** | Application Performance Monitoring |
| **Datadog** | 상용 APM 도구 |
| **Pinpoint** | 네이버 오픈소스 APM |
| **Grafana** | 오픈소스 메트릭 시각화 도구 |
| **Prometheus** | 오픈소스 메트릭 수집/저장 시스템 |
| **OpenSearch** | AWS의 ElasticSearch 포크 (로그 모니터링) |
| **ELK Stack** | Elasticsearch + Logstash + Kibana |
| **Slack Alert** | Slack 알림 시스템 |
| **Health Check** | 서버 상태 확인 |

---

### 🟡 모니터링 지표 관련

| 키워드 | 설명 |
|--------|------|
| **RPS / TPS** | Request/Transaction Per Second |
| **Latency** | API 처리 시간 (응답 지연) |
| **QPS** | Query Per Second |
| **p95 / p99** | 95%, 99% 백분위 응답 시간 |
| **Cache Hit Rate** | 캐시 적중률 |
| **Connection Pool** | DB 연결 풀 (활성/유휴) |
| **Heap Memory** | JVM 힙 메모리 |
| **GC Pause** | Garbage Collection 일시 정지 |

---

### 🟢 부하 테스트 관련

| 키워드 | 설명 |
|--------|------|
| **Load Test** | 예상 부하 정상 처리 여부 평가 |
| **Stress Test** | 지속적 부하 증가 시 처리 능력 평가 |
| **Peak Test** | 일시적 최고 부하 처리 능력 평가 |
| **Endurance Test** | 장기간 안정성 평가 |
| **TPS** | Transaction Per Second |
| **vUser** | Virtual User, 가상 사용자 |
| **Deadlock** | 교착 상태 |
| **Slow Query** | 느린 쿼리 |
| **Memory Leak** | 메모리 누수 |

---

### 🔵 부하 테스트 도구

| 키워드 | 설명 |
|--------|------|
| **k6** | 오픈소스, JS 기반 경량 고성능 부하 테스트 툴 |
| **JMeter** | Apache 성능 테스팅 툴 |
| **nGrinder** | 네이버 성능 테스팅 툴 |
| **Artillery** | 오픈소스 클라우드 환경 로드 테스팅 툴 |

---

### 🟣 아키텍처 / 메시징 관련

| 키워드 | 설명 |
|--------|------|
| **Redis Pub/Sub** | Redis 메시지 발행/구독 |
| **RabbitMQ** | AMQP 프로토콜 메시지 큐 |
| **Kafka** | Pub-Sub 기반 메시지 발행/구독 시스템 |
| **Partition** | Kafka 토픽의 병렬 처리 단위 |
| **DLQ** | Dead Letter Queue, 처리 실패 메시지 보관 큐 |
| **Circuit Breaker** | 서킷브레이커, 장애 전파 방지 패턴 |
| **ISTIO** | 서비스 메시 플랫폼 (트래픽 관리, 보안, 관찰성) |

---

### ⚪ 비동기 처리 (Async) 관련

| 키워드 | 설명 |
|--------|------|
| **Async** | 비동기 처리 |
| **MDC** | Mapped Diagnostic Context, 로그 추적용 컨텍스트 |
| **Async Thread Pool** | 비동기 작업용 스레드 풀 |
| **@Async** | Spring 비동기 메서드 어노테이션 |
| **CompletableFuture** | Java 비동기 프로그래밍 API |

---

### ⚫ Observability (관찰 가능성) 관련

| 키워드 | 설명 |
|--------|------|
| **Observability** | 시스템 상태를 외부에서 관찰할 수 있는 능력 |
| **Tracing** | 분산 추적 (요청 흐름 추적) |
| **Sampling** | 로그/메트릭/트레이스 샘플링 |
| **Retention Policy** | 데이터 보관 주기 정책 |
| **Chaos Engineering** | 카오스 엔지니어링, 의도적 장애 유발 테스트 |
| **Alert Fatigue** | 알림 피로, 과도한 알림으로 인한 둔감화 |

---

## 📚 참고 자료 링크

### 부하 테스트 도구

- [k6 공식 사이트](https://k6.io/)
- [JMeter를 이용한 부하 테스트 작성하기](https://creampuffy.tistory.com/209)
- [nGrinder와 Spring으로 부하 테스트 시나리오 작성하기](https://leezzangmin.tistory.com/42)
- [Artillery를 이용한 부하테스트](https://techblog.tabling.co.kr/artillery%EB%A5%BC-%EC%9D%B4%EC%9A%A9%ED%95%9C-%EB%B6%80%ED%95%98-%ED%85%8C%EC%8A%A4%ED%8A%B8-9d1f6bb2c2f5)

### 장애 대응 사례

- [우아한 장애 대응](https://techblog.woowahan.com/4886/)
- [LINE 장애 대응 문화](https://engineering.linecorp.com/ko/blog/line-platform-server-outage-process-and-dev-culture)

### 서비스 상태 모니터링

- [Downdetector (자연재해 감지기)](https://downdetector.com/)

### AWS SLA

- [AWS Service Level Agreements](https://aws.amazon.com/ko/legal/service-level-agreements/)

---

## ❓ 멘토링 질문 키워드

### Observability 스택 관련

- 실무에서 주로 사용하는 **Observability 스택 조합** (Datadog, OpenSearch, Grafana + Prometheus, Elastic Stack 등)

### 샘플링 & 보관 정책

- 로그/메트릭/트레이스 **Sampling** 허용 범위
- **Retention Policy** (보관 주기) 설정 기준

### 카오스 엔지니어링

- **Chaos Engineering** 실무 도입 경험
- 의도적 장애 유발을 통한 복원력 검증

### 알림 설계

- **Alert Fatigue** 방지 전략
- 심각도(Level)별 채널 분리 운영
- 치명적 장애 시 전화/문자 알림
- "지금 봐야 하는 알림" vs "나중에 확인해도 되는 알림" 구분
- 사일런스 처리 기준

### 핵심 지표 관리

- **SLA / SLO / Error Budget** 정의 및 관리
- Latency, Error Rate, Throughput, Saturation 추적

### DLQ 설계

- DLQ 전송 여부 판단 기준
- "모든 실패 메시지를 DLQ로 보내는 것이 맞는가?"
- DLQ 처리 책임 분담

### 장애 방지 아키텍처

- 실무에서 효과적이었던 장애 방지/대응 패턴
- "이 설계 덕분에 큰 사고를 막았다" 경험

---

## 🏷️ 키워드 카테고리별 태그

```
#장애대응 #모니터링 #부하테스트 #고가용성 #성능분석
#APM #Grafana #Datadog #k6 #JMeter
#TPS #Latency #MTTD #MTTR #SPOF
#OOM #Deadlock #SlowQuery #MemoryLeak
#Redis #Kafka #RabbitMQ #DLQ #CircuitBreaker
#ISTIO #Async #MDC #Observability #ChaosEngineering
```
