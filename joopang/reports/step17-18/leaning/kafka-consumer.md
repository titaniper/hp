# 카프카 컨슈머 운영 가이드

## 좋은 옵션들
| 목적 | 주요 옵션 | 설명 |
| --- | --- | --- |
| 처리 안정성 | `enable.auto.commit=false`, `max.poll.interval.ms`, `max.poll.records` | 수동 커밋으로 정확성 제어, 폴링 간격과 배치 크기를 조정해 처리 시간을 확보한다. |
| 재시도 제어 | `retry.backoff.ms`, `fetch.max.wait.ms`, `fetch.min.bytes` | 브로커 응답 지연 시 불필요한 재시도를 줄이고 배치 효율을 높인다. |
| 순서 보존 | `isolation.level=read_committed` | 트랜잭션 프로듀서와 연동 시 커밋된 메시지만 읽는다. |
| 장애 복구 | `session.timeout.ms`, `heartbeat.interval.ms` | 그룹 코디네이터가 컨슈머 장애를 감지하는 시간을 결정한다. |

## 폴링/리밸런싱/하트비트 전략
- `max.poll.interval.ms`보다 긴 시간을 처리에 사용하면 그룹에서 축출된다. 긴 처리 시간을 예상하면 `max.poll.records`를 줄이거나 백그라운드 스레드로 비즈니스 로직을 분리한다.
- `heartbeat.interval.ms`는 `session.timeout.ms`의 1/3 이하로 설정해 빠른 장애 감지를 보장한다.
- 리밸런싱이 잦을 경우 `partition.assignment.strategy`를 `CooperativeStickyAssignor`로 설정해 점진적 재할당을 사용하거나, `static.group.membership`을 통해 인스턴스를 고정한다.

## 좋은 모니터링
- **컨슈머 랙**: `kafka.consumer:type=consumer-fetch-manager-metrics, client-id=...`의 `records-lag`, `records-lag-max`.
- **리밸런스 이벤트**: `kafka.coordinator.group:type=consumer-metrics`의 `rebalance-latency-avg`.
- **폴링 주기**: 애플리케이션 로그에 `poll()` 시작/종료 시간을 기록하거나 마이크로미터 지표를 추가한다.
- **처리 실패율**: DLQ 전송 건수, 예외 타입, 재시도 횟수를 알림에 연결한다.

## 운영 팁
- 소비가 지연되는 파티션은 lag가 높은 순으로 재조정하거나, 동일 그룹 내 컨슈머 인스턴스를 늘린다.
- `pause() / resume()` API로 느린 다운스트림 처리량을 조절해 리밸런스 없이 백프레셔를 건다.
- 컨슈머 장애 시 `client.rack`를 활용해 동일 AZ 내 브로커에서 데이터를 우선 소비하도록 구성하면 네트워크 비용을 줄일 수 있다.
