# MSA (Microservices Architecture)

## 1. MSA란 무엇인가?

**Microservices Architecture (MSA)**는 하나의 거대한 애플리케이션을 **작고 독립적인 여러 개의 서비스(Microservices)**로 나누어 개발, 배포, 유지보수하는 아키텍처 스타일입니다.

기존의 **Monolithic Architecture(모놀리식 아키텍처)**가 모든 기능을 하나의 프로젝트에 담아 통합된 형태로 개발되는 것과 대조적입니다. 모놀리식은 초기 개발이 빠르지만, 규모가 커질수록 빌드/배포 시간이 길어지고, 작은 수정사항이 전체 시스템에 영향을 줄 수 있으며, 특정 기능만 스케일아웃(Scale-out)하기 어려운 단점이 있습니다.

반면 MSA는 각 서비스가 독립적으로 실행되며, HTTP API(REST)나 메시지 큐(Message Queue) 같은 가벼운 통신 메커니즘을 통해 서로 통신합니다.

### MSA의 장점

* **배포 유연성**: 서비스별로 독립적인 배포가 가능하여 전체 시스템 중단 없이 업데이트가 가능합니다.
* **확장성 (Scalability)**: 트래픽이 많은 특정 서비스만 선택적으로 확장(Scale-out)할 수 있어 리소스 효율이 좋습니다.
* **기술 다양성**: 각 서비스의 목적에 맞는 최적의 언어, 프레임워크, 데이터베이스를 선택할 수 있습니다 (Polyglot Programming).
* **장애 격리**: 하나의 서비스에 장애가 발생해도 다른 서비스로 전파되지 않도록 격리할 수 있어 전체 시스템의 가용성이 높아집니다.

### MSA의 단점

* **복잡성 증가**: 분산 시스템이므로 서비스 간 통신, 트랜잭션 관리, 장애 처리 등이 복잡해집니다.
* **데이터 일관성**: 분산 데이터베이스 환경에서 데이터 정합성(Consistency)을 유지하기 위해 Saga 패턴 등의 복잡한 트랜잭션 관리가 필요합니다.
* **운영 비용**: 관리해야 할 서비스 인스턴스가 많아지므로 모니터링, 로깅, 배포 자동화 등 인프라 운영 비용이 증가합니다.

---

## 2. MSA가 갖춰야 하는 핵심 요소 (Components & Capabilities)

성공적인 MSA 구축을 위해서는 단순히 서비스를 나누는 것 외에도 분산 환경을 지탱할 수 있는 여러 인프라와 패턴이 필요합니다.

### 1) 서비스 독립성 및 분산 데이터 관리 (Database per Service)

* 각 마이크로서비스는 **자신만의 데이터베이스**를 가져야 합니다.
* 다른 서비스의 데이터베이스에 직접 접근하지 않고, 오직 API를 통해서만 데이터를 주고받아야 결합도(Coupling)를 낮출 수 있습니다.

### 2) API Gateway

* 클라이언트(Web, Mobile)가 여러 마이크로서비스를 직접 호출하는 대신, **단일 진입점** 역할을 하는 서버입니다.
* **역할**: 인증/인가, 라우팅, 로드 밸런싱, 프로토콜 변환, 로깅 등 공통 기능을 처리합니다.

### 3) Service Discovery

* 동적으로 생성되고 삭제되는 서비스 인스턴스들의 **IP 주소와 포트 정보를 관리**하고 찾아주는 기능입니다.
* 서비스가 시작될 때 자신의 위치를 등록(Registry)하고, 클라이언트나 게이트웨이가 이를 조회하여 통신합니다. (예: Netflix Eureka, Consul)

### 4) 장애 격리 및 회복 탄력성 (Fault Tolerance & Resilience)

* 특정 서비스의 장애가 전체 시스템으로 전파되는 것을 막아야 합니다.
* **Circuit Breaker**: 장애가 발생한 서비스로의 요청을 차단하여 대기 시간을 줄이고 시스템을 보호합니다. (예: Resilience4j)
* **Fallback**: 요청 실패 시 기본값을 반환하거나 대체 로직을 수행합니다.

### 5) 분산 트랜잭션 관리 (Saga Pattern)

* 여러 서비스에 걸친 비즈니스 로직을 처리할 때 데이터 일관성을 보장하기 위한 패턴입니다.
* 2PC(Two-Phase Commit) 대신, 각 서비스의 로컬 트랜잭션을 순차적으로 실행하고, 실패 시 보상 트랜잭션(Compensating Transaction)을 실행하여 롤백합니다.

### 6) 중앙 집중식 로깅 및 모니터링 (Centralized Logging & Monitoring)

