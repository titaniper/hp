# Loki 설정 옵션 설명

Grafana Loki는 Promtail 등이 전송한 로그를 저비용으로 저장·검색할 수 있게 만든 수평 확장 로그 수집기이며, LogQL 질의로 Grafana 대시보드에 시각화할 수 있다. 여기서는 docker-compose 단일 노드 구성을 기준으로 옵션을 설명한다.

`docker/loki/loki-config.yml` 파일에 정의된 모든 옵션과 값의 의미를 정리했다. 각 섹션은 Loki 구성 요소와 1:1 로 매핑된다.

## auth_enabled
- 값: `false`
- 설명: 모든 HTTP 요청에 대한 인증을 비활성화한다. 로컬/테스트 환경에서 Grafana Loki를 빠르게 띄우기 위한 설정으로, 운영에서는 Reverse Proxy 등의 보호 장치를 반드시 두어야 한다.

## server
- `http_listen_port: 3100` → Loki HTTP API 포트.
- `grpc_listen_port: 9096` → gRPC 통신 포트(제어 plane 용).
- `log_level: warn` → 경고 이상 로그만 출력해 로그 양을 제한한다.

## ingester
- `lifecycler.address: 0.0.0.0` → Lifecycler가 바인딩할 주소. 단일 인스턴스라 모든 인터페이스를 사용한다.
- `lifecycler.ring.kvstore.store: inmemory` → 멤버십/토큰 링 정보를 메모리에 저장한다. 단일 인스턴스 구성이라 외부 KV 스토리지가 필요 없다.
- `lifecycler.ring.replication_factor: 1` → 데이터 복제본 개수. 단일 노드에서만 수집하므로 1로 둔다.
- `lifecycler.final_sleep: 0s` → 종료 직전 대기 시간. 바로 종료하도록 0초로 설정.
- `chunk_idle_period: 5m` → 동일 스트림에서 5분 동안 새 로그가 없으면 청크를 플러시한다.
- `chunk_retain_period: 1m` → 쓰기 완료 후 로컬 메모리에 1분 동안 청크를 유지해 재전송 대비.
- `wal.dir: /loki/wal` → Write-Ahead Log 저장 경로. 컨테이너 볼륨 `/loki/wal`을 사용한다.

## schema_config
- 유효 기간: `from: 2020-10-15` (v11 스키마를 해당 날짜 이후 모든 데이터에 적용).
- `store: boltdb-shipper` → 인덱스 스토어 엔진.
- `object_store: filesystem` → 실제 청크 저장소를 로컬 파일 시스템으로 지정.
- `schema: v11` → Loki 스키마 버전.
- `index.prefix: index_` → 인덱스 파일 프리픽스.
- `index.period: 24h` → 인덱스 롤오버 주기(24시간).

## storage_config
- `boltdb_shipper.active_index_directory: /loki/index` → 로컬 인덱스 파일 위치.
- `boltdb_shipper.cache_location: /loki/cache` → 인덱스 캐시 디렉터리.
- `boltdb_shipper.shared_store: filesystem` → 공유 저장소가 파일 시스템임을 명시.
- `filesystem.directory: /loki/chunks` → 청크 파일이 저장될 디렉터리.

## compactor
- `working_directory: /loki/compactor` → 압축 작업 임시 파일 경로.
- `retention_enabled: true` → 보관 정책(데이터 만료)을 활성화.
- `delete_request_cancel_period: 5m` → 삭제 요청 취소 허용 시간.

## limits_config
- `allow_structured_metadata: true` → 구조화된 메타데이터 라벨 사용 허용.
- `retention_period: 168h` → 기본 보관 기간 7일(168시간).
- `ingestion_burst_size_mb: 16` → 단일 스트림이 순간적으로 밀어 넣을 수 있는 최대 MB.
- `ingestion_rate_mb: 8` → 초당 허용되는 지속적 수집 속도.

## chunk_store_config
- `max_look_back_period: 0s` → 쿼리가 과거 데이터를 검색할 때 제한 없음(0초는 비활성화 의미).

## table_manager
- `retention_deletes_enabled: true` → retention_period에 따라 테이블 삭제를 수행.
- `retention_period: 168h` → 7일치 데이터만 유지하도록 관리.
