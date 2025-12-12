# Joopang 서비스 MSA 전환 보고서

작성일: 2025-12-12
작성자: Codex Agent

## 1. 개요
- 기존 모놀리식(Spring Boot 단일 애플리케이션)을 `common`, `order-service`, `coupon-service`, `gateway-service` 4개 Gradle 모듈로 분리했습니다.
- `common` 모듈은 값 객체, 공통 설정, 쿠폰/이벤트 계약 모델을 포함한 공유 라이브러리입니다.
- `order-service`, `coupon-service`는 각각 독립 실행 가능한 Spring Boot 애플리케이션이며, Kafka·Redis·MySQL 설정과 리소스를 자체적으로 가집니다.
- `gateway-service`는 Spring Cloud Gateway 기반 API 게이트웨이로 `/api/**` 트래픽을 수집해 서비스 라우팅, 공통 CORS/모니터링/기본 필터를 담당합니다.

## 2. 서비스 경계
| 서비스 | 책임 | 데이터 |
| --- | --- | --- |
| order-service | 주문 생성/결제, 재고 예약, 이벤트 발행 | `orders`, `order_items`, `users`, `products` 등 |
| coupon-service | 쿠폰 템플릿 관리, 발급/조회, 사용 확정 | `coupon_templates`, `coupons` 등 |
| common | Money, Quantity 등 값 타입 · 이벤트 Payload · 쿠폰 계약 타입 | 없음(라이브러리) |
| gateway-service | 외부 클라이언트 → 서비스 트래픽 집약, 경로 기반 라우팅, 공통 CORS/모니터링 헤더 주입 | 없음(Stateless Edge) |

### 쿠폰 연동
- `order-service`는 `CouponClient`를 통해 쿠폰 서비스와 통신하며, 운영 프로파일에서는 `KafkaCouponClient`가 `coupon-command`/`coupon-command-reply` 토픽을 이용해 **요청-응답 패턴**을 구현합니다.
- 테스트 프로파일은 `InMemoryCouponClient`로 대체해 의존성 없이 단위 테스트 가능.
- `coupon-service` 내부에는 `CouponOrderFacade` + `CouponCommandHandler`가 존재하며, Kafka 명령을 소비/응답합니다.

### 이벤트 전송
- 주문 결제 완료 시 `OrderPaidEvent`를 생성하여 Kafka 주제(`order-paid`)로 발행합니다.
- 쿠폰 검증/사용은 `CouponCommand`/`CouponCommandResult` 이벤트로 비동기 요청-응답을 수행합니다.
- `order-service`는 `KafkaOrderEventPublisher`(운영)와 `NoopOrderEventPublisher`(테스트)를 구현합니다.
- OpenTelemetry Collector + Zipkin 구성으로 추후 trace-id 기반 분산 추적을 수집할 수 있습니다.

### API Gateway 구성
- `gateway-service`는 Spring Cloud Gateway + WebFlux로 구현되어 있으며, `spring.cloud.gateway.routes`에서 경로별 라우팅을 선언형으로 제어합니다.
- 주문 계열 트래픽(`/api/orders`, `/api/carts`, `/api/products`, `/api/payments`, `/api/deliveries`, `/api/users`, `/api/sellers`, `/api/categories`)은 `ORDER_SERVICE_URI`(기본값 `http://localhost:8083`)로 전달하고, 쿠폰 계열(`/api/coupons/**`, `/api/users/*/coupons/**`)은 `COUPON_SERVICE_URI`(기본값 `http://localhost:8082`)로 전달합니다. 쿠폰 전용 라우트는 `order: -1` 처리해 `/api/users/{id}/coupons` 충돌을 예방했습니다.
- `gateway-service/src/main/resources/application.yml`은 공통 CORS 허용(`GATEWAY_ALLOWED_ORIGINS`), Header 중복 제거(`DedupeResponseHeader`), Netty HTTP Client 타임아웃, Prometheus/health 노출을 기본값으로 제공합니다.
- `gateway-service/Dockerfile`과 `docker-compose.yml`에 `gateway` 서비스를 추가해 `docker compose up gateway` 명령만으로 Edge를 띄울 수 있으며, Compose 환경에서는 `host.docker.internal`을 기본 URI로 사용해 로컬에서 구동 중인 order/coupon 인스턴스를 곧바로 프록시할 수 있습니다.

