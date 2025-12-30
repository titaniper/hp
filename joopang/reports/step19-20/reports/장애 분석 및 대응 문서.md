# DLQ 구현 계획

## 다이어그램 해석 요약
- **주 흐름**: Kafka consumer가 메시지를 서비스 레이어에 전달해 처리한다. 동일 오류로 3회 이상 재시도 실패 시 메시지를 별도 DLQ 토픽/큐로 이동한다.
- **DLQ 처리 흐름**: DLQ에 쌓인 메시지는 `dlq-consumer`가 읽어 DB에 실패 원인과 payload를 저장하고, 동시에 alert 채널로 알려 운영자가 즉시 상황을 인지한다.
- **운영介입**: 개발자가 admin service를 통해 특정 DLQ 레코드를 골라 수동 재실행을 수행하고, 왜 실패했는지 DB 기록을 기반으로 분석한다.
- **이벤트 발행**: `TransactionalEventListener (AFTER_COMMIT)`로 커밋 이후 DLQ 항목을 외부 채널(SQS 등)로 비동기 복제해 다중 채널 모니터링을 지원한다.
- **DLQ 메시지 소스**: Kafka, 앱 내부 이벤트, SQS 등 다양한 소스가 동일한 DLQ 대시보드에 표시된다.

## 요구사항 정리
1. **재시도 정책**: 기본 컨슈머에서 동일 예외가 3회 이상 반복되면 DLQ에 전송한다. 재시도 간 지연/백오프를 설정해 브로커 도배를 방지한다.
2. **DLQ 페이로드**: 원본 메시지, 헤더, 실패 원인(stack trace 요약), 발생 시각, 재시도 횟수를 함께 저장한다.
3. **DB 저장**: DLQ consumer는 실패 레코드를 `dlq_messages` 테이블에 영속화하고, admin service가 조회/재처리할 수 있도록 REST API를 제공한다.
4. **알림 채널**: DLQ consumer는 alert 시스템(예: Slack, PagerDuty)에 오류 정보를 push하여 즉각 대응이 가능하도록 한다.
5. **수동 재처리**: admin service에서 선택한 레코드를 원본 토픽으로 재발행하거나, 별도 복구 로직을 실행한다. 재처리 결과는 DB에 상태 업데이트.
6. **다중 소스**: Kafka 이외에도 앱 이벤트, SQS 등에서 동일 인터페이스로 DLQ를 관리할 수 있도록 파이프라인을 추상화한다.

## 구성 요소 및 책임
| 구성 요소 | 역할 |
| --- | --- |
| Main Consumer | 정상 토픽을 소비하고 3회 재시도 정책을 적용, 실패 시 DLQ 토픽으로 redirect |
| Service Layer | 비즈니스 로직 수행, 예외 발생 시 재시도/실패 전파 |
| DLQ Topic/Queue | 실패 이벤트를 격리해 저장, 추후 재처리 대상 |
| DLQ Consumer | DLQ 메시지 읽기, DB 기록, 알림, 추가 채널 복제 |
| DB (`dlq_messages`) | 실패 payload/metadata 저장, 재처리 상태 추적 |
| Alert System | 운영팀에게 실패 알림 |
| Admin Service | 개발자가 실패 사유를 조회하고 선택적으로 재실행 하는 UI/API |
| Transactional Event Listener | DB 커밋 이후 비동기로 DLQ 이벤트를 외부 큐(SQS 등)로 복제 |

## 단계별 구현 계획
1. **컨슈머 재시도/전환 로직**
   - Spring Kafka라면 `DefaultAfterRollbackProcessor` 또는 `DeadLetterPublishingRecoverer`를 사용해 3회 실패 시 DLQ 토픽으로 자동 전송.
   - 재시도 횟수, backoff, 재시도 가능 예외 목록을 설정 파일로 노출.
2. **DLQ 토픽/큐 설계**
   - `dlq.{original-topic}` 네이밍, 복제팩터 3, 보존 기간 7~14일.
   - 메시지 헤더에 `x-origin-topic`, `x-retry-count`, `x-error-code` 추가.
3. **DLQ Consumer 서비스**
   - 별도 마이크로서비스 또는 배치에서 DLQ 토픽 구독.
   - 메시지 도착 시 DB에 저장(`status=PENDING`, `error_detail`, `payload`).
   - Alert 발송(예: Slack webhook) + observability 지표 발행.
   - `@TransactionalEventListener(phase = AFTER_COMMIT)`을 사용해 DB 트랜잭션 완료 후 SQS로 비동기 발행.
4. **Admin Service & 재처리**
   - REST API: `GET /dlq`, `POST /dlq/{id}/replay`.
   - 재플레이 시 원본 토픽으로 메시지를 재전송하거나 별도 서비스 메서드를 직접 호출.
   - 실행 결과를 DB `status`와 `last_replayed_at`로 업데이트.
5. **모니터링/대시보드**
   - DLQ 메시지 수, 최근 실패 유형, 재처리 성공률을 Grafana 또는 내부 UI에 노출.
   - Kafka + app event + SQS 등 소스별 필터링 가능하도록 쿼리/지표 설계.
6. **운영 가이드**
   - 3회 이상 동일 실패 발생 시 즉시 알림 확인 → admin service에서 원인 검토 → 필요 시 재처리.
   - DLQ 잔여량이 임계치 초과 시 브로커/컨슈머 상태 점검 및 스케일링.

## 추가 고려사항
- 메시지 순서 요구가 있는 토픽은 DLQ 재처리 시 순서를 유지하도록 키 기반 재발행.
- PII 포함 데이터는 DLQ 저장 시 암호화 또는 토큰화를 적용.
- 대량 실패 시를 대비해 DLQ consumer의 처리량과 DB 파티션 전략을 사전에 준비.
