# Auto-commit과 멱등성

## Auto-commit
- 컨슈머 설정 `enable.auto.commit=true`일 때, `auto.commit.interval.ms` 간격으로 Kafka 클라이언트가 자동으로 오프셋을 커밋한다.
- **장점**: 구현이 단순하고 오프셋 관리 코드가 필요 없다.
- **단점**:
  - 메시지를 처리하기 전에 커밋하면 실패 시 데이터 손실이 발생 (at-most-once).
  - 처리 후 커밋되더라도 interval 동안 중복 처리가 발생할 수 있다.
- **권장 사용법**: 중요 데이터일수록 `enable.auto.commit=false`로 수동 커밋을 사용하고, 처리 완료 시점에 `commitSync()` 또는 `commitAsync()`를 호출한다.
- **메시지를 처리하기 전에 커밋한다는 뜻?**: poll()이 레코드를 가져오고 auto-commit 스레드가 주기적으로 오프셋을 커밋한다. 만약 아직 비즈니스 로직이 실행되지 않았는데 commit 시점이 먼저 도달하면 브로커는 해당 오프셋을 완료된 것으로 기록한다. 이후 애플리케이션이 크래시하면 이미 커밋된 오프셋 이전 메시지는 다시 받지 못하므로 데이터가 유실된다.
- **실제 예시**: `auto.commit.interval.ms=5000`인 주문 컨슈머가 10초 걸리는 결제 API 호출을 처리한다고 하자. poll 직후 5초가 지나며 auto-commit이 선행되어 오프셋 `N`이 커밋된다. 그러나 7초 지점에서 결제 API가 예외를 던지고 컨슈머가 종료되면, 재시작 시 다음 오프셋 `N+1`부터 읽기 때문에 실패한 주문은 영원히 누락된다.

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

## 시각화: 수동 커밋 + 멱등성 전송 흐름
```
Producer(enable.idempotence) ──► Topic ──► Consumer(enable.auto.commit=false)
       │                                         │
       ├─ 재시도 발생 시 브로커가 시퀀스 중복 제거   ├─ 메시지 처리 → DB/외부 API
       │                                         └─ 처리 성공 시 commitSync()
       └─ (옵션) transactional.id로 exactly-once 보장
```
위 플로우는 프로듀서 중복과 컨슈머 오프셋 누락을 동시에 방지해 end-to-end 정합성을 높인다.

## 실제 운용 시나리오
1. 재고 서비스는 Kafka에서 주문 이벤트를 읽어 창고 수량을 차감한다. `enable.auto.commit=false`, `commitSync()` 사용.
2. 주문 이벤트 처리 중 DB deadlock으로 예외가 발생하면 오프셋 커밋이 수행되지 않으므로 재시작 시 동일 메시지를 다시 받는다.
3. 프로듀서는 `enable.idempotence=true`, `acks=all`로 쓰기 때문에 동일 이벤트를 두 번 전송해도 브로커가 두 번째 시도를 버린다.
4. 재처리 시에도 재고 테이블은 `UPSERT` 쿼리를 사용해 동일 주문 ID를 한 번만 차감하도록 애플리케이션 레벨 멱등성을 구현한다.

## 운영 체크리스트
- auto-commit을 사용할 경우 `max.poll.interval.ms`보다 짧은 간격으로 처리 완료 여부를 확인한다.
- 멱등성 활성화 시 브로커의 `transaction.state.log` 관련 설정과 모니터링 지표를 검토하고, producer ID 리셋 이벤트를 감시한다.
- 재시도 로직과 DLQ 전략을 결합해 실패 시 데이터 손실 없이 복구 가능하도록 설계한다.
