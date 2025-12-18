# 카프카 전달 보장 (Delivery Semantics)

## At-most-once
- **설명**: 메시지를 최대 한 번만 전달하며, 실패 시 손실 가능. 컨슈머가 데이터를 읽고 처리하기 전 오프셋을 커밋한다.
- **장점**: 구현 단순, 지연 최소.
- **단점**: 메시지 손실 위험이 있어 중요 데이터에는 적합하지 않음.

## At-least-once
- **설명**: 최소 한 번 전달. 컨슈머는 처리가 끝난 뒤 오프셋을 커밋하거나 브로커는 acks=all로 확인을 받는다.
- **장점**: 데이터 손실 없이 내구성을 확보.
- **단점**: 오류나 재시도 시 중복 처리가 발생할 수 있어 idempotent 처리 필요.

## Exactly-once
- **설명**: 중복 없이 정확히 한 번 처리. 카프카는 프로듀서 멱등성 + 트랜잭션 + 컨슈머 read-process-write를 조합해 구현한다.
- **필수 조건**:
  1. `enable.idempotence=true`로 멱등성 프로듀서 사용.
  2. `transactional.id`를 지정하고 `initTransactions()` → `beginTransaction()` → `sendOffsetsToTransaction()` → `commitTransaction()` 흐름 사용.
  3. 컨슈머는 `isolation.level=read_committed`로 설정해 커밋된 레코드만 읽는다.
- **주의사항**: 트랜잭션 타임아웃, 상태 저장소 필요, 브로커 설정(`transaction.state.log.replication.factor`, `min.isr`)이 요구됨.

## 전략 비교
| 전략 | 손실 | 중복 | 복잡도 | 사용 예 |
| --- | --- | --- | --- | --- |
| At-most-once | 가능 | 없음 | 낮음 | 캐시 갱신, 로그 수집 등 손실 허용 |
| At-least-once | 없음 | 가능 | 중간 | 일반적인 이벤트 처리, ETL |
| Exactly-once | 없음 | 없음 | 높음 | 금융 거래, 재고 관리 등 정합성 필요 |

## 운영 고려
- At-least-once를 기본으로 하고, 중복 허용이 어려운 부분에만 Exactly-once를 적용해 비용을 최소화.
- 컨슈머 애플리케이션에서 idempotent 처리를 구현(예: upsert, unique constraint)해 재시도 시 안정성을 높인다.
- 브로커/프로듀서/컨슈머 버전을 맞추고, 트랜잭션 state log에 대한 모니터링을 추가한다.
