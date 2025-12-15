# 보상 트랜잭션(Compensating Transaction)

## 1. 개념
- 분산 시스템에서 하나의 비즈니스 작업을 여러 단계로 나눴을 때 중간 실패가 발생하면 이전 단계에서 이미 반영된 상태를 되돌리는 트랜잭션.
- 즉각적인 롤백이 어려운 환경(외부 시스템 호출, 비동기 처리 등)에서 결과적 일관성을 유지하는 수단.

## 2. 핵심 지식
- 보상 트랜잭션은 원 트랜잭션과 반대되는 작업을 수행(예: 예약 취소, 포인트 환불).
- Saga 패턴에서 각 Step마다 `Action`과 `Compensation`이 함께 정의됨.
- 상태 추적이 중요하며, 보상 실행 여부를 기록할 저장소 또는 로그 필요.

## 3. 장점
- 단일 트랜잭션으로 묶을 수 없는 작업에 대한 복구 경로 제공.
- 서비스를 느슨하게 결합하면서도 데이터 일관성을 유지.

## 4. 단점 / 주의점
- 보상 로직 자체가 복잡하고 테스트 비용이 높음.
- 모든 상황을 완벽히 되돌릴 수 없는 경우가 있어 비즈니스 동의 필요.
- 보상 트랜잭션 실행 중 다시 실패할 경우 추가 복구 전략 필요.

## 5. 예시 시나리오
1. **항공권 예약**  
   - `Action`: 좌석 예약 → 결제 → 마일리지 적립  
   - `Compensation`: 적립 취소 → 결제 취소 → 좌석 반환
2. **커머스 주문**  
   - `Action`: 재고 차감 → 결제 승인 → 배송 요청  
   - `Compensation`: 배송 취소 → 결제 환불 → 재고 복원

## 6. 구현 아이디어
- 상태 머신 또는 Saga 오케스트레이터를 사용해 각 단계의 상태를 기록.
- 이벤트 로그(Outbox) 기반으로 보상 요청을 재시도/감시.
- 보상 불가 케이스에 대한 수동 처리 절차 정의.

## 7. 코드 예시 (Kotlin)

간단한 주문 취소 시나리오에서 결제 취소와 재고 복구를 수행하는 보상 트랜잭션 예시입니다.

```kotlin
@Service
class OrderCancelService(
    private val paymentService: PaymentService,
    private val inventoryService: InventoryService,
    private val orderRepository: OrderRepository
) {

    fun cancelOrder(orderId: Long) {
        val order = orderRepository.findById(orderId).orElseThrow()
        
        // 1. 결제 취소 (Compensation Action 1)
        try {
            paymentService.cancelPayment(order.paymentId)
        } catch (e: Exception) {
            // 결제 취소 실패 시 재시도 로직 또는 알림 필요 (여기서는 로그만)
            log.error("결제 취소 실패: ${order.id}", e)
            throw e // 상위로 전파하여 처리
        }

        // 2. 재고 복구 (Compensation Action 2)
        try {
            inventoryService.restoreStock(order.productId, order.quantity)
        } catch (e: Exception) {
            log.error("재고 복구 실패: ${order.id}", e)
            // 이미 성공한 '결제 취소'를 되돌릴 것인가? (재결제?) 
            // 아니면 재고 복구를 큐에 넣어 재시도할 것인가? -> 보통 재시도(Retry) 전략 사용
            retryRestoreStock(order) 
        }

        order.status = OrderStatus.CANCELLED
        orderRepository.save(order)
    }
    
    private fun retryRestoreStock(order: Order) {
        // Kafka나 DB 테이블에 실패 기록을 남겨 별도 데몬이 재시도하도록 처리
    }
}
```
