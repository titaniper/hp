# 🪵 로깅 시스템과 ELK Stack (Logging Systems & ELK)

## 1. 중앙 집중형 로깅 (Centralized Logging)

### 1.1 필요성

MSA 환경에서는 수십, 수백 개의 컨테이너가 수시로 생성되고 사라집니다. 각 서버에 접속해서 로그 파일(`tail -f`)을 확인하는 것은 불가능하므로, 모든 로그를 한곳으로 모아서 저장하고 검색할 수 있어야 합니다.

---

## 2. ELK Stack (Elastic Stack)

가장 널리 사용되는 오픈소스 로깅 스택입니다. (최근에는 AWS 주도의 **OpenSearch**도 많이 사용됩니다.)

### 2.1 구성 요소

```mermaid
flowchart LR
    App[Application] -->|Logs| Beat[Filebeat]
    Beat -->|Buffer| Kafka[Kafka]
    Kafka -->|Parsing| Logstash[Logstash]
    Logstash -->|Indexing| ES[Elasticsearch]
    ES -->|Visualization| Kibana[Kibana]
```

1. **Beats (Filebeat)**: 각 서버/컨테이너에 설치되어 로그 파일을 읽어 전송하는 경량 수집기.
2. **Logstash**: 수집된 로그를 필터링, 파싱, 변환하여 Elasticsearch로 전송. (무거워서 최근에는 Fluentd나 Fluent Bit로 대체되기도 함)
3. **Elasticsearch**: 로그 데이터를 저장하고 검색 엔진을 통해 빠르게 검색/분석.
4. **Kibana**: Elasticsearch에 저장된 데이터를 시각화(대시보드)하고 검색하는 UI.

### 2.2 로그 수집 패턴

- **Sidecar 패턴**: 각 애플리케이션 컨테이너 옆에 로그 수집 컨테이너를 배치.
- **DaemonSet 패턴 (Kubernetes)**: 각 노드(Node)마다 하나의 로그 수집 에이전트를 실행하여 해당 노드의 모든 컨테이너 로그를 수집. (리소스 효율적)

---

## 3. 로그 파이프라인 설계 고려사항

### 3.1 버퍼링 (Buffering)

트래픽 폭주 시 로그가 급증하면 Elasticsearch에 부하가 갈 수 있습니다. 중간에 **Kafka** 같은 메시지 큐를 두어 로그를 버퍼링하면 안정적인 처리가 가능합니다.

### 3.2 구조화된 로그 (Structured Logging)

로그를 단순 텍스트가 아닌 **JSON** 형태로 남기면 파싱이 쉽고 검색이 용이합니다.

**Bad (Text):**
`2025-12-22 10:00:00 ERROR [OrderService] Order failed for user 123: Out of stock`

**Good (JSON):**

```json
{
  "timestamp": "2025-12-22T10:00:00Z",
  "level": "ERROR",
  "service": "OrderService",
  "userId": "123",
  "message": "Order failed",
  "reason": "Out of stock",
  "traceId": "abc-123"
}
```

### 3.3 보관 주기 (Retention Policy)

로그 데이터는 용량을 많이 차지하므로 무한정 저장할 수 없습니다.

- **Hot**: 최근 7일 (SSD, 빠른 검색)
- **Warm**: 7일~30일 (HDD, 검색 가능)
- **Cold**: 30일 이상 (S3 등으로 아카이빙, 필요 시 복구)
- **Delete**: 보관 주기 지난 로그 삭제

---

## 4. EBK? (참고)

질문하신 **EBK**는 일반적으로 통용되는 약어는 아니지만, 문맥상 **ELK** (Elasticsearch, Logstash, Kibana) 또는 **EFK** (Elasticsearch, Fluentd, Kibana)를 의미하는 것으로 보입니다.

- **Fluentd**: Logstash보다 가볍고 유연한 오픈소스 데이터 수집기 (CNCF 졸업 프로젝트). Kubernetes 환경에서 표준처럼 사용됨.
