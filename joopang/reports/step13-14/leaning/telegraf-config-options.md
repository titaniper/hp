# Telegraf 설정 옵션 설명

Telegraf는 InfluxData가 만든 경량 에이전트로, 다양한 입력 플러그인으로 메트릭을 수집해 InfluxDB나 다른 TSDB로 전송할 수 있다. 여기서는 Docker/MySQL 지표를 모아 InfluxDB 2.x로 보내는 구성을 다룬다.

대상 파일: `docker/telegraf/telegraf.conf`. 주석이 없는 최소 설정이라, 각 옵션의 역할을 아래에 정리했다.

## [agent]
- `interval = "15s"` → 모든 입력 플러그인이 15초 간격으로 수집한다.
- `round_interval = true` → 수집 시간을 15초 배수에 맞춰 라운딩해 각 에이전트가 같은 타이밍에 데이터 수집.
- `collection_jitter = "5s"` → 실제 수집 시점을 최대 5초 랜덤하게 지연시켜 스파이크를 완화.
- `metric_batch_size = 1000` → 출력으로 전송할 때 한 번에 보낼 지표 수.
- `metric_buffer_limit = 10000` → 출력이 지연될 때 버퍼링 가능한 최대 지표 수. 초과 시 가장 오래된 데이터가 삭제된다.
- `flush_interval = "15s"` → 출력 플러그인이 15초 간격으로 버퍼를 비운다.
- `flush_jitter = "5s"` → 플러시 시간도 최대 5초 랜덤 지연으로 분산.
- `hostname = "joopang-telegraf"` → 호스트 라벨 값. 동일 컨테이너가 어디서 수집했는지 구분하기 위한 논리 이름이다.

## [[outputs.influxdb_v2]]
- `urls = ["http://influxdb:8086"]` → InfluxDB 2.x API 엔드포인트. Docker compose 네트워크의 서비스 이름을 사용한다.
- `token = "$INFLUX_TOKEN"` → InfluxDB 인증 토큰. 컨테이너 환경 변수에서 주입된다.
- `organization = "$INFLUX_ORG"` → 토큰이 속한 조직 이름.
- `bucket = "$INFLUX_BUCKET"` → 시계열 데이터가 들어갈 버킷.

## [[inputs.docker]]
- `endpoint = "unix:///var/run/docker.sock"` → Docker API 소켓 경로. 호스트 소켓을 바인드 마운트해야 접근 가능하다.
- `gather_services = false` → Docker Swarm 서비스 통계를 수집하지 않는다.
- `perdevice_include = ["cpu", "blkio", "network", "memory"]` → 컨테이너별로 수집할 메트릭 종류.
- `total_include = ["cpu", "blkio", "network", "memory"]` → 호스트 전체 합계를 계산할 메트릭 종류.
- `timeout = "5s"` → Docker API 호출 타임아웃.
- `docker_label_include = []` → 라벨 필터링이 비어 있으므로 모든 컨테이너의 라벨 정보를 포함하지 않는다.

## [[inputs.mysql]]
- `servers = ["$TELEGRAF_MYSQL_DSN"]` → 수집 대상 MySQL DSN 문자열. 예) `telegraf:metrics@tcp(mysql:3306)/`.
- `metric_version = 2` → 출력 스키마 버전. v2는 측정 이름/라벨 구성이 개선된 최신 버전이다.
- `gather_process_list = true` → `SHOW PROCESSLIST` 결과를 메트릭으로 수집하여 슬로우 쿼리 감시.
- `gather_info_schema_auto_inc = true` → informationschema에서 AUTO_INCREMENT 사용 현황을 가져와 용량 관리.
- `perf_events_statements = true` → Performance Schema의 이벤트 스테이트먼트를 활성화해 쿼리 digest 분석 가능.
- `perf_events_statements_digest_text_limit = 120` → digest 텍스트 길이 제한.
- `perf_events_statements_limit = 200` → Perf Schema에서 가져올 이벤트 수 상한.
- `perf_events_statements_time_limit = 86400` → Perf Schema 스냅샷 기간(초). 86400초는 24시간.
- `table_schema_databases = ["joopang"]` → 특정 데이터베이스 스키마(`joopang`)만 모니터링해 불필요한 지표를 줄인다.
