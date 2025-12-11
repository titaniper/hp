# Saga 패턴 상세 정리

## 1. 개념 및 배경

마이크로서비스 아키텍처(MSA)에서는 서비스별로 데이터베이스가 분리되어 있어, 단일 데이터베이스의 ACID 트랜잭션을 사용할 수 없습니다. 2PC(Two-Phase Commit) 같은 분산 트랜잭션은 성능 저하와 가용성 문제(Blocking)가 있어 클라우드 환경에 적합하지 않습니다.

**Saga 패턴**은 긴 트랜잭션(Long Running Transaction)을 여러 개의 짧은 로컬 트랜잭션으로 나누어 순차적으로 실행하는 패턴입니다.

- **ACID 대신 BASE**: Atomicity(원자성)을 보장하기 위해 모든 단계가 성공하거나, 실패 시 **보상 트랜잭션(Compensating Transaction)**을 실행하여 데이터를 원복합니다. 이를 통해 **결과적 일관성(Eventual Consistency)**을 달성합니다.

## 2. 핵심 구성 요소

1. **Saga**: 전체 비즈니스 프로세스.
1. **Transaction (T)**: 각 서비스에서 수행하는 로컬 트랜잭션. 실제 비즈니스 로직.
1. **Compensating Transaction (C)**: 특정 단계 실패 시, 이전 단계들의 변경 사항을 의미적으로 취소(Undo)하는 트랜잭션.
   - *주의*: 단순히 DB 롤백이 아니라, "결제 취소", "재고 복구"와 같은 비즈니스 로직입니다.
   - *특징*: 보상 트랜잭션은 반드시 성공해야 하며(재시도 필요), 멱등성(Idempotency)이 보장되어야 합니다.

## 3. 구현 방식: Choreography vs Orchestration

### 3.1 Choreography (코레오그래피)

중앙 제어 없이 각 서비스가 이벤트를 주고받으며 자율적으로 트랜잭션을 진행합니다.

- **흐름**:
  1. 주문 서비스: 주문 생성 트랜잭션 완료 -> `OrderCreated` 이벤트 발행.
  1. 결제 서비스: `OrderCreated` 수신 -> 결제 처리 -> `PaymentCompleted` 발행.
  1. 재고 서비스: `PaymentCompleted` 수신 -> 재고 차감 -> `StockUpdated` 발행.
  - **실패 시**: 재고 부족 발생 -> `StockFailed` 발행 -> 결제 서비스가 수신하여 결제 취소 -> 주문 서비스가 수신하여 주문 취소.

- **장점**:
  - 구성이 간단하고 초기 도입이 쉬움.
  - 중앙 집중적인 병목 지점이 없음 (Decentralized).

- **단점**:
  - 프로세스가 복잡해지면 어떤 서비스가 어떤 이벤트를 구독하는지 파악하기 어려움 (Cyclic Dependency 위험).
  - 통합 테스트가 어려움.

### 3.2 Orchestration (오케스트레이션)

중앙의 **Saga Orchestrator(Manager)**가 전체 트랜잭션의 상태를 관리하고, 각 서비스에 커맨드(Command)를 보냅니다.

- **흐름**:
  1. 주문 서비스: 주문 요청 -> 오케스트레이터 시작.
  1. 오케스트레이터: "결제 승인해" 커맨드 전송 -> 결제 서비스 처리 후 응답.
  1. 오케스트레이터: "재고 차감해" 커맨드 전송 -> 재고 서비스 처리 후 응답.
  - **실패 시**: 재고 서비스 실패 응답 -> 오케스트레이터가 "결제 취소해" 커맨드 전송 (보상 트랜잭션 실행).

- **장점**:
  - 비즈니스 로직의 흐름을 한곳에서 파악 및 관리 가능.
  - 서비스 간의 직접적인 의존성을 줄임 (서비스는 오케스트레이터만 알면 됨).
  - 복잡한 워크플로우 관리에 유리.

- **단점**:
  - 오케스트레이터가 추가적인 인프라 복잡성을 야기할 수 있음.
  - 오케스트레이터가 단일 실패 지점(SPOF)이 되지 않도록 고가용성 확보 필요.

## 4. Saga 패턴의 격리성(Isolation) 문제와 해결책

Saga 패턴은 ACID 중 A(원자성), C(일관성), D(지속성)는 어느 정도 보장하지만, **I(격리성)**은 보장하지 못합니다. 트랜잭션 중간에 데이터가 변경되고 커밋되므로, 다른 트랜잭션이 중간 상태의 데이터를 읽거나 변경할 수 있습니다.

### 주요 문제 (Anomalies)

1. **Lost Updates (갱신 손실)**: 한 Saga가 변경한 데이터를 다른 Saga가 덮어쓰는 경우.
1. **Dirty Reads (오손 읽기)**: 보상 트랜잭션으로 취소될 수 있는 중간 데이터를 다른 트랜잭션이 읽는 경우.
1. **Non-repeatable Reads (반복 불가능한 읽기)**: Saga 진행 중에 데이터가 변경되어 조회가 달라지는 경우.

### 해결책 (Countermeasures)

