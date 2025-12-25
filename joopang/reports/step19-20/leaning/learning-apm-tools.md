# 🕵️ APM 도구와 분산 추적 (APM Tools & Distributed Tracing)

## 1. 분산 추적 (Distributed Tracing)

### 1.1 개념
마이크로서비스 환경에서 하나의 사용자 요청이 여러 서비스를 거쳐 처리될 때, 전체 흐름을 추적하는 기술입니다.

### 1.2 작동 원리 (Trace Context Propagation)
요청이 서비스 간에 이동할 때 HTTP 헤더에 식별자를 포함시켜 전달합니다.

- **Trace ID**: 전체 트랜잭션을 식별하는 고유 ID.
- **Span ID**: 작업의 각 단위(구간)를 식별하는 ID.
- **Parent Span ID**: 호출 관계를 나타내는 ID.

```mermaid
sequenceDiagram
    participant Client
    participant Service A
    participant Service B
    participant DB

    Client->>Service A: Request (TraceID 생성)
    activate Service A
    Service A->>Service A: Span A1 (Processing)
    Service A->>Service B: Request (TraceID, Parent=A1)
    activate Service B
    Service B->>DB: Query (Span B1)
    DB-->>Service B: Result
    Service B-->>Service A: Response
    deactivate Service B
    Service A-->>Client: Response
    deactivate Service A
```

---

## 2. Pinpoint (오픈소스 APM)

### 2.1 특징
- **Java 최적화**: Java 애플리케이션에 특화되어 있으며, Bytecode Instrumentation 기술을 사용하여 코드를 수정하지 않고 에이전트만 부착하면 됩니다.
- **Call Stack 시각화**: 메서드 레벨까지 상세한 호출 스택과 소요 시간을 보여줍니다.
- **Server Map**: 서비스 간의 연결 관계와 트래픽 흐름을 토폴로지 맵으로 시각화합니다.

### 2.2 아키텍처
- **Agent**: 애플리케이션에 부착되어 데이터 수집.
- **Collector**: 에이전트로부터 데이터를 수집하여 저장소에 저장.
- **HBase**: 대용량 트레이스 데이터를 저장하는 NoSQL DB.
- **Web**: 저장된 데이터를 시각화하여 보여주는 UI.

---

## 3. Datadog (SaaS APM)

### 3.1 특징
- **통합 관제**: 인프라, 애플리케이션, 로그, 네트워크, 보안 모니터링을 하나의 플랫폼에서 제공.
- **Tagging System**: `env:prod`, `service:payment` 등 태그 기반으로 데이터를 필터링하고 집계하는 기능이 강력함.
- **Watchdog**: 머신러닝 기반으로 이상 징후를 자동으로 탐지하고 알림을 보냄.

### 3.2 Pinpoint vs Datadog

| 특징 | Pinpoint | Datadog |
|------|----------|---------|
| **유형** | 오픈소스 (설치형) | SaaS (구독형) |
| **비용** | 무료 (인프라 운영 비용 발생) | 유료 (사용량 기반 과금) |
| **주 언어** | Java, PHP, Python 등 | 거의 모든 언어 지원 |
| **장점** | 상세한 Call Stack, 무료 | 설치 간편, 통합 UI, 알림/협업 기능 강력 |
| **단점** | 설치/운영 복잡, UI가 다소 투박 | 비용 부담, 데이터 외부 전송 이슈 |

---

## 4. 트레이싱 데이터 활용 예시

**문제 상황**: "결제 요청이 간헐적으로 3초 이상 걸린다."

1. **APM 접속**: 느린 트랜잭션 목록 조회.
2. **Trace 분석**: 3초 걸린 트랜잭션의 상세 Span 확인.
3. **병목 확인**:
    - Service A (10ms) -> Service B (2900ms) -> DB (50ms)
    - Service B 내부에서 외부 API 호출 구간이 2.8초 소요됨을 발견.
4. **조치**: 외부 API 타임아웃 설정 및 서킷 브레이커 적용.
