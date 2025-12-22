# 카프카 컨슈머 운영 가이드

## 좋은 옵션들
| 목적 | 주요 옵션 | 설명 |
| --- | --- | --- |
| 처리 안정성 | `enable.auto.commit=false`, `max.poll.interval.ms`, `max.poll.records` | 수동 커밋으로 정확성 제어, 폴링 간격과 배치 크기를 조정해 처리 시간을 확보한다. |
| 재시도 제어 | `retry.backoff.ms`, `fetch.max.wait.ms`, `fetch.min.bytes` | 브로커 응답 지연 시 불필요한 재시도를 줄이고 배치 효율을 높인다. |
| 순서 보존 | `isolation.level=read_committed` | 트랜잭션 프로듀서와 연동 시 커밋된 메시지만 읽는다. |
| 장애 복구 | `session.timeout.ms`, `heartbeat.interval.ms` | 그룹 코디네이터가 컨슈머 장애를 감지하는 시간을 결정한다. |
| AZ 최적화 | `client.rack`, `partition.assignment.strategy=RackAwareAssignor` | 동일 가용영역 내 브로커를 우선 할당해 네트워크 비용 절감. |

- `fetch.min.bytes`를 크게 잡으면 일정량 이상 메시지가 쌓일 때만 배치로 가져와 CPU/네트워크 효율을 높일 수 있지만, 지연(latency)이 증가하므로 `fetch.max.wait.ms`와 함께 튜닝한다.
- `max.poll.records`는 한 번에 처리할 레코드 수이므로 CPU/메모리 워크로드에 맞게 설정한다. 대용량 메시지는 낮은 값, 경량 이벤트는 높은 값이 적합하다.

## 컨슈머 루프 시각화
```
┌──────────┐     ┌────────────┐     ┌─────────────┐     ┌──────────────┐
│ Poll()   │────►│ 처리 로직   │────►│ Commit Sync │────►│ Heartbeat    │
│ (fetch)  │     │ (DB/API)   │     │/Async       │     │ (백그라운드) │
└──────────┘     └────────────┘     └─────────────┘     └──────────────┘
      ▲                 │                    │                     │
      │                 └────────── pause()/resume() ──────────────┘
```
- `poll()` 스레드는 주기적으로 브로커에서 레코드를 가져온다. `max.poll.interval.ms` 내에 다시 호출되지 않으면 그룹 코디네이터가 해당 컨슈머를 죽은 것으로 판단한다.
- 비즈니스 로직이 오래 걸릴 경우 워커 스레드 풀을 두고, `poll()` 스레드는 빠르게 `pause()`/`resume()`으로 워커 큐 상태를 제어한다.
- 커밋은 처리 완료 후 호출되어야 하며, 백그라운드 하트비트 스레드가 그룹 세션을 유지한다.

## 폴링/리밸런싱/하트비트 전략
- `max.poll.interval.ms`보다 긴 시간을 처리에 사용하면 그룹에서 축출된다. 긴 처리 시간을 예상하면 `max.poll.records`를 줄이거나 백그라운드 스레드로 비즈니스 로직을 분리한다.
- `heartbeat.interval.ms`는 `session.timeout.ms`의 1/3 이하로 설정해 빠른 장애 감지를 보장한다.
- 리밸런싱이 잦을 경우 `partition.assignment.strategy`를 `CooperativeStickyAssignor`로 설정해 점진적 재할당을 사용하거나, `static.group.membership`을 통해 인스턴스를 고정한다.
- 리밸런스 이벤트 흐름:
  1. 컨슈머가 조인 요청(`JoinGroup`)을 보내고 리더가 파티션을 계산한다.
  2. `CooperativeStickyAssignor`는 최소 파티션만 이동하며, 기존 파티션은 `REVOKE` 단계 없이 `ADD` 단계에서 추가된다.
  3. 새 할당 후 각 컨슈머는 `syncGroup` 응답을 받고 `onPartitionsAssigned` 콜백에서 상태를 초기화한다.
- `max.poll.interval.ms`를 크게 늘려야 한다면 `poll()` 내에서 주기적으로 `consumer.pause(partitions)`를 호출해 할당을 유지한 채 처리 시간을 벌 수 있다.

## 좋은 모니터링
- **컨슈머 랙**: `kafka.consumer:type=consumer-fetch-manager-metrics, client-id=...`의 `records-lag`, `records-lag-max`.
- **리밸런스 이벤트**: `kafka.coordinator.group:type=consumer-metrics`의 `rebalance-latency-avg`.
- **폴링 주기**: 애플리케이션 로그에 `poll()` 시작/종료 시간을 기록하거나 마이크로미터 지표를 추가한다.
- **처리 실패율**: DLQ 전송 건수, 예외 타입, 재시도 횟수를 알림에 연결한다.
- **하트비트 지연**: `heartbeat-response-time-max` 지표를 추적해 네트워크 문제를 조기 파악한다.
- **스레드 풀 상태**: 비즈니스 처리 워커 큐 길이를 메트릭으로 노출해 밀림을 감지하고 `pause()`로 backpressure를 건다.

## 운영 팁
- 소비가 지연되는 파티션은 lag가 높은 순으로 재조정하거나, 동일 그룹 내 컨슈머 인스턴스를 늘린다.
- `pause() / resume()` API로 느린 다운스트림 처리량을 조절해 리밸런스 없이 백프레셔를 건다.
- 컨슈머 장애 시 `client.rack`를 활용해 동일 AZ 내 브로커에서 데이터를 우선 소비하도록 구성하면 네트워크 비용을 줄일 수 있다.
- 장기 랙 해소를 위해 특정 파티션을 임시로 새로운 컨슈머 그룹으로 할당해 병렬 catch-up을 수행한 뒤, 정상 그룹으로 되돌릴 수 있다.
- 대형 메시지를 처리할 때는 `max.partition.fetch.bytes`와 `fetch.max.bytes`를 충분히 키우되, JVM 힙 사용량을 모니터링해 OOM을 방지한다.

## 실제 운영 시나리오
1. 결제 정산 서비스는 파티션당 최대 `max.poll.records=50`으로 설정하고, 워커 스레드 10개로 병렬 처리한다.
2. 특정 외부 정산 API가 느려지면 워커 큐가 포화되어 처리 속도가 떨어진다. 이때 컨슈머는 `pause()`로 추가 fetch를 잠시 멈춰 다운스트림을 보호한다.
3. 큐가 비기 시작하면 `resume()`을 호출해 다시 소비를 재개한다. 이 과정에서 `records-lag-max` 지표가 임계치를 넘으면 PagerDuty 알림으로 대응한다.
4. 만약 컨슈머 인스턴스 하나가 OOM으로 죽어 그룹 리밸런스가 발생했을 때, `static.group.membership` 덕분에 나머지 인스턴스의 파티션 이동이 최소화되어 처리 지연을 줄인다.