1. **Semantic Lock (의미적 락)**:
   - 데이터에 `PENDING`, `APPROVAL_WAIT` 같은 상태 플래그를 두어, Saga가 진행 중임을 표시합니다.
   - 다른 트랜잭션은 이 상태를 보고 접근을 제어하거나 경고를 띄웁니다.
1. **Commutative Updates (교환적 업데이트)**:
   - 업데이트 순서가 바뀌어도 결과가 같도록 설계합니다. (예: `x = x + 1`은 순서 상관없음)
1. **Pessimistic View (비관적 관점)**:
   - 더티 리드 위험을 줄이기 위해 Saga의 순서를 조정합니다. (예: 취소 가능성이 높은 단계를 뒤로 배치)
1. **Reread Value (값 다시 읽기)**:
   - 덮어쓰기 전에 값을 다시 읽어 변경 여부를 확인합니다 (Optimistic Locking과 유사).
1. **Version File**:
   - 순서가 꼬인 요청을 처리하기 위해 작업 기록을 남겨 비교합니다.

## 5. 예시 시나리오: 주문 실패 (Orchestration)

1. **주문 생성 (Start)**: 사용자가 주문 요청. `OrderSaga` 인스턴스 생성.
1. **결제 요청 (T1)**: 오케스트레이터가 결제 서비스에 `ApprovePayment` 명령 전송.
   - 결제 서비스: 결제 성공, DB 커밋. 응답 전송.
1. **재고 차감 (T2)**: 오케스트레이터가 재고 서비스에 `DecreaseStock` 명령 전송.
   - 재고 서비스: **재고 부족으로 실패**. 실패 응답 전송.
1. **보상 트랜잭션 시작**: 오케스트레이터가 실패 감지.
1. **결제 취소 (C1)**: 오케스트레이터가 결제 서비스에 `CancelPayment` 명령 전송.
   - 결제 서비스: 결제 취소 로직 수행 (환불). 성공 응답.
1. **주문 실패 처리 (End)**: 오케스트레이터가 주문 상태를 `FAILED`로 변경하고 종료.

## 6. 구현 팁 및 도구

- **멱등성(Idempotency)**: 네트워크 오류로 메시지가 중복 전달될 수 있으므로, 모든 트랜잭션과 보상 트랜잭션은 멱등해야 합니다. (이미 처리된 요청은 무시하거나 같은 결과 반환)
- **Transactional Outbox Pattern**: DB 업데이트와 이벤트 발행을 원자적으로 처리하기 위해 사용합니다.
- **프레임워크**:
  - Java: **Axon Framework**, **Eventuate Tram**, **Spring Cloud Stream**
  - Orchestrator 도구: **Camunda**, **Netflix Conductor**, **Uber Cadence**

## 7. 코드 예시 (Kotlin)

### 7.1 Choreography (Event Driven)

각 서비스가 이벤트를 구독하고 다음 작업을 수행합니다.

```kotlin
// 1. 주문 서비스: 주문 생성 후 이벤트 발행
@Service
class OrderService(private val eventPublisher: ApplicationEventPublisher) {
    @Transactional
    fun createOrder(order: Order) {
        orderRepository.save(order)
        // 주문 생성 이벤트 발행 -> 결제 서비스가 구독
        eventPublisher.publishEvent(OrderCreatedEvent(order.id, order.userId, order.amount))
    }
    
    // 결제 실패 이벤트 수신 시 보상 트랜잭션 (주문 취소)
    @EventListener
    fun handlePaymentFailed(event: PaymentFailedEvent) {
        val order = orderRepository.findById(event.orderId).get()
        order.cancel() // 상태 변경
        orderRepository.save(order)
    }
}

// 2. 결제 서비스: 주문 생성 이벤트 구독
@Component
class PaymentEventListener(private val paymentService: PaymentService) {
    @EventListener
    fun handleOrderCreated(event: OrderCreatedEvent) {
        try {
            paymentService.processPayment(event.orderId, event.amount)
            // 성공 시 재고 서비스로 이벤트 전파 (여기서는 생략)
        } catch (e: Exception) {
            // 실패 시 결제 실패 이벤트 발행 -> 주문 서비스가 구독하여 취소 처리
            eventPublisher.publishEvent(PaymentFailedEvent(event.orderId))
        }
    }
}
```

### 7.2 Orchestration (Central Manager)

중앙 오케스트레이터가 순서를 제어합니다.

```kotlin
@Service
class OrderSagaOrchestrator(
    private val paymentService: PaymentService,
    private val inventoryService: InventoryService,
    private val orderService: OrderService
) {
    fun processOrder(orderId: Long) {
        // Step 1: 결제 시도
        try {
            paymentService.processPayment(orderId)
        } catch (e: Exception) {
            // 결제 실패 시 바로 종료 (주문은 이미 생성된 상태라고 가정하면 주문 취소 호출)
            orderService.cancelOrder(orderId)
            return
        }

        // Step 2: 재고 차감
        try {
            inventoryService.decreaseStock(orderId)
        } catch (e: Exception) {
            // 재고 실패 시 -> 보상 트랜잭션: 결제 취소 + 주문 취소
            paymentService.cancelPayment(orderId) // 결제 취소 (보상)
            orderService.cancelOrder(orderId)     // 주문 취소 (보상)
            return
        }
        
        // 성공 완료
        orderService.completeOrder(orderId)
    }
}
```
