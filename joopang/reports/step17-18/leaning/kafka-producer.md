# 카프카 프로듀서 운영 가이드

## 좋은 옵션들
| 목적 | 주요 옵션 | 설명 |
| --- | --- | --- |
| 내구성 | `acks=all`, `min.insync.replicas>=2` | 복제본 다수의 확인을 받아 데이터 손실을 최소화한다. |
| 성능/지연 | `batch.size`, `linger.ms`, `compression.type` | 배치 크기와 대기 시간을 조절해 처리량과 지연 사이 균형 조정. |
| 중복 방지 | `enable.idempotence=true`, `max.in.flight.requests.per.connection<=5` | 멱등성을 보장하고 순서를 유지한다. |
| 재시도 | `retries`, `delivery.timeout.ms`, `retry.backoff.ms` | 브로커 장애 시 안전하게 재전송하고 무한 대기 상태를 피한다. |
| 보안 | `sasl.mechanism`, `ssl.truststore.location`, `security.protocol` | 인증·암호화로 메시지를 보호한다. |
| 버퍼/백프레셔 | `buffer.memory`, `max.block.ms`, `compression.type` | 버퍼 풀 고갈 시 애플리케이션 블록을 제어한다. |

- `delivery.timeout.ms`는 전송 시도+재시도+백오프 전체 시간의 상한이다. `retries`와 `linger.ms`를 늘리면 timeout도 비례해 늘려야 한다.
- `max.in.flight.requests.per.connection`을 멱등성 활성화 시 5 이하로 유지하지 않으면 재시도 중 순서가 바뀔 수 있다.

## 프로듀서 데이터 경로 시각화
```
Record Accumulate ──► Batch (per partition) ──► Compressor ──► Sender Thread
        │                                          │
        └─ buffer.memory pool                      └─ network client (HTTP-like I/O)
```
- `RecordAccumulator`가 파티션별로 배치를 만들고, `batch.size` 또는 `linger.ms` 조건이 충족되면 Sender Thread가 브로커로 전송한다.
- `buffer.memory` 풀은 모든 배치가 공유하며, 가득 차면 `max.block.ms` 동안 `send()`가 대기한다.
- 압축은 배치 단위로 적용되므로, 배치 크기와 메시지 패턴에 따라 압축률이 달라진다.

## 이벤트가 발행되지 않을 때 진단
1. **프로듀서 버퍼 적체**: `buffer.memory`가 가득 차면 `BufferExhaustedException`이 발생하므로 `max.block.ms`를 모니터링한다.
2. **배치 타임아웃**: `linger.ms`가 길고 트래픽이 낮으면 배치가 늦게 전송된다. 필요 시 `linger.ms`와 `batch.size`를 낮춰 즉시 전송.
3. **브로커 측 에러**: `NotEnoughReplicas`, `UnknownTopicOrPartition`와 같은 예외를 로깅하고, DLQ나 알림으로 전파한다.
4. **트랜잭션 동작**: `transaction.timeout.ms` 초과나 `transactional.id` 중복 시 커밋이 실패할 수 있으니 상태 스토어를 점검한다.
- **DNS/보안 문제**: `SaslAuthenticationException`, `SSLHandshakeException` 발생 시 인증서 만료, 클럭 드리프트, DNS 레코드 상태를 확인한다.
- **프로듀서 thread pool 제한**: Sender Thread가 CPU 바운드일 경우 `num.io.threads` 조정 또는 프로듀서 인스턴스 분리로 부하를 나눈다.

## 좋은 모니터링
- **전송 지연**: `record-send-rate`, `request-latency-avg`.
- **에러율**: `record-error-rate`, `record-retry-rate`, `record-size-avg`.
- **배치 크기**: `batch-size-avg`, `compression-rate-avg`로 효율을 확인.
- **멱등성 상태**: `producer-id`, `epoch` 변경 이벤트를 감시해 세션 리셋을 감지한다.
- **버퍼 사용률**: `bufferpool-wait-time-total`, `buffer-exhausted-rate`로 backpressure 여부를 파악한다.
- **트랜잭션 상태**: `transaction-start-rate`, `transaction-abort-rate`를 모니터링해 트랜잭션 실패 시점을 찾는다.

## 운영 팁
- 파티션 키를 결정할 때 비즈니스 키 또는 해시 키를 사용해 불균형을 피하고, 데이터 순서 보장이 필요한 경우 단일 키를 유지한다.
- 멱등성 + 트랜잭션을 사용할 때는 브로커 `transaction.state.log.replication.factor`와 `transaction.state.log.min.isr`를 높여 안정성을 확보한다.
- 프로듀서 인스턴스를 스케일할 때 `client.id`를 고유하게 부여해 모니터링 지표를 분리한다.
- 발행 실패 대응 플로우:
  1. 예외 로그에서 에러 코드(`Errors` enum)를 확인.
  2. 재시도 가능한 오류인지(`RET` vs `NON-RET`) 판단 후 `retries`/`retry.backoff.ms` 조정.
  3. 재시도 불가이면 DLQ/알림으로 전환하고, 토픽/ACL/네트워크 상태를 점검.
- 트랜잭션 프로듀서 예시:
  ```java
  producer.initTransactions();
  producer.beginTransaction();
  producer.send(record);
  producer.sendOffsetsToTransaction(offsets, groupId);
  producer.commitTransaction();
  ```
  이 패턴을 사용하면 소스 토픽 소비와 싱크 토픽 발행을 exactly-once로 묶을 수 있다.
- 프로듀서 백업 계획: region 장애를 대비해 이중화된 bootstrap servers 리스트를 설정하고, `client.dns.lookup=use_all_dns_ips`를 사용해 DNS round-robin을 활용한다.
