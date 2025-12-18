# 토픽과 파티션 설계 가이드

## 토픽 설계 원칙
- 비즈니스 도메인이나 SLA별로 토픽을 분리해 보존 정책, 보안 정책을 개별 조정한다.
- `cleanup.policy`를 `delete` 또는 `compact`로 설정해 로그 삭제 또는 키 기반 최신 상태 보존을 선택한다.
- 토픽 자동 생성은 비활성화하고, 인프라 코드로 토픽 스펙을 선언적으로 관리한다.
- 데이터 민감도에 따라 `principal.topic.acl`을 다르게 적용해 읽기/쓰기 권한을 최소화한다.
- `segment.bytes`는 보존 기간 × 처리량에 맞게 산정한다. 작은 세그먼트는 파일 수를 늘려 운영이 복잡해지고, 너무 크면 compaction/삭제 주기가 느려진다.
- 토픽 명명 규칙 예: `{도메인}.{이벤트}.{env}` (`orders.created.prod`). 이를 기준으로 Terraform/CDK에서 선언적으로 생성한다.

## 파티션 전략
- 파티션 수는 목표 처리량, 메시지 크기, 컨슈머 병렬성에 기반해 산정한다. 일반적으로 `Throughput / (BrokerDiskMBps * Headroom)` 방식으로 역산한다.
- 복제 팩터는 최소 3을 권장하며, `min.insync.replicas`는 2 이상으로 설정해 리더 장애 시에도 데이터 손실을 방지한다.
- 파티션 재할당 시 `kafka-reassign-partitions` 또는 Cruise Control을 사용해 균형 잡힌 리더/팔로워 배치를 만든다.
- 파티션 수 산정 예시:
  | 항목 | 값 |
  | --- | --- |
  | 목표 처리량 | 200 MB/s |
  | 브로커 I/O 처리량 | 50 MB/s |
  | 헤드룸 | 30% |
  | 계산 | `200 / (50 * 0.7) ≈ 5.7` → 최소 6개 파티션 |
- 컨슈머 병렬성 요구: 컨슈머 인스턴스를 12개까지 확장해야 한다면 파티션도 12개 이상이어야 한다. 파티션이 컨슈머 수보다 적으면 일부 인스턴트가 유휴 상태가 된다.
- 파티션 키 라우팅:
  - 해시 기반 `key.hashCode % partition`을 사용하면 균등 분포가 보장되지만, 제공하는 키에 따라 핫 파티션이 생길 수 있다.
  - 대용량 트래픽 키는 `random suffix` 또는 `bucketing`을 도입해 분산한다 (`userId#bucket`).

## 토픽/파티션 시각화
```
Topic: orders.created
├─ Partition 0 (Broker1) ──► Segment 000 → 001 → 002
├─ Partition 1 (Broker2) ──► Segment 000 → 001
└─ Partition 2 (Broker3) ──► Segment 000

Consumer Group A
├─ Instance A-1 ──► P0
├─ Instance A-2 ──► P1
└─ Instance A-3 ──► P2
```
- 파티션은 브로커 간에 분산되어 각 브로커의 I/O를 활용한다.
- 컨슈머 그룹 내에서 파티션 수와 인스턴스 수가 동일하면 병렬 처리가 극대화된다.

## 컨슈머 lag이 계속 쌓이는 경우
1. **핫 파티션**: 특정 키로 트래픽이 몰리면 한 파티션만 backlog가 증가한다. 파티션 라우팅 키를 재검토하거나 파티션 수를 늘려 균등화한다.
2. **컨슈머 처리 병목**: 애플리케이션 로직 지연, 외부 API 호출, DB lock 등을 프로파일링하고, `max.poll.records`를 줄여 단위 처리를 줄인다.
3. **재조정 지연**: 잦은 리밸런스로 인한 stop-the-world. Cooperative Sticky 할당자 사용, `session.timeout.ms` 조정으로 안정화.
4. **브로커 스로틀**: 브로커 I/O 포화로 fetch 응답이 느려지는 경우, 브로커 증설이나 QoS 제어로 해결.
- **Lag 대응 플로우**
  1. `records-lag-max` 지표로 어떤 파티션이 지연되는지 파악.
  2. 핫 파티션이라면 라우팅 키 변경 또는 임시 소비 그룹 분리.
  3. 컨슈머 병목이면 `pause()`로 증폭을 막고, 스케일 아웃 후 `resume()`.
  4. 브로커 병목이면 Cruise Control로 파티션 이동 또는 브로커 증설.

## Compaction 예시
- 주문 상태 토픽처럼 키별 최신 상태만 필요하면 `cleanup.policy=compact`로 설정한다.
- compaction 토픽에선 동일 키의 가장 최신 레코드만 유지되므로, tombstone 메시지를 써서 키 삭제를 명시해야 한다.
- compaction은 백그라운드에서 수행되어 CPU/I/O를 사용하므로, 야간 시간대나 별도 브로커 tier에 배치하는 것이 좋다.
- 시각화:
  ```
  Key=order-1: [PENDING] → [PAID] → [SHIPPED]
  Key=order-2: [PENDING] → [CANCELED] → tombstone

  Compaction 후:
  ├─ order-1 : [SHIPPED]
  └─ order-2 : tombstone (최종 삭제)
  ```
- compaction이 끝나기 전까지 이전 버전이 남아 있을 수 있으므로, 일정 기간 동안 tombstone을 유지하는 `delete.retention.ms`를 설정해 컨슈머가 삭제 이벤트를 처리할 수 있도록 한다.

## 운영 체크리스트
- 토픽 생성 시 `retention.ms`, `retention.bytes`, `segment.bytes`, `message.format.version`을 명시한다.
- 파티션 수 조정 후 컨슈머 코드를 점검해 파티션 증가에 따른 재밸런스 처리가 이뤄지는지 확인한다.
- Lag 모니터링을 토픽/파티션/컨슈머 그룹 단위로 대시보드화하고, 임계치 초과 시 알림과 자동 스케일링을 연동한다.
- 토픽 변경(예: compaction 활성화) 전에 `describeConfigs` 출력과 GitOps 선언 상태가 일치하는지 검증한다.
- 각 토픽의 보존/ACL/compaction 정책을 정기 점검해, 사용되지 않는 토픽을 정리해 디스크를 확보한다.
- 파티션 늘리기 전 `key`에서 순서 의존성이 없는지 확인하고, 필요하면 `KTable` 엔티티 등 멱등 처리를 마련한다.
