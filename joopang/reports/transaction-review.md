# 트랜잭션·경계·동시성 점검 보고서

_작성일: 2025-11-10_

## 요약
- 주문, 쿠폰, 결제 서비스의 핵심 메서드에 누락된 트랜잭션 경계를 추가해 복수 저장소를 갱신하는 작업이 전부 원자적으로 처리되고 예외 시 롤백되도록 했습니다.
- 기존에 사용하던 인메모리 락(Product/Coupon Lock Manager)이 새 트랜잭션 구성에서도 그대로 효력을 발휘함을 확인했습니다.
- 다단계 쓰기 흐름을 모두 단일 트랜잭션으로 감싼 이후 추가적인 동시성 결함은 발견되지 않았습니다.

## 상세 진단 및 조치

1. **주문 생성/재고 예약 시 재고 불일치 위험** (`src/main/kotlin/io/joopang/services/order/application/OrderService.kt:36-188`)
   - `reserveStock()`이 쿠폰 검증이나 `OrderRepository.save()` 이전에 `ProductRepository.update()`를 호출해 재고를 감소시켰습니다. 각 저장소 호출이 독립 트랜잭션으로 실행되면서 후속 단계에서 예외가 발생하면 주문 없이 재고만 줄어드는 문제가 있었습니다.
   - 서비스 전체에 `@Transactional(readOnly = true)`를 부여하고 `createOrder()`를 `@Transactional`로 명시해 재고 차감, 할인 생성, 주문 저장이 하나의 트랜잭션 안에서 함께 커밋/롤백되도록 수정했습니다.

2. **결제 처리 파이프라인이 비원자적으로 동작** (`src/main/kotlin/io/joopang/services/order/application/OrderService.kt:120-199`)
   - 사용자 잔액 차감, 쿠폰 사용 처리, 주문 상태 변경, 외부 전송 큐 적재가 각각 별도 트랜잭션이어서 잔액만 감소하고 주문은 여전히 PENDING 상태로 남는 등 불일치가 발생할 수 있었습니다.
   - `processPayment()`를 트랜잭션으로 감싸 동일 요청 내 변경 사항이 전부 성공하거나 전부 롤백되도록 했습니다. 기존 Product/Coupon 락도 그대로 이용합니다.

3. **쿠폰 발급 시 수량 차감과 쿠폰 생성이 분리되어 손실 가능** (`src/main/kotlin/io/joopang/services/coupon/application/CouponService.kt:17-131`)
   - 인메모리 락이 있더라도 템플릿 `issue()`와 쿠폰 저장이 서로 다른 DB 트랜잭션에서 커밋되어 `couponRepository.save()` 실패 시 템플릿 수량만 줄고 쿠폰은 생성되지 않는 문제가 있었습니다.
   - 클래스 기본을 읽기 전용 트랜잭션으로 지정하고 `issueCoupon()`/`getUserCoupons()`에 `@Transactional`을 부여해 템플릿 수량 차감, 쿠폰 발급, 자동 만료 처리가 하나의 트랜잭션에서 실행되도록 했습니다.

4. **쿠폰 만료 처리 중간 실패 시 API 응답 불일치** (`src/main/kotlin/io/joopang/services/coupon/application/CouponService.kt:77-96`)
   - `getUserCoupons()`는 호출 중 만료가 필요한 쿠폰을 바로 저장소에 반영하지만, 개별 `save()`가 각각 커밋되어 루프 중단 시 일부만 만료되고 응답에는 만료/미만료가 섞여 들어갈 수 있었습니다.
   - 동일하게 트랜잭션을 적용해 메서드 완료 전까지는 DB 상태가 변경되지 않도록 했고, 실패 시 전체가 롤백되도록 했습니다.

5. **결제 등록 경로의 향후 확장성 저하** (`src/main/kotlin/io/joopang/services/payment/application/PaymentService.kt:14-96`)
   - 현재는 단일 저장만 수행하지만 서비스 계층에 트랜잭션이 없어 추후 정산/이벤트 기록 등을 추가하면 동일한 롤백 범위에 묶을 수 없었습니다.
   - 주문/쿠폰과 동일하게 클래스 전반을 읽기 전용 트랜잭션으로 두고 `registerPayment()`만 명시적으로 트랜잭션 처리해 일관된 롤백 보증을 갖도록 했습니다.

## 검증
- 코드 정적 검토만 수행했습니다. 추가 신뢰가 필요하면 `./gradlew test` 실행을 권장합니다.

## 후속 권장사항
- 새 트랜잭션 범위 확대로 인한 잠재적 경합을 모니터링하세요(현재 락 구조상 영향은 제한적일 것으로 예상).
- 쿠폰/주문 실패 시 롤백을 검증하는 통합 테스트를 추가해 회귀를 조기에 감지하는 방안을 검토하세요.
