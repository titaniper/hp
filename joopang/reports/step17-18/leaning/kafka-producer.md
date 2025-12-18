# 카프카 프로듀서 운영 가이드

## 좋은 옵션들
| 목적 | 주요 옵션 | 설명 |
| --- | --- | --- |
| 내구성 | `acks=all`, `min.insync.replicas>=2` | 복제본 다수의 확인을 받아 데이터 손실을 최소화한다. |
| 성능/지연 | `batch.size`, `linger.ms`, `compression.type` | 배치 크기와 대기 시간을 조절해 처리량과 지연 사이 균형 조정. |
| 중복 방지 | `enable.idempotence=true`, `max.in.flight.requests.per.connection<=5` | 멱등성을 보장하고 순서를 유지한다. |
| 재시도 | `retries`, `delivery.timeout.ms`, `retry.backoff.ms` | 브로커 장애 시 안전하게 재전송하고 무한 대기 상태를 피한다. |
| 보안 | `sasl.mechanism`, `ssl.truststore.location`, `security.protocol` | 인증·암호화로 메시지를 보호한다. |

## 이벤트가 발행되지 않을 때 진단
1. **프로듀서 버퍼 적체**: `buffer.memory`가 가득 차면 `BufferExhaustedException`이 발생하므로 `max.block.ms`를 모니터링한다.
2. **배치 타임아웃**: `linger.ms`가 길고 트래픽이 낮으면 배치가 늦게 전송된다. 필요 시 `linger.ms`와 `batch.size`를 낮춰 즉시 전송.
3. **브로커 측 에러**: `NotEnoughReplicas`, `UnknownTopicOrPartition`와 같은 예외를 로깅하고, DLQ나 알림으로 전파한다.
4. **트랜잭션 동작**: `transaction.timeout.ms` 초과나 `transactional.id` 중복 시 커밋이 실패할 수 있으니 상태 스토어를 점검한다.

## 좋은 모니터링
- **전송 지연**: `record-send-rate`, `request-latency-avg`.
- **에러율**: `record-error-rate`, `record-retry-rate`, `record-size-avg`.
- **배치 크기**: `batch-size-avg`, `compression-rate-avg`로 효율을 확인.
- **멱등성 상태**: `producer-id`, `epoch` 변경 이벤트를 감시해 세션 리셋을 감지한다.

## 운영 팁
- 파티션 키를 결정할 때 비즈니스 키 또는 해시 키를 사용해 불균형을 피하고, 데이터 순서 보장이 필요한 경우 단일 키를 유지한다.
- 멱등성 + 트랜잭션을 사용할 때는 브로커 `transaction.state.log.replication.factor`와 `transaction.state.log.min.isr`를 높여 안정성을 확보한다.
- 프로듀서 인스턴스를 스케일할 때 `client.id`를 고유하게 부여해 모니터링 지표를 분리한다.
