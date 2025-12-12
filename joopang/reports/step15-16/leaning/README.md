# Step 15-16 학습 키워드 정리

`spec.md`에서 요구하는 학습 항목을 키워드 기반으로 정리한 문서입니다.

## 1. 트랜잭션 경계(Transaction Boundary)
- **관련 지식**: DB 트랜잭션 ACID, Connection/Lock 유지 시간, 비즈니스 단계별 트랜잭션 구분.
- **기술 포인트**: Spring `@Transactional`, 트랜잭션 전파/격리 수준 설정, 긴 트랜잭션 분할 전략.

## 2. 사이드 이펙트 분리(Side-effect Isolation)
- **관련 지식**: 외부 API 호출, 파일/메시지 전송 등 비DB 작업이 트랜잭션에 미치는 영향.
- **기술 포인트**: 비동기 처리(Executor, `@Async`), 이벤트 분리, 보상 트랜잭션 설계.

## 3. Application Event / Domain Event
- **관련 지식**: Observer 패턴, 이벤트 발행/구독 모델, 도메인 이벤트 설계 원칙.
- **기술 포인트**: Spring `ApplicationEventPublisher`, `@TransactionalEventListener`, 이벤트 순서 제어(`@Order`), Event 클래스 설계.

## 4. 관심사 분리(Separation of Concerns)
- **관련 지식**: 레이어드 아키텍처, 핵심 로직 vs 부가 로직 분리, 단일 책임 원칙(SRP).
- **기술 포인트**: Facade/Service/Listener 역할 분배, 이벤트 활용하여 코드 모듈화.

## 5. 비동기 처리 & 큐잉 (Asynchronous Processing & Queueing)
- **관련 지식**: Back-pressure, 메시지 큐 개념, 이벤트 드리븐 아키텍처.
- **기술 포인트**: Redis Stream/Sorted Set, Kafka 등 메시지 브로커, ETA 계산(대기열 순번), PEL 관리.

## 6. Redis 활용 (Caching, Ranking, Queue)
- **관련 지식**: Redis 자료구조(String/List/Set/Sorted Set/Hash), TTL, 파이프라이닝.
- **기술 포인트**: Sorted Set 기반 랭킹, `ZUNIONSTORE`, Redis Stream 컨슈머 그룹, 캐시 키 전략, Warmup Job.

## 7. 분산락 / 동시성 제어
- **관련 지식**: Optimistic/Pessimistic Lock, Redlock 개념, 동시성 평가 지표.
- **기술 포인트**: Spring 분산락(`@DistributedLock`), Redis 기반 락 구현, 락 해제/갱신 처리.

## 8. 보상 트랜잭션 & Saga
- **관련 지식**: 최종적 일관성(Eventual Consistency), Saga 패턴(Choreography/Orchestration).
- **기술 포인트**: 보상 로직 설계, 상태 추적, 실패 감지 및 재처리 플로우.

## 9. 모니터링 & Observability
- **관련 지식**: APM, 메트릭/로그/트레이싱, 대시보드 구성 원칙.
- **기술 포인트**: DB Lock 모니터링, Redis 키 상태(TTL, Hit Ratio), 이벤트 처리 성공/실패 지표, Stream PEL 모니터링.

## 10. 분산 트랜잭션 대비 & MSA 확장성
- **관련 지식**: 도메인 분리, 서비스 간 의존성, 결과적 일관성.
- **기술 포인트**: 이벤트 스키마 관리, Outbox 패턴, 메시지 버전 관리, 향후 Kafka 등 외부 브로커로 확장 전략.
