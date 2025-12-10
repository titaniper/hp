# Spring Application Event 정리

## 1. 개념
- Spring 컨테이너 내부에서 발생하는 도메인/애플리케이션 이벤트를 발행(publish)하고 리스너(listener)가 구독해 후속 작업을 수행하는 패턴.
- Observer 패턴을 기반으로 하며, Bean 간 결합도를 낮춰 관심사를 분리한다.

## 2. 핵심 지식
- `ApplicationEventPublisher` 또는 `ApplicationEventPublisherAware`를 통해 이벤트 발행.
- `@TransactionalEventListener`를 사용하면 트랜잭션 단계(AFTER_COMMIT, BEFORE_COMMIT 등)에 맞춰 리스너 실행.
- `@Order`로 다수 리스너의 실행 순서를 지정 가능.
- 기본적으로 동기 실행이며, `@Async`와 함께 사용하면 비동기 Event Listener 구성 가능.

## 3. 장점
- 메인 트랜잭션 로직과 부가 로직(알림, 로그 적재 등)을 분리해 유지보수성 향상.
- 재사용 가능한 이벤트 모델로 여러 리스너가 동일 이벤트를 구독 가능.
- 트랜잭션 경계를 명확하게 하여 롤백 영향 범위 제어 용이.

## 4. 단점 / 주의점
- 이벤트 플로우 추적이 어려워 디버깅이 복잡해질 수 있음.
- 비동기 리스너 사용 시 쓰레드풀/에러 처리 구성 필요.
- 이벤트 순서, 중복 처리, 실패 재시도 등 운영 정책을 명시해야 함.

## 5. 코드 예시
```kotlin
// 이벤트 정의
data class OrderCompletedEvent(val orderId: Long, val userId: Long)

// 발행
@Service
class OrderService(
    private val publisher: ApplicationEventPublisher,
) {
    @Transactional
    fun completeOrder(command: CompleteOrderCommand) {
        // ... 주문 비즈니스 처리 ...
        publisher.publishEvent(OrderCompletedEvent(orderId = command.orderId, userId = command.userId))
    }
}

// 리스너
@Component
class OrderEventListener {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun sendInvoice(event: OrderCompletedEvent) {
        // 청구서 발송 로직
    }
}
```

## 6. 실제 활용 예시
- 주문 완료 후 알림/포인트 적립/데이터 전송.
- 회원 가입 후 웰컴 이메일 발송.
- 결제 승인 후 회계 시스템에 이벤트 전파.
