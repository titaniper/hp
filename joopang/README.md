# Joopang Service

## Overview
- 실시간 재고 확인, 주문/결제, 선착순 쿠폰 발급을 다루는 학습용 이커머스 백엔드입니다.
- Kotlin + Spring Boot 기반의 모놀리식 애플리케이션으로, 도메인별 헥사고날 구조(`presentation → application → domain → infrastructure`)를 따릅니다.
- 각 도메인은 인메모리 어댑터를 제공하며, 요구사항 확장 시 외부 시스템으로 대체하도록 설계되었습니다.
- ./gradlew test

## Tech Stack
- Kotlin 1.9, JDK 17
- Spring Boot 3.2 (Web MVC, Validation, Springdoc OpenAPI)
- Gradle Kotlin DSL + Version Catalog
- Kotest, Spring Boot Test (준비된 의존성)

## Architecture Highlights
- `src/main/kotlin/io/joopang/services/<domain>` 구조로 도메인을 구분합니다.
- 공통 관심사는 `services/common` 아래에 위치하며, 전역 예외 처리(`presentation/ApiControllerAdvice`)와 표준 에러 포맷(`application/ErrorResponse`)을 제공합니다.
- 세부 아키텍처 정리는 [docs/architecture.md](docs/architecture.md)에 정리돼 있습니다.

## Domain Modules
- `services/order` : 주문 생성/결제, 외부 전송 페이로드 생성, 재고/쿠폰 연동.
- `services/product` : 상품 및 상품 옵션 관리, 재고 확인, 인기 상품 조회.
- `services/coupon` : 선착순 쿠폰 발급/조회, 락 매커니즘(`CouponLockManager`).
- `services/user`, `services/payment`, `services/category`, `services/seller`, `services/cart`, `services/delivery`, `services/metrics` : 주문/상품 도메인을 보조하는 엔티티 및 값 객체.
- 인메모리 구현체(`infrastructure`)는 예시용으로 제공되며, 실제 연동에 맞춰 확장 가능합니다.

## Getting Started
### Prerequisites
- JDK 17
- (선택) 로컬에서 Gradle 설치가 필요하지 않습니다. 래퍼(`./gradlew`)를 사용하세요.

### Build & Run
```bash
./gradlew bootRun
```

### Tests
```bash
./gradlew test
```
> `build.gradle.kts`에서 테스트 실패가 무시되도록(`ignoreFailures = true`) 설정돼 있으니, 실제 품질 검증 시 값을 조정하세요.

### Load Testing (k6)
- `k6/` 디렉터리에 smoke/load 시나리오, 공용 헬퍼, 환경별 설정이 준비돼 있습니다.
- 예시 실행: `K6_ENV=local k6 run k6/main.js` (기본 smoke), `SCENARIO=load K6_ENV=local k6 run k6/main.js`, `SCENARIO=rush K6_ENV=local k6 run k6/main.js`
- 자세한 구조와 데이터 준비 방법은 `k6/README.md`를 참고하세요.

### Test Profile (H2)
- `./gradlew test` 실행 시 자동으로 `test` 프로파일이 활성화되고, 인메모리 H2(`jdbc:h2:mem:joopang-test`)를 사용합니다.
- 로컬 애플리케이션은 기본값(로컬 MySQL)로 계속 실행되며, 다른 DB를 쓰고 싶다면 `SPRING_PROFILES_ACTIVE` 값을 직접 지정하세요.

## API Documentation
- Springdoc OpenAPI UI: `http://localhost:8080/swagger-ui/index.html` (기본 부트 실행 기준)
- OpenAPI/도메인 명세, ERD 등은 `docs/design` 디렉터리에 정리돼 있습니다.

## Additional Documentation
- [docs/architecture.md](docs/architecture.md) : 전체 아키텍처 요약 및 계층 구조.
- [docs/design/requirements.md](docs/design/requirements.md) : 요구사항 정리.
- [docs/design/api-specification.md](docs/design/api-specification.md) : API 설계 초안.
- [docs/design/erd.md](docs/design/erd.md) : 도메인 모델 ERD.
- [docs/design/tasks.md](docs/design/tasks.md), [docs/design/user-stories.md](docs/design/user-stories.md) : 학습/개발 계획 자료.

## Project Conventions
- Kotlin 파일은 도메인 중심 패키지 구조를 따릅니다 (`io.joopang.services.*`).
- 공통 DTO, 에러 코드, 유틸 등은 `services/common` 아래에서 공유합니다.
- 새로운 인프라 어댑터 추가 시 `infrastructure` 계층에 구현하고, `application` 계층에서 인터페이스로 주입받는 패턴을 유지하세요.

## Observability Stack
컨테이너 로그, MySQL 성능 지표(슬로우 쿼리 포함), Docker 리소스를 한 곳에서 볼 수 있도록 Loki + InfluxDB + Grafana 기반 스택을 추가했습니다.

