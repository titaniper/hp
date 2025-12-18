# 카프카 브로커 운영 가이드

## 핵심 개념
- 브로커는 토픽 파티션을 저장·복제하고, 리더 파티션에 대한 읽기/쓰기 요청을 처리한다.
- KRaft 또는 ZooKeeper를 통해 컨트롤러가 선출되어 파티션 리더 선출, 브로커 생존 감시, 메타데이터 배포를 담당한다.
- 브로커 프로세스는 크게 네트워크 스레드(accept/read/write), I/O 스레드(파일 쓰기/읽기), 복제 fetcher 스레드로 구성된다. 각 스레드 풀을 워크로드에 맞게 조정해야 큐 적체를 방지할 수 있다.
- 컨트롤러는 `controller.quorum.voters`(KRaft) 또는 ZooKeeper ensemble로 구성되며, Active Controller가 단 1개여야 한다. ActiveControllerCount>1이면 split-brain 징후다.

## 아키텍처 시각화
```
┌───────────────┐        ┌───────────────┐
│  Broker 1     │        │  Broker 2     │
│  ┌──────────┐ │        │  ┌──────────┐ │
│  │ P0 (Leader)│<──rep──►│  │ P0 (Follower)│
│  ├──────────┤ │        │  ├──────────┤ │
│  │ P1 (Follower)│◄─rep──│  │ P1 (Leader) │
│  └──────────┘ │        │  └──────────┘ │
└──────┬────────┘        └──────┬────────┘
       │                          │
       ▼                          ▼
     Clients ──read/write──► Leader partitions only

Controller (KRaft/ZK)
└─ 선출된 브로커가 메타데이터를 관리하고 리더 전환을 조정한다.
```
- 리더 파티션에서만 읽기/쓰기가 이루어지고, 팔로워는 리더에서 데이터를 fetch 하여 ISR을 유지한다.
- 리더 장애 시 컨트롤러가 ISR 중 하나를 새 리더로 승격하며, `unclean.leader.election.enable=false`일 때 ISR 외 브로커는 승격되지 않으므로 데이터 손실을 막는다.

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
- **Controller 이벤트**: `ControllerChangeRateAndTimeMs`가 급증하면 컨트롤러 플랩(flapping) 가능성을 확인한다.
- **Throttle 상태**: 재할당/리스토어 시 `leader.throttled.replicas`, `follower.throttled.replicas` 설정을 모니터링해 지나친 스로틀로 복구가 지연되지 않도록 한다.

## 운영 팁
- 브로커를 롤링 재시작할 때 파티션 리더를 사전에 이동하거나 `leader.imbalance.check.interval.seconds`를 줄여 자동 균형을 맞춘다.
- 토픽/파티션 증가에 대비해 브로커 수를 선제적으로 늘리고, `auto.create.topics.enable=false`로 무분별한 토픽 생성을 막는다.
- Tiered Storage 또는 JBOD를 사용할 경우, 브로커별 디스크 용량 편차를 모니터링해 균등 재할당을 수행한다.
- 디스크 장애 시 절차:
  1. 해당 브로커를 `controlled shutdown`으로 내려 리더를 이전한다.
  2. JBOD라면 장애 디스크 파티션만 재할당하고, RAID라면 전체 브로커 교체를 고려한다.
  3. 재가동 후 `kafka-preferred-replica-election` 또는 Cruise Control로 리더 균형을 맞춘다.
- 특정 브로커에 파티션이 몰릴 때는 Cruise Control `rebalance` 작업을 사용하고, 재할당 중에는 `replica.fetch.max.bytes` 등을 튜닝해 복구 속도를 높인다.
- KRaft 전환 시 `kraft.topic.replication.factor`를 3 이상으로 설정하고, 컨트롤 플레인과 데이터 플레인 포트를 분리하면 장애 범위를 줄일 수 있다.

## 실제 운영 시나리오
1. 파티션 300개, replica factor 3인 클러스터에서 브로커 한 대의 디스크가 I/O Wait 40% 이상으로 상승해 `ReplicaFetcherManager.MaxLag`가 증가한다.
2. 운영자는 문제 브로커를 `decommission` 태그로 지정해 Cruise Control이 파티션을 다른 브로커로 이동하도록 한다.
3. 이동 중에는 `net.throttle`을 설정해 다른 워크로드에 영향을 최소화하고, `OfflinePartitionsCount`가 0인지 지속 확인한다.
4. 디스크 교체 후 브로커를 복귀시키고 `preferred leader election`을 수행해 리더 분포를 균형 있게 맞춘다.
