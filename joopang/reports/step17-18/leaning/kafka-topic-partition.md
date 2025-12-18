# 토픽과 파티션 설계 가이드

## 토픽 설계 원칙
- 비즈니스 도메인이나 SLA별로 토픽을 분리해 보존 정책, 보안 정책을 개별 조정한다.
- `cleanup.policy`를 `delete` 또는 `compact`로 설정해 로그 삭제 또는 키 기반 최신 상태 보존을 선택한다.
- 토픽 자동 생성은 비활성화하고, 인프라 코드로 토픽 스펙을 선언적으로 관리한다.

## 파티션 전략
- 파티션 수는 목표 처리량, 메시지 크기, 컨슈머 병렬성에 기반해 산정한다. 일반적으로 `Throughput / (BrokerDiskMBps * Headroom)` 방식으로 역산한다.
- 복제 팩터는 최소 3을 권장하며, `min.insync.replicas`는 2 이상으로 설정해 리더 장애 시에도 데이터 손실을 방지한다.
- 파티션 재할당 시 `kafka-reassign-partitions` 또는 Cruise Control을 사용해 균형 잡힌 리더/팔로워 배치를 만든다.

## 컨슈머 lag이 계속 쌓이는 경우
1. **핫 파티션**: 특정 키로 트래픽이 몰리면 한 파티션만 backlog가 증가한다. 파티션 라우팅 키를 재검토하거나 파티션 수를 늘려 균등화한다.
2. **컨슈머 처리 병목**: 애플리케이션 로직 지연, 외부 API 호출, DB lock 등을 프로파일링하고, `max.poll.records`를 줄여 단위 처리를 줄인다.
3. **재조정 지연**: 잦은 리밸런스로 인한 stop-the-world. Cooperative Sticky 할당자 사용, `session.timeout.ms` 조정으로 안정화.
4. **브로커 스로틀**: 브로커 I/O 포화로 fetch 응답이 느려지는 경우, 브로커 증설이나 QoS 제어로 해결.

## Compaction 예시
- 주문 상태 토픽처럼 키별 최신 상태만 필요하면 `cleanup.policy=compact`로 설정한다.
- compaction 토픽에선 동일 키의 가장 최신 레코드만 유지되므로, tombstone 메시지를 써서 키 삭제를 명시해야 한다.
- compaction은 백그라운드에서 수행되어 CPU/I/O를 사용하므로, 야간 시간대나 별도 브로커 tier에 배치하는 것이 좋다.

## 운영 체크리스트
- 토픽 생성 시 `retention.ms`, `retention.bytes`, `segment.bytes`, `message.format.version`을 명시한다.
- 파티션 수 조정 후 컨슈머 코드를 점검해 파티션 증가에 따른 재밸런스 처리가 이뤄지는지 확인한다.
- Lag 모니터링을 토픽/파티션/컨슈머 그룹 단위로 대시보드화하고, 임계치 초과 시 알림과 자동 스케일링을 연동한다.