* 수십, 수백 개의 서비스 로그를 개별 서버에서 확인하는 것은 불가능합니다.
* **ELK Stack (Elasticsearch, Logstash, Kibana)** 등을 통해 로그를 한곳에 모아 분석해야 합니다.
* **Tracing**: 분산 추적(Distributed Tracing) 도구(Zipkin, Jaeger)를 사용하여 하나의 요청이 여러 서비스를 거쳐가는 흐름을 추적해야 합니다.

### 7) CI/CD 자동화 (DevOps)

* 서비스가 많아질수록 수동 배포는 불가능에 가깝습니다.
* 빌드, 테스트, 컨테이너 이미지 생성, 배포 과정을 자동화해야 합니다. (Jenkins, GitHub Actions, ArgoCD 등)

---

## 3. MSA 예시: 이커머스 (E-commerce) 시스템

전통적인 쇼핑몰을 MSA로 전환한다고 가정했을 때의 구조 예시입니다.

### 서비스 분리

1. **사용자 서비스 (User Service)**: 회원 가입, 로그인, 프로필 관리.
2. **상품 서비스 (Product Service)**: 상품 등록, 수정, 재고 조회, 카테고리 관리.
3. **주문 서비스 (Order Service)**: 주문 생성, 주문 내역 조회, 취소.
4. **결제 서비스 (Payment Service)**: 결제 승인, 환불 처리, PG사 연동.
5. **배송 서비스 (Delivery Service)**: 배송 상태 추적, 운송장 관리.

### 시나리오: "사용자가 상품을 주문한다"

1. **Client** -> **API Gateway**: 주문 요청 전송.
2. **API Gateway**: 인증 토큰 확인 후 **주문 서비스**로 요청 라우팅.
3. **주문 서비스**:
    * **상품 서비스** API 호출 -> 재고 확인 및 차감.
    * **사용자 서비스** API 호출 -> 배송지 정보 확인.
    * 주문 데이터 생성 (Pending 상태).
4. **주문 서비스** -> **결제 서비스**: 결제 요청 이벤트 발행 (Kafka 등 메시지 큐 사용).
5. **결제 서비스**: 결제 처리 후 '결제 완료' 이벤트 발행.
6. **주문 서비스**: '결제 완료' 이벤트 수신 후 주문 상태를 'Complete'로 변경.
7. **배송 서비스**: '결제 완료' 이벤트 수신 후 배송 준비 시작.

이 과정에서 만약 **결제 서비스**가 다운되더라도, 사용자는 상품을 장바구니에 담거나 상품 목록을 보는(**상품 서비스**) 기능은 정상적으로 이용할 수 있습니다. 이것이 MSA의 핵심인 **장애 격리**입니다.

---

# 4. 단일 모놀리식 서비스 → MSA 전환 로드맵 (실무 가이드)

아래는 **단일 모놀리식 서비스 → MSA(Microservices Architecture)** 로 전환할 때 일반적으로 수행해야 하는 전체적인 로드맵을 **실행 순서 중심, 실무 기준**으로 정리한 것입니다.

## 4.1 목표 정의 및 경계 설정

### 1) 왜 MSA인가?

먼저 조직 내부 합의가 필요합니다. 흔한 목표는 다음과 같습니다.

* 배포 속도 및 독립성 향상
* 장애 전파 최소화
* 팀별 독립 개발 속도 증가
* 도메인 기반 서비스 구조 확립(DDD 기반)

→ 목표 없이 분리하면 **쿼리 분산, 트랜잭션 처리, 운영 난이도**만 증가할 수 있습니다.

### 2) 무엇부터 MSA로 전환할지 결정

모든 것을 한 번에 쪼개면 실패합니다. 가장 좋은 시작점은:

* **변경이 잦은 도메인**
* **다른 도메인과 의존성이 낮은 도메인**
* **확장 필요성이 큰 도메인**

예: Auth, User, Billing, Order, Payment, Inventory, Notification, Marketing 등.

## 4.2 MSA 전환 핵심 아키텍처 결정

### 1) 서비스 분리 기준 (DDD)

* Bounded Context 단위로 나눌 것
* Context Map 작성
* Entity/ValueObject/Aggregate 기준 재정의
* 각 Context 간 **동기/비동기 인터페이스**를 설계할 것

### 2) 서비스 간 통신 방식

반드시 **혼합 전략**을 쓴다:

* 동기: REST/gRPC
* 비동기: Kafka (이벤트 발행/소비 기반)
* Outbox 패턴: DB → Kafka 메시지 정합성 확보
* SAGA 패턴: 분산 트랜잭션 처리

### 3) 데이터 분리 전략

MSA의 필수 요소:

* 서비스별 DB 독립
* 동일 스키마 공유 금지
* 조회용은 API Composition / CQRS / Materialized View 사용
* 변경 이벤트(Event-driven)로 다른 서비스 상태에 반영

