# 👁️ 모니터링 및 관찰 가능성 (Monitoring & Observability)

## 1. Observability (관찰 가능성)

### 1.1 모니터링 vs Observability
- **모니터링**: "시스템이 건강한가?" (Known Unknowns). 미리 정의된 지표를 통해 시스템 상태 확인.
- **Observability**: "시스템이 왜 그런 상태인가?" (Unknown Unknowns). 시스템의 출력을 통해 내부 상태를 유추할 수 있는 능력.

### 1.2 Observability의 3대 기둥 (Three Pillars)

```mermaid
graph TD
    Obs[Observability] --> Metrics
    Obs --> Logs
    Obs --> Traces
    
    Metrics[Metrics<br/>(What?)]
    Logs[Logs<br/>(Why?)]
    Traces[Traces<br/>(Where?)]
    
    Metrics -->|시계열 데이터| Grafana
    Logs -->|이벤트 기록| ELK/OpenSearch
    Traces -->|요청 흐름| Jaeger/Zipkin
```

1.  **Metrics (메트릭)**: 시간에 따라 측정된 수치 데이터. (CPU 사용량, TPS 등) -> **경향 파악**
2.  **Logs (로그)**: 시스템에서 발생한 이산적인 이벤트 기록. -> **상세 원인 분석**
3.  **Traces (트레이스)**: 분산 시스템에서 요청의 전체 경로 추적. -> **병목 구간 확인**

---

## 2. 모니터링 도구 및 스택

### 2.1 APM (Application Performance Monitoring)
- **역할**: 애플리케이션의 성능을 코드 레벨까지 상세하게 모니터링.
- **도구**:
    - **Datadog**: 통합 모니터링 SaaS. 강력하지만 비용 발생.
    - **Pinpoint**: Java 기반 오픈소스 APM. Call Stack 추적에 강점.
    - **Scouter**: 국산 오픈소스 APM.

### 2.2 로그 및 메트릭 스택
- **ELK Stack**: Elasticsearch (저장/검색) + Logstash (수집) + Kibana (시각화).
- **Prometheus + Grafana**:
    - **Prometheus**: Pull 방식의 메트릭 수집 및 저장 (시계열 DB).
    - **Grafana**: 다양한 데이터 소스를 시각화하는 대시보드 도구.

---

## 3. 운영 전략 및 패턴

### 3.1 알림 (Alerting) 설계
- **Alert Fatigue (알림 피로)**: 너무 잦은 알림으로 인해 운영자가 알림에 무감각해지는 현상.
- **방지 전략**:
    - **Severity Level 구분**: Critical(즉시 대응, 전화), Warning(업무 시간 내 확인, 슬랙), Info(기록).
    - **Deduplication**: 동일한 원인의 알림 중복 제거.
    - **Silence**: 계획된 작업이나 이미 인지한 장애에 대해 알림 일시 중지.

### 3.2 로그 추적성 확보 (MDC)
- **MDC (Mapped Diagnostic Context)**: 멀티 스레드 환경에서 로그에 컨텍스트 정보(TraceID, UserID 등)를 남겨 요청별로 로그를 묶어서 볼 수 있게 함.

**예시 (Spring Boot + Logback):**
```java
// Filter 또는 Interceptor에서
MDC.put("traceId", UUID.randomUUID().toString());

// 로그 출력 시
log.info("Order processed"); 
// 출력: [TraceID: abc-123] Order processed
```

### 3.3 데이터 관리
- **Sampling (샘플링)**: 모든 로그/트레이스를 저장하면 비용이 과다하므로, 전체 요청의 일부(예: 5%)만 저장하거나 오류 발생 시에만 저장하는 전략.
- **Retention Policy (보관 주기)**: 데이터의 중요도와 규제에 따라 보관 기간 설정 (예: 메트릭 1년, 상세 로그 1개월).
