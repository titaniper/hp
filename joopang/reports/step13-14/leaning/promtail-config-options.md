# Promtail 설정 옵션 설명

Promtail은 로컬 파일·시스템 로그를 tail 해서 Loki로 전송하는 사이드카/에이전트다. Docker 컨테이너 로그를 자동으로 파싱해 라벨링하는 구성을 기준으로 옵션을 정리했다.

`docker/promtail/promtail-config.yml` 내용을 기반으로 각 항목의 의미를 정리했다.

## server
- `http_listen_port: 9080` → Promtail 자체 상태/메트릭 HTTP 엔드포인트.
- `grpc_listen_port: 0` → gRPC 리스너 비활성화(0은 사용 안 함).
- `log_level: warn` → 경고 이상의 로그만 유지해 노이즈를 줄인다.

## positions
- `filename: /tmp/positions.yaml` → tailing 위치를 저장하는 파일 경로. 컨테이너 재시작 후에도 이어서 읽을 수 있도록 volume 으로 매핑한다.

## clients
- `url: http://loki:3100/loki/api/v1/push` → Loki HTTP Push API 주소. Docker 네트워크 내 `loki` 서비스로 전송한다.

## scrape_configs
### job_name: docker
- `pipeline_stages`:
  - `docker: {}` → Docker JSON 로그를 `log`, `stream`, `attrs` 필드로 파싱해 라벨화한다.
- `static_configs`:
  - `targets: ["localhost"]` → Promtail 자체 메트릭용 더미 타겟. Loki에는 사용되지 않는다.
  - `labels.job: container-logs` → 로그 스트림에 붙는 고정 라벨.
  - `labels.host: ${HOSTNAME:-docker-host}` → 컨테이너 환경 변수 `HOSTNAME`이 있으면 사용하고, 없으면 `docker-host` 기본값을 사용한다.
  - `labels.__path__: /var/lib/docker/containers/*/*.log` → tail 할 파일 경로 패턴. Docker 컨테이너 json 로그 디렉터리를 직접 읽는다.