## 3. 데이터베이스 및 docker-compose
- `order-mysql`, `coupon-mysql` 두 개의 MySQL 컨테이너를 추가해 서비스별 DB를 완전히 분리했습니다. 각 서비스의 `application-local.yml`이 해당 DSN을 기본값으로 사용합니다.
- Kafka 스택(`zookeeper`, `kafka`, `kafka-ui`)을 docker-compose에 포함해 로컬에서도 이벤트 기반 통신을 검증할 수 있습니다.
- OpenTelemetry Collector(`otel-collector`)와 Zipkin을 구성해 OTLP 수집 → Zipkin 저장 → Grafana/Zipkin UI에서 추적을 확인할 수 있습니다.
- 기존 Loki/Promtail/Influx/Telegraf/Grafana 스택은 그대로 유지되며, Telegraf는 `order-mysql`을 기본 모니터링 대상으로 변경했습니다.

## 4. CI/CD 분리
- `.github/workflows/order-service-ci.yml`과 `coupon-service-ci.yml`을 추가해 서비스별 테스트 파이프라인을 독립적으로 실행하도록 구성했습니다.
- 공통 모듈이나 루트 설정이 바뀌더라도 해당 모듈 테스트만 수행하므로, 배포 승인 및 롤백 단위를 서비스별로 관리할 수 있습니다.

## 5. 사용자 검증
- `coupon-service`는 더 이상 `UserRepository`에 직접 접근하지 않습니다. 대신 `UserClient` 인터페이스 + `HttpUserClient`가 `/internal/users/{id}` 외부 API를 호출해 존재 여부만 확인합니다.
- 테스트 환경에서는 `StubUserClient`가 주입돼 외부 의존 없이 시나리오를 실행합니다.

## 6. 향후 보완 과제
1. **Gateway 인증·정책 강화**: Spring Cloud Gateway에 JWT/Session 인증, Rate-Limiter, Circuit Breaker(Resilience4j) 필터를 추가해 엣지 보안을 강화합니다.
2. **데이터 마이그레이션 자동화**: Flyway 스크립트를 서비스별 디렉터리에 재배치하고, CI 단계에서 모듈별 DB 마이그레이션을 수행하도록 조정합니다.
3. **쿠폰/주문 이벤트 고도화**: 현재 Kafka 요청-응답 패턴은 blocking 방식이므로, 장기적으로는 Saga 또는 CQRS 기반으로 전환해 완전한 비동기 처리를 지향합니다.
4. **관측성 확장**: OpenTelemetry Collector가 준비되었으므로, 애플리케이션에 OTLP exporter/trace ID 전파 필터를 추가하고 Grafana Tempo/Zipkin에 대시보드를 구성합니다.

## 7. 확인 및 한계
- Gradle 테스트 실행 시 macOS 보안 정책으로 wrapper가 `~/.gradle` 아래 `.lck` 파일을 만들지 못해 실패했습니다. 로컬에서 `chmod` 혹은 보안 예외를 설정한 뒤 재시도해야 합니다.
- 사용자 API, 주문/쿠폰 외 다른 도메인 서비스는 아직 모놀리식에 남아 있으므로, 추가 분해 시 동일한 패턴(계약 공유 + Kafka 통신)을 적용해야 합니다.

## 8. 요약
- 주문/쿠폰 책임을 각각 서비스로 나누고, `common` 모듈을 통한 계약/이벤트 공유를 완료했습니다.
- docker-compose는 서비스별 MySQL·Kafka·Kafka-UI·OpenTelemetry·Zipkin을 포함한 운영형 학습 환경으로 확장됐습니다.
- Kafka 기반 이벤트 통신과 UserClient를 도입해 서비스 간 결합도를 낮췄으며, CI/CD 파이프라인도 모듈별로 분리했습니다.
- Spring Cloud Gateway 기반 `gateway-service`를 추가해 `/api/**` 트래픽 진입점을 단일화하고 Compose로 손쉽게 기동할 수 있도록 했습니다.