- `docker-compose.yml`에 Loki, Promtail, InfluxDB 2.x, Telegraf, Grafana 서비스를 추가했습니다.
- `docker/loki/loki-config.yml`, `docker/promtail/promtail-config.yml`, `docker/telegraf/telegraf.conf`에 기본 설정이 들어 있으며 Grafana 프로비저닝(`docker/grafana/provisioning/datasources`)으로 데이터소스를 자동 등록합니다.
- `mysql` 컨테이너는 슬로우 쿼리를 TABLE 로깅으로 기록하도록 플래그가 켜져 있고(`--slow_query_log=1`, `--long_query_time=1` 등), `docker/mysql/init/01-telegraf-user.sql`에서 성능 지표 조회 전용 계정을 만듭니다.

### 실행 방법
```bash
docker compose up -d
```
- 처음 실행 시 InfluxDB(포트 `8086`), Grafana(포트 `3000`), Loki(포트 `3100`)가 동시에 올라옵니다.
- Grafana 기본 계정은 `admin` / `grafana123`입니다. 로그인 후 즉시 변경하세요.
- InfluxDB 초기 설정 값(`admin` / `admin123`, 토큰 `joopang-influx-token`, org `joopang`, bucket `telegraf`)도 운영환경에 맞게 교체하세요.

### 수집 경로
- **컨테이너 로그**: Promtail이 `/var/lib/docker/containers/*/*.log`를 tail 하면서 Docker 메타데이터를 함께 보내고, Loki(`http://localhost:3100`)에 적재합니다. Grafana에서 Explore → Loki 선택 후 `{job=\"container-logs\"}`로 조회하세요.
- **DB/슬로우 쿼리 메트릭**: Telegraf `mysql` input이 `performance_schema.events_statements_summary_by_digest` 데이터를 모읍니다. Grafana에서 InfluxDB 데이터소스를 선택하고 `from(bucket:\"telegraf\") |> range(start: -15m) |> filter(fn: (r) => r._measurement == \"mysql_digest\")` 등 Flux 쿼리로 상위/느린 쿼리를 볼 수 있습니다. 기본 쿼리 임계값(`--long_query_time=1`)은 `docker-compose.yml`에서 조정할 수 있습니다.
- **컨테이너 자원 사용량**: Telegraf `docker` input이 Docker API(`/var/run/docker.sock`)를 통해 CPU/메모리/네트워크를 수집합니다. Grafana에서 InfluxDB datasource + `docker_container_cpu`, `docker_container_mem` 시계열을 이용하거나 Dashboard를 직접 import하세요.

### 추가 구성 포인트
1. **보안 변수 분리**: 실서비스에서는 `.env.monitoring` 등을 만들어 Influx 토큰, Grafana admin 비밀번호, Telegraf DSN을 환경변수로 뺀 뒤 `docker compose --env-file .env.monitoring up`으로 실행하세요.
2. **대시보드**: Grafana Explore로 개별 시계열을 확인한 뒤 필요에 따라 공식 Loki / Docker / MySQL 대시보드를 import하면 됩니다.(예: `16120` - *Docker Monitoring*, `13679` - *Loki Logs*, `7362` - *MySQL Overview*).
3. **추가 알람**: Grafana Unified Alerting에서 Loki/Influx 쿼리를 기반으로 슬랙/메일 알람을 걸 수 있습니다. Loki 쿼리는 LogQL, Influx는 Flux를 그대로 사용합니다.
4. **호스트 권한**: Promtail과 Telegraf는 Docker 로그/소켓을 읽어야 하므로 맥/리눅스 모두에서 `docker` 그룹 권한이 있는 사용자로 `docker compose`를 실행해야 합니다. `telegraf` 컨테이너는 `user: "0:0"` + `privileged: true` + `/var/run/docker.sock` RW 마운트를 사용하니(`docker-compose.yml` 참고) 권한 관련 변경 이후엔 `docker compose up -d telegraf --force-recreate`로 재시작하세요. Docker Desktop 환경이라면 `/var/lib/docker/containers` 마운트는 자동으로 LinuxKit VM 경로를 바라보므로 별도 추가 작업이 필요 없습니다.

  1. 소켓 권한을 완화

     sudo chmod 666 /var/run/docker.sock
     # 또는 실제 파일 경로가 심볼릭 링크이므로
     sudo chmod 666 /Users/kang/.docker/run/docker.sock
     Docker Desktop을 재시작하면 권한이 원래대로 돌아갈 수 있으니, 재부팅 후에도 로그를 보고 다시 조정해 줘.
     Docker Desktop을 재시작하면 권한이 원래대로 돌아갈 수 있으니, 재부팅 후에도 로그를 보고 다시 조정해 줘.
  2. 혹은 소켓 소유자/그룹을 변경해서 컨테이너에서 사용하는 UID/GID가 접근할 수 있도록 만들어. 예를 들어,

     sudo chgrp wheel /Users/kang/.docker/run/docker.sock
     sudo chmod 660 /Users/kang/.docker/run/docker.sock

     그리고 Docker Desktop 설정에서 “File Sharing”에 /Users/kang/.docker/run이 포함되어 있는지도 확인해 줘.


     docker compose up -d --force-recreate telegraf