# 📡 Observability 구축 현황

## 1. 개요
- 모든 Spring Boot 서비스(`gateway-service`, `order-service`, `coupon-service`)가 `spring-boot-starter-actuator`를 의존하고 `/actuator/health`, `/actuator/metrics`, `/actuator/prometheus`를 노출한다. `gateway-service/src/main/resources/application.yml`에서 관제 엔드포인트를 기본 노출하고, 다른 서비스도 동일한 프로파일 구성(`application-*.yml`)을 공유한다.
- 로컬 및 Stage 환경은 `docker-compose.yml` 기반으로 **로그(Loki)**, **메트릭(InfluxDB + Telegraf)**, **Tracing(OpenTelemetry Collector + Zipkin)**, **APM(Pinpoint)**, **시각화(Grafana)**를 한 번에 기동하도록 구성돼 있다.
- Kubernetes 배포(`k8s/base`)는 Actuator 기반의 Liveness/Readiness 프로브를 사용하며, `docs/istio.md`가 설명하듯 Istio Telemetry → Loki/InfluxDB 스택으로 확장 연결할 수 있도록 설계됐다.

## 2. 로그 수집
### 2.1 애플리케이션 레벨
- `common/src/main/kotlin/io/joopang/common/monitoring/PerformanceLoggingAspect.kt`는 `@TrackPerformance` 애노테이션이 붙은 메서드의 실행 시간을 SLF4J 로그로 남긴다. 예: `order-service`의 `ProductService#getTopProducts()`는 `@TrackPerformance("getTopProducts")`로 태깅돼 캐시 효과를 비교 관찰한다.
- 모든 서비스가 `p6spy`를 적용(`application.yml`의 `decorator.datasource.p6spy.enable-logging`)하여 SQL 실행 시간을 로그에 남긴다. MySQL slow query log (`docker-compose`의 `--slow_query_log=1`)와 결합해 DB 병목을 빠르게 파악할 수 있다.

### 2.2 중앙 로그 파이프라인
- `docker/promtail/promtail-config.yml`: Promtail이 `/var/lib/docker/containers/*/*.log`를 tail 하면서 `job=container-logs`, `host=$HOSTNAME` 라벨을 붙여 Loki로 Push한다.
- `docker/loki/loki-config.yml`: 단일 노드 모드(Boltdb-shipper + filesystem)를 사용하며 7일(168h) 보존을 활성화했다. `retention_deletes_enabled: true`로 과도한 로그 누적을 방지한다.
- `docker/grafana/provisioning/datasources/datasource.yml`: Loki 데이터소스가 기본 탑재되고, `docker/grafana/provisioning/dashboards/loki-logs.json`을 통해 컨테이너별 로그를 바로 조회할 수 있다.

## 3. 메트릭 수집
### 3.1 서비스 메트릭
- Spring Actuator가 기본 지표를 노출하며, `gateway-service`는 `management.endpoints.web.exposure.include=health,info,metrics,prometheus`로 Prometheus 포맷을 제공한다 (`gateway-service/src/main/resources/application.yml:36`). 동일한 설정이 다른 서비스에도 적용될 수 있도록 공통 설정을 유지하고 있다.
- `k8s/base/deployment-*.yaml`에서 `/actuator/health/readiness`와 `/actuator/health/liveness` 프러브를 사용하여 K8s/ Istio 레이어에서도 상태를 즉시 확인한다.

### 3.2 인프라/DB 메트릭
- `docker/telegraf/telegraf.conf`: Telegraf가 Docker Host Metrics(`inputs.docker`)와 MySQL Performance Schema(`inputs.mysql`)를 15초 주기로 수집한다. 수집된 데이터는 `outputs.influxdb_v2`를 통해 `influxdb:8086`의 `joopang` 조직 → `telegraf` 버킷으로 적재된다.
- MySQL 컨테이너는 Slow Query Log, Performance Schema를 활성화(`docker-compose.yml`의 `--long_query_time=1`, `--performance-schema=ON` 관련 플래그)하여 Telegraf가 필요한 메트릭/이벤트를 읽을 수 있게 구성했다.

