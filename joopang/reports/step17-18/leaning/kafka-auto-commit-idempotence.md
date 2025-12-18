# Auto-commit과 멱등성

## Auto-commit
- 컨슈머 설정 `enable.auto.commit=true`일 때, `auto.commit.interval.ms` 간격으로 Kafka 클라이언트가 자동으로 오프셋을 커밋한다.
- **장점**: 구현이 단순하고 오프셋 관리 코드가 필요 없다.
- **단점**:
  - 메시지를 처리하기 전에 커밋하면 실패 시 데이터 손실이 발생 (at-most-once).
  - 처리 후 커밋되더라도 interval 동안 중복 처리가 발생할 수 있다.
- **권장 사용법**: 중요 데이터일수록 `enable.auto.commit=false`로 수동 커밋을 사용하고, 처리 완료 시점에 `commitSync()` 또는 `commitAsync()`를 호출한다.

## 멱등성(Idempotence)
- 프로듀서 설정 `enable.idempotence=true`를 통해 동일한 메시지가 여러 번 전송돼도 브로커가 중복을 제거한다.
- 브로커는 `(producerId, producerEpoch, sequenceNumber)`를 추적해 같은 시퀀스 번호를 가진 메시지를 무시한다.
- **필수 조건**: `acks=all`, `retries` > 0, `max.in.flight.requests.per.connection<=5`.
- **장점**: 네트워크 오류, 브로커 장애로 인한 재시도 중복을 제거해 at-least-once 환경에서 exactly-once 전송을 실현한다.
- **제약**: 멱등성은 단일 파티션에만 적용되며, 트랜잭션과 결합 필요 시 `transactional.id`를 사용한다.

## Auto-commit과 멱등성의 관계
- Auto-commit은 컨슈머 측 오프셋 관리, 멱등성은 프로듀서 측 중복 제거로 서로 다른 책임이다.
- End-to-end 정확성 확보를 위해서는 `auto.commit`을 끄고 수동 커밋, 멱등성 프로듀서, 필요 시 트랜잭션까지 결합한다.
- 컨슈머 애플리케이션도 멱등한 방식(upsert, unique key)으로 상태를 업데이트해 재시도 시 데이터 정합성을 유지한다.

## 운영 체크리스트
- auto-commit을 사용할 경우 `max.poll.interval.ms`보다 짧은 간격으로 처리 완료 여부를 확인한다.
- 멱등성 활성화 시 브로커의 `transaction.state.log` 관련 설정과 모니터링 지표를 검토하고, producer ID 리셋 이벤트를 감시한다.
- 재시도 로직과 DLQ 전략을 결합해 실패 시 데이터 손실 없이 복구 가능하도록 설계한다.