## 4.3 인프라 및 플랫폼 준비

### 1) CI/CD 파이프라인

각 서비스는 “독립 배포”가 가능해야 한다.

* GitHub Actions / Jenkins / GitLab CI
* Canary / Rolling Update
* Versioning / Tag 기반 배포

### 2) Kubernetes 기반 배포 관리

* Service Mesh (Istio/Linkerd) 고려
* API Gateway 배치 (Kong, NGINX, Traefik)
* Observability: Prometheus, Grafana, ELK, OpenTelemetry
* Config 관리: ConfigMap/Secrets + Vault

### 3) 공통 모듈 제거

모놀리식에서 공통 유틸, DTO 를 그대로 쓰면 MSA 실패.

* 공통 모듈은 **라이브러리화** or **폴리시화**
* 서비스 간 공유 모델 금지 (명세로만 공유)

## 4.4 도메인 분리 실행 단계

### 1) 최초 서비스 추출

먼저 분리하기 쉬운 서비스 하나를 추출한다:

* Notification Service
* User/Auth Service
* Catalog/Inventory Service

실제로 가장 많이 첫 타로 추출되는 것은:

* **Auth** (JWT 발급)
* **Notification** (독립적이고 변경이 많음)
* **Order** (초기 수요가 많고 확장 필요)

### 2) 서비스 간 API 계약 정의

* OpenAPI/Swagger 기반 계약 우선
* Consumer Driven Contract Test 적용 가능 (Pact 등)

### 3) 이벤트 발행 규칙 정의

* 이벤트 명명 규칙
* Stream/Topic 구조 (Kafka)
* Payload 스키마(schema registry)

## 4.5 데이터 정합성 및 트랜잭션 패턴 도입

### 1) 분산 트랜잭션 대체 패턴

* **Saga Orchestration / Choreography**
* **Outbox & CDC** (Debezium 포함 가능)
* **Idempotency** 설계 필수

### 2) 조회 일관성 처리

MSA 최대 난제: 데이터를 조인할 수 없음
해결책:

* Composite API
* Materialized View (별도 조회용 DB 구축)
* CQRS 패턴
* Read Model Rebuild

## 4.6 운영/관측 구조 강화

### 1) Observability 3종 구축

* Logs: ELK
* Metrics: Prometheus
* Tracing: Jaeger / OpenTelemetry

MSA에서 Log/Trace 없으면 **디버깅 불가**.

### 2) 장애 복구 체계

서비스별:

* Circuit Breaker
* Retry
* Timeout
* Bulkhead (격리 정책)

## 4.7 조직 및 프로세스 변화

### 1) 팀 구조 변화

MSA의 핵심은 조직이다.

* 도메인별 스쿼드로 운영
* 각 팀이 **개발-테스트-배포**까지 책임

### 2) 문서와 표준화 체계

* API Contract
* Event Schema
* Service Documentation
* Alert/Monitoring Guideline

## 4.8 점진적 마이그레이션 전략

### 1) Strangler Pattern

모놀리스를 완전히 드러내며 대체하는 방식:

* 기존 서비스 앞단에 Gateway 설치
* 특정 기능만 MSA 서비스로 대체
* 점진적으로 기능을 이동
* 최종적으로 모놀리식 제거

### 2) Feature Toggle 방식

신규 기능을 MSA 기반으로 개발하고, 점차 모놀리식 의존도 제거.

## 4.9 실무에서 실패하는 원인과 예방

### 실패 원인

* 서비스 쪼갠 기준이 잘못됨
* 트랜잭션 관리 실패
* 성능 이슈 (네트워크 오버헤드)
* 너무 많은 서비스로 복잡도 증가
* 팀 역량/운영 인프라 부족

### 예방

* 최소 기능부터 쪼갠다
* 도메인 모델을 명확히 재정의
* 비동기 이벤트를 표준화
* 관측 가능성(Observability) 필수 도입
* 데이터는 절대 공유하지 않는다

## 4.10 실제로 “해야 하는 항목 체크리스트”

### 기술 체크리스트

* [ ] API Gateway 도입
* [ ] 서비스 통신 방식 결정(gRPC/REST/Kafka)
* [ ] Outbox 패턴 적용
* [ ] Saga(또는 보상 트랜잭션) 적용
* [ ] Kubernetes 배포 파이프라인 구축
* [ ] Config 분리 및 Secret 관리
* [ ] Observability 구축

### 도메인 체크리스트

* [ ] 도메인 분류 및 Bounded Context 도출
* [ ] Context Map 작성
* [ ] 서비스별 DB 분리
* [ ] 새 서비스 1개 이상 추출
* [ ] API Contract 확립
* [ ] 이벤트 스키마 확립
