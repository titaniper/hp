# 카프카 브로커 운영 가이드

## 핵심 개념
- 브로커는 토픽 파티션을 저장·복제하고, 리더 파티션에 대한 읽기/쓰기 요청을 처리한다.
- KRaft 또는 ZooKeeper를 통해 컨트롤러가 선출되어 파티션 리더 선출, 브로커 생존 감시, 메타데이터 배포를 담당한다.

## 추천 브로커 옵션
| 목적 | 주요 옵션 | 설명 |
| --- | --- | --- |
| 네트워크 처리 | `num.network.threads`, `socket.send.buffer.bytes`, `socket.receive.buffer.bytes` | 대역폭과 동시 연결 수에 맞춰 조정해 큐 적체를 방지한다. |
| I/O 처리 | `num.io.threads`, `log.flush.interval.messages`, `log.flush.interval.ms` | 파티션 수와 디스크 성능을 고려해 로그 플러시 빈도를 최적화한다. |
| 저장소 관리 | `log.segment.bytes`, `log.retention.hours`, `log.retention.bytes` | 세그먼트 크기와 보존 정책으로 디스크 압력을 제어한다. |
| 복제 신뢰성 | `replica.lag.time.max.ms`, `unclean.leader.election.enable=false` | ISR 이탈 감지 및 데이터 손실 방지 전략을 강화한다. |
| 보안 | `ssl.keystore.location`, `sasl.enabled.mechanisms`, `authorizer.class.name` | TLS, SASL, ACL을 사용해 접근을 제한한다. |

## 모니터링 포인트
- **리더/팔로워 상태**: `ActiveControllerCount`, `OfflinePartitionsCount`로 리더 전환 여부 확인.
- **리플리케이션 지연**: `ReplicaFetcherManager.MaxLag` 값이 커지면 네트워크/디스크 병목을 의심.
- **디스크 및 네트워크 사용량**: 브로커별 스토리지 사용률, OS 레벨 I/O wait 추세를 추적.
- **요청 지연**: `RequestMetrics`의 `RequestQueueTimeMs`, `ResponseQueueTimeMs`를 수집해 카프카 내부 큐 적체를 측정.
- **GC 및 JVM**: `kafka.server:name=G1-Old-Generation,type=GarbageCollector` 등 JMX 지표로 stop-the-world 영향을 파악.

## 운영 팁
- 브로커를 롤링 재시작할 때 파티션 리더를 사전에 이동하거나 `leader.imbalance.check.interval.seconds`를 줄여 자동 균형을 맞춘다.
- 토픽/파티션 증가에 대비해 브로커 수를 선제적으로 늘리고, `auto.create.topics.enable=false`로 무분별한 토픽 생성을 막는다.
- Tiered Storage 또는 JBOD를 사용할 경우, 브로커별 디스크 용량 편차를 모니터링해 균등 재할당을 수행한다.
