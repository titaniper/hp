# Saga 패턴 정리

## 1. 개념
- 긴 트랜잭션을 다수의 로컬 트랜잭션으로 분할하고, 각 단계가 성공하면 다음 단계로 진행하며 실패 시 보상 트랜잭션을 실행하는 분산 트랜잭션 패턴.
- 결과적 일관성을 전제로 하며, 중앙 오케스트레이터(Orchestration) 또는 참여자 간 이벤트 교환(Choreography)로 흐름을 제어한다.

## 2. 구성 요소
- **Action**: 각 서비스에서 수행하는 로컬 트랜잭션.
- **Compensation**: Action 실패 시 이전 상태를 되돌리는 로직.
- **Coordinator**: (선택) Saga 흐름을 제어하는 컴포넌트.
- **State Store**: 진행 상태, 실패 여부 등을 추적하기 위한 저장소.

## 3. Orchestration vs Choreography
- **Orchestration**: 전용 Saga 오케스트레이터가 단계 순서와 분기를 관리. 흐름이 명확하고 감시가 쉬움.
- **Choreography**: 각 서비스가 이벤트를 구독/발행하며 다음 작업을 트리거. 결합도가 낮지만 복잡도가 증가할 수 있음.

## 4. 장점
- 글로벌 트랜잭션 없이도 비즈니스 조합을 구현.
- 서비스별 로컬 트랜잭션으로 확장성과 독립 배포가 용이.

## 5. 단점 / 주의점
- 상태 관리, 보상 로직 구현 등 복잡도가 높음.
- 순환 의존(Event Storming)이나 중복 이벤트 처리에 주의.
- 최종 일관성까지 지연이 발생할 수 있음.

## 6. 예시: 주문-결제-배송
1. Order Service: 주문 생성(Action), 실패 시 주문 취소(Compensation).
2. Payment Service: 결제 승인(Action), 실패 시 주문 취소 이벤트 발행.
3. Inventory Service: 재고 차감(Action), 실패 시 결제 취소.
4. Delivery Service: 배송 요청(Action), 실패 시 재고 복원 → 결제 취소 → 주문 취소 순으로 보상.

## 7. 구현 팁
- 각 단계의 상태/이벤트를 Kafka, DB Outbox 등에 기록해 추적 가능하게 함.
- 보상 로직이 성공적으로 끝났는지 모니터링하기 위한 대시보드를 마련.
- 테스트 시 Happy Path뿐 아니라 단계별 failure case를 모두 시뮬레이션.
