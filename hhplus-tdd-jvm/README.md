# hhplus-tdd-jvm

## Point Service Enhancements

- 포인트는 100원 단위로만 충전/사용 가능합니다.
- 최대 보유 포인트는 1,000,000원이며 초과 시 예외가 발생합니다.
- 잔고 부족, 단위 위반, 최대치 초과 등 모든 정책 실패는 HTTP 400 응답으로 매핑됩니다.

## Concurrency Control

- **전략**: 사용자별 `ReentrantLock`을 `ConcurrentHashMap`에 저장해 동일 사용자 요청을 직렬화합니다.
- **장점**
  - 같은 사용자의 요청만 순차 처리하므로 데이터 정합성을 보장합니다.
  - 서로 다른 사용자 요청은 락을 공유하지 않아 병렬성이 유지됩니다.
  - 락 해제 후 대기 중 스레드가 없으면 맵에서 제거해 메모리 누수를 방지합니다.
- **단점**
  - 서버 인스턴스 간에는 공유되지 않으므로, 다중 서버 환경에서는 분산 락(예: Redis, DB row lock)으로 확장해야 합니다.
  - 락 획득/해제 비용이 있어 매우 높은 경쟁 상황에서는 성능 저하가 발생할 수 있습니다.

## Test Coverage

- **단위 테스트**: `PointServiceTest`  
  - 정상 시나리오 + 정책 위반(단위/최대치/잔고 부족) 검증.
- **통합 테스트**: `PointControllerIntegrationTest`  
  - 포인트 조회, 충전, 사용, 내역 조회 REST API 및 예외 케이스 검증.
- **동시성 통합 테스트**: `PointConcurrencyIntegrationTest`  
  - 동일 사용자에 대한 동시 충전 요청이 누락 없이 반영되는지 확인.