### 3.3 시각화
- Grafana(포트 `3000`, 기본 `admin/grafana123`)는 Loki/InfluxDB 데이터소스를 자동 등록한다. `docker/grafana/provisioning/dashboards/*`에는
  - `containers-overview.json`: CPU/메모리/네트워크 등 컨테이너 지표
  - `mysql-overview.json`: MySQL Slow Query, InnoDB Buffer, 연결 수
  - `loki-logs.json`: 서비스 로그 탐색
  대시보드가 포함돼 있어 기동 직후에도 기본 대시보드를 활용할 수 있다.

## 4. 트레이싱 & APM
- `docker/otel/otel-collector-config.yml`: OTLP(4317 gRPC / 4318 HTTP)로 받은 Trace/Metric을 Zipkin과 로깅 Exporter로 전달한다. Collector만 기동하면 애플리케이션에 OpenTelemetry SDK/Auto Instrumentation을 붙여 OTLP 전송만 활성화하면 된다.
- Zipkin(포트 `9411`)은 Collector에서 export된 스팬을 저장/조회한다. `reports/step15-16/reports/msa-migration.md`에 Collector ↔ Zipkin ↔ Grafana 사용 사례가 정리돼 있다.
- Pinpoint APM 스택(`pinpoint-hbase`, `pinpoint-collector`, `pinpoint-web`)을 함께 띄워 Java agent 기반의 상세 X-Ray를 확인할 수 있다. Collector 포트(`9991~9996`)와 Web UI(`8079`)가 `docker-compose.yml`에 정의돼 있다.

## 5. Kubernetes & Istio 연계
- `k8s/base/deployment-order-service.yaml` 등에서 Actuator 기반 프로브를 사용하여 Pod 상태를 SRE 도구와 연결한다. 동일한 `/actuator/health` 엔드포인트를 Istio Envoy(또는 HPA) 메트릭 소스로 사용할 수 있다.
- `docs/istio.md`는 Istio 설치 시 Prometheus/Grafana/Zipkin 애드온을 함께 설치한 뒤, Envoy Telemetry 출력을 `docker/`의 Loki/InfluxDB 스택과 연동하여 정책/텔레메트리 파이프라인을 확장하는 방법을 제안한다.

## 6. 컴포넌트 요약
| 구성요소 | 포트/접근 | 역할 | 관련 파일 |
| --- | --- | --- | --- |
| Grafana | `http://localhost:3000` (`admin/grafana123`) | Loki/InfluxDB 대시보드 뷰어 | `docker/grafana/provisioning/*` |
| Loki | `http://localhost:3100` | 중앙 로그 저장, 7일 보존 | `docker/loki/loki-config.yml` |
| Promtail | 내부 9080 | Docker 로그 → Loki Push | `docker/promtail/promtail-config.yml` |
| InfluxDB | `http://localhost:8086` (org `joopang`, bucket `telegraf`, token `joopang-influx-token`) | 시계열 지표 저장 | `docker-compose.yml`, `docker/telegraf/telegraf.conf` |
| Telegraf | Daemon (Host socket) | Docker/OS/MySQL 메트릭 수집 | `docker/telegraf/telegraf.conf` |
| OTEL Collector | gRPC 4317 / HTTP 4318 | OTLP 수집 후 Zipkin + 로그로 Export | `docker/otel/otel-collector-config.yml` |
| Zipkin | `http://localhost:9411` | 분산 추적 저장/조회 | `docker-compose.yml` |
| Pinpoint Web | `http://localhost:8079` | Java APM UI | `docker-compose.yml` |
| Actuator | 각 서비스 `:808x/actuator/*` | 헬스/메트릭/프로메테우스 노출 | `gateway-service/src/main/resources/application.yml`, `k8s/base/deployment-*.yaml` |

## 7. 운영 시 고려 사항
1. **수집기 동작 확인**: Loki, InfluxDB, Grafana 컨테이너가 모두 Healthy인지 `docker ps` 및 Grafana `Status` 패널에서 점검한다.
2. **애플리케이션 계측**: OTLP Exporter(예: `opentelemetry-javaagent.jar` 또는 Micrometer Tracing + OTLP Exporter)를 JVM 옵션에 추가하면 Collector → Zipkin → Grafana Tempo 등으로 손쉽게 확장 가능하다.
3. **알람 연계**: Grafana Contact Point나 Alertmanager를 붙여 Loki LogQL/Influx Flux 쿼리에 대한 임계치 기반 Alerting을 구성한다. 현재 문서는 수집/시각화까지 완료된 상태이며, Alerting은 TODO이다.
