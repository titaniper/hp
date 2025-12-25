# Pinpoint APM 구성 가이드

Pinpoint는 분산 트랜잭션 추적(Distributed Tracing)과 호출 트리 분석을 제공하는 APM입니다. `docker-compose.yml`에 Pinpoint 스택을 추가했으며, Collector/Web/HBase를 한 번에 실행하고 각 Spring Boot 서비스에 Pinpoint 에이전트를 붙일 수 있습니다.

## 1. Pinpoint 스택 실행
```bash
docker compose up -d pinpoint-hbase pinpoint-collector pinpoint-web
```
- `pinpoint-hbase`: Pinpoint 메타데이터 저장소(HBase + Zookeeper). UI 포트 `16010`.
- `pinpoint-collector`: 애플리케이션에서 전송한 트레이스를 수집하며 TCP/UDP 9991~9996 포트를 노출합니다.
- `pinpoint-web`: Pinpoint UI (`http://localhost:8079`). Collector 및 HBase와 통신해 Call Tree, Scatter Chart 등을 제공합니다.

`docker compose ps pinpoint-web`으로 상태를 확인하고 `docker compose logs pinpoint-collector -f`로 Collector 로그를 살펴볼 수 있습니다.

## 2. Pinpoint 에이전트 준비
애플리케이션 프로세스에 Pinpoint Java Agent를 붙여야 Collector가 데이터를 받을 수 있습니다.

### 2.1 에이전트 바이너리 확보
가장 쉬운 방법은 공식 이미지를 사용해 로컬 디렉터리에 복사하는 것입니다.
```bash
mkdir -p docker/pinpoint/agent
# 컨테이너 안의 /pinpoint-agent를 호스트로 복사
container_id=$(docker create pinpointdocker/pinpoint-agent:2.5.3)
docker cp "$container_id:/pinpoint-agent" docker/pinpoint/
docker rm "$container_id"
```
> 이미지를 한 번만 내려받으면 되며, `docker/pinpoint/pinpoint-agent` 폴더에 `pinpoint-bootstrap.jar`, `lib/`, `profiles/` 등이 생성됩니다.

### 2.2 Collector IP/포트 설정
`docker/pinpoint/pinpoint-agent/profiles/release/pinpoint.config`에서 Collector 정보를 수정하세요.
```properties
profiler.collector.ip=pinpoint-collector
profiler.collector.tcp.port=9994
profiler.collector.stat.port=9995
profiler.collector.span.port=9996
```
로컬에서 직접 Collector 포트를 사용할 경우 `pinpoint-collector` 대신 `host.docker.internal` 또는 `127.0.0.1`을 넣으면 됩니다.

## 3. Spring Boot 서비스에 적용하기
### 3.1 로컬 실행(`./gradlew bootRun`)
```bash
export PINPOINT_APP_NAME=order-service
export PINPOINT_AGENT_ID=order-local-01
export JAVA_TOOL_OPTIONS="-javaagent:${PWD}/docker/pinpoint/pinpoint-agent/pinpoint-bootstrap.jar \
  -Dpinpoint.agentId=${PINPOINT_AGENT_ID} \
  -Dpinpoint.applicationName=${PINPOINT_APP_NAME} \
  -Dpinpoint.config=${PWD}/docker/pinpoint/pinpoint-agent/profiles/release/pinpoint.config"
./gradlew :order-service:bootRun
```
`PINPOINT_APP_NAME`은 Pinpoint UI에서 묶어서 보고 싶은 서비스 이름, `PINPOINT_AGENT_ID`는 개별 인스턴스 식별자입니다 (고유하게 유지).

### 3.2 Docker 컨테이너 실행
`JAVA_TOOL_OPTIONS`를 동일하게 주입하면 됩니다.
```bash
docker run --rm \
  -e JAVA_TOOL_OPTIONS="-javaagent:/pinpoint-agent/pinpoint-bootstrap.jar -Dpinpoint.agentId=gw-01 -Dpinpoint.applicationName=gateway-service -Dpinpoint.config=/pinpoint-agent/profiles/release/pinpoint.config" \
  -v $(pwd)/docker/pinpoint/pinpoint-agent:/pinpoint-agent \
  ghcr.io/<org>/joopang-gateway-service:local
```
`docker-compose.yml`의 `gateway`/`order`/`coupon` 서비스를 Pinpoint와 함께 돌리고 싶다면, `environment` 섹션에 `JAVA_TOOL_OPTIONS`를 추가하고 위와 같이 호스트 디렉터리를 마운트하세요.

## 4. UI에서 확인하기
1. `http://localhost:8079` 접속 → 기본 계정은 필요 없습니다.
2. 좌측 Application 드롭다운에서 `PINPOINT_APP_NAME`에 지정한 값을 선택.
3. Scatter / Call Tree에서 트랜잭션을 조회하고, 실시간 활성 스레드/에러율을 살펴볼 수 있습니다.

## 5. 운영 시 고려사항
- HBase는 디스크를 많이 사용하므로 `pinpoint-hbase-data` 볼륨이 있는 디스크 용량을 주기적으로 확인하세요.
- Collector는 UDP를 사용하므로 방화벽에 9995~9996 포트를 허용해야 합니다.
- Agent 설정(`pinpoint.config`)에서 샘플링 비율(`profiler.sampling.rate`), JVM/SQL 프로파일링 여부 등을 조정하면 트래픽이 많은 환경에서도 오버헤드를 제어할 수 있습니다.
- Pinpoint와 기존 관측 스택(Loki, InfluxDB, Zipkin 등)을 함께 사용해 이상 징후 발생 시 빠르게 근본 원인을 찾을 수 있습니다.

## 6. 문제 해결
- Collector 접속 실패: `docker compose logs pinpoint-collector -f`로 에러를 확인하고, Agent에서 설정한 Collector IP/포트가 일치하는지 검증합니다.
- UI에 애플리케이션이 보이지 않음: Agent가 시작되지 않았거나 `pinpoint.applicationName`이 다를 수 있습니다. JVM 파라미터 적용 여부(`ps -ef | grep javaagent`)도 확인하세요.
- HBase 초기화 지연: 첫 실행 시 1~2분 정도 걸릴 수 있으며, 준비 전에 Collector가 올라오면 반복 재시도가 발생할 수 있습니다. `docker compose up` 명령을 사용할 때 `--wait` 옵션을 주거나 HBase가 healthy한지 확인한 뒤 Collector/Web을 띄우세요.
