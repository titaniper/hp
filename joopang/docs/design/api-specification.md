# API 명세 — 주팡 RESTful 서비스

## 1. 개요
- **API Base URL**: `https://api.joopang.com/v1`
- **지원 로캘**: `Accept-Language` 헤더(`ko-KR`, `en-US`) 및 `X-Market` 헤더(`KR`, `US`)
- **응답 포맷**: JSON (`application/json; charset=utf-8`)
- **타임라인**: MVP 범위에서 사용자 스토리(카탈로그 탐색, 장바구니, 쿠폰, 주문, 운영) 충족

## 2. 인증 및 공통 규약
- **엔드포인트 구분**
  - 퍼블릭/고객용: OAuth 2.0 Client Credentials + JWT Bearer (`Authorization: Bearer <token>`)
  - 내부/관리자용: 서비스 간 접근 토큰 + RBAC 권한 (`X-Role` 메타데이터)
  - 외부 데이터 플랫폼: mTLS + HMAC 서명(`X-Signature`)
- **공통 헤더**
  - `Accept-Language`: 응답 언어 선택 (기본 `ko-KR`)
  - `X-Market`: `KR` 또는 `US`, 미전달 시 사용자 프로필 기반 자동 결정
  - `Idempotency-Key`: POST/PUT 멱등 요청 시 필수
- **표준 응답 래퍼**
```json
{
  "data": { /* 성공 데이터 */ },
  "meta": { /* 페이지네이션, 처리 정보 */ },
  "errors": []
}
```
- **에러 응답**
```json
{
  "errors": [
    {
      "code": "COUPON.EXPIRED",
      "message": "쿠폰이 만료되었습니다.",
      "details": { "couponId": "CPN10" }
    }
  ],
  "traceId": "9dcb2e6f-..."
}
```

## 3. 도메인별 API

### 3.1 카탈로그

#### GET /products
- **설명**: 카테고리/정렬 조건으로 상품 목록 조회
- **쿼리 파라미터**
  - `category` (string, optional)
  - `sort` (enum: `price`, `popularity`, `newest`, default `popularity`)
  - `page` (int, default 1), `pageSize` (int, default 20, 최대 100)
- **응답 200**
```json
{
  "data": {
    "products": [
      {
        "productId": "P001",
        "name": "무선 키보드",
        "description": "저소음 기계식",
        "price": { "amount": 89000, "currency": "KRW" },
        "stock": { "status": "IN_STOCK", "quantity": 42 },
        "purchaseLimit": 3,
        "couponEligible": true
      }
    ]
  },
  "meta": { "page": 1, "pageSize": 20, "total": 120 }
}
```
- **오류**
  - `400 BAD_REQUEST`: 잘못된 정렬 조건 (`CATALOG.INVALID_SORT`)

#### GET /products/{productId}
- **설명**: 상품 상세 조회
- **응답 200**: 상품 정보 + 옵션, 재고, 프로모션 세부 정보
- **오류**
  - `404 NOT_FOUND`: 상품 미존재 (`CATALOG.NOT_FOUND`)

#### GET /products/top
- **설명**: 최근 3일 Top 5 인기 상품 조회
- **쿼리 파라미터**: `market` (enum, 필수)
- **응답 200**: `products` 배열(순위, 판매량, 매출)

### 3.2 장바구니

#### GET /carts/current
- **설명**: 현재 사용자(회원/세션)의 장바구니 조회
- **헤더**: 비회원은 `X-Session-Id` 필수
- **응답 200**: 장바구니 항목, 합계, 예상 할인

#### PUT /carts/current/items
- **설명**: 장바구니 항목 추가 또는 수량 업데이트 (Upsert)
- **요청**
```json
{
  "productId": "P001",
  "quantity": 2,
  "sessionId": "sess-123" // 비회원일 경우
}
```
- **응답 200**: 갱신된 장바구니 요약
- **오류**
  - `409 CONFLICT`: 재고 부족 (`CART.OUT_OF_STOCK`)
  - `409 CONFLICT`: 구매 한도 초과 (`CART.LIMIT_EXCEEDED`)

#### DELETE /carts/current/items/{itemId}
- **설명**: 장바구니 항목 삭제
- **응답 204**
- **오류**: `404 NOT_FOUND` (`CART.ITEM_NOT_FOUND`)

#### POST /carts/merge
- **설명**: 비회원 장바구니를 회원 장바구니와 병합
- **요청**: `{ "sessionId": "sess-123" }`
- **응답 200**: 병합 결과(충돌 항목, 최종 수량)

### 3.3 쿠폰(고객)

#### POST /coupons/{couponCode}/claim
- **설명**: 선착순 쿠폰 발급
- **헤더**: `Idempotency-Key`
- **응답 201**
```json
{
  "data": {
    "userCouponId": "UC-20240301-1",
    "couponCode": "FLASH10",
    "discountType": "PERCENT",
    "discountValue": 10,
    "expiresAt": "2024-03-31T14:59:00Z",
    "remainingQuantity": 482
  }
}
```
- **오류**
  - `409 CONFLICT`: 수량 소진 (`COUPON.SOLD_OUT`)
  - `422 UNPROCESSABLE_ENTITY`: 사용자 한도 초과 (`COUPON.USER_LIMIT`)

#### POST /coupons/validate
- **설명**: 장바구니/주문에 쿠폰 적용 시 사전 검증
- **요청**: `{ "couponCode": "FLASH10", "cartId": "CART-1" }`
- **응답**: 할인 금액, 적용 가능 상태

### 3.4 주문·결제

#### POST /orders
- **설명**: 주문 생성 및 결제 처리
- **헤더**: `Idempotency-Key`
- **요청**
```json
{
  "cartId": "CART-123",
  "paymentMethod": "WALLET",
  "couponCode": "FLASH10",
  "shippingAddressId": "ADDR-1"
}
```
- **응답 201**
```json
{
  "data": {
    "orderId": "ORD-20240301-0001",
    "status": "PAID",
    "total": { "amount": 78000, "currency": "KRW" },
    "discounts": [{ "type": "COUPON", "amount": 10000 }],
    "items": [
      { "productId": "P001", "quantity": 1, "unitPrice": 88000 }
    ]
  }
}
```
- **오류**
  - `409 CONFLICT`: 재고 부족 (`ORDER.OUT_OF_STOCK`)
  - `402 PAYMENT_REQUIRED`: 잔액 부족 (`PAYMENT.INSUFFICIENT_FUNDS`)
  - `409 CONFLICT`: 멱등 키 중복 (`ORDER.IDEMPOTENT_REPLAY`)

#### GET /orders
- **설명**: 고객 주문 목록 페이지네이션 조회
- **쿼리**: `status`, `from`, `to`, `page`, `pageSize`

#### GET /orders/{orderId}
- **설명**: 주문 상세 조회
- **응답 200**: 상태, 결제 정보, 배송 단계, 사용 쿠폰 정보
- **오류**: `403 FORBIDDEN` (타 사용자 접근 시 `ORDER.ACCESS_DENIED`)

#### POST /orders/{orderId}/cancel
- **설명**: 주문 취소 요청
- **응답 202**: 취소 요청 수락, 비동기 처리를 위한 `cancellationId`
- **오류**
  - `409 CONFLICT`: 취소 불가 상태 (`ORDER.CANNOT_CANCEL`)

### 3.5 지갑·잔액

#### GET /wallet/balance
- **설명**: 현재 지갑 잔액 조회
- **응답 200**: `{ "data": { "balance": { "amount": 120000, "currency": "KRW" } } }`

#### GET /wallet/transactions
- **설명**: 거래 내역 (입금, 결제, 환불) 목록
- **쿼리**: `type`, `from`, `to`, `page`, `pageSize`

#### POST /wallet/refunds
- **설명**: 주문 취소/환불로 발생한 잔액 복원
- **요청**: `{ "orderId": "ORD-20240301-0001", "reason": "CUSTOMER_CANCEL" }`
- **응답 202**: 환불 프로세스 시작

### 3.6 고객 지원

#### GET /support/customers/{customerId}/orders
- **설명**: 상담사가 고객 주문 목록 조회
- **권한**: `ROLE_SUPPORT`
- **응답 200**: 주문 기본 정보 + 최근 상담 메모

#### POST /support/orders/{orderId}/refunds
- **설명**: 상담사 환불 처리
- **요청**: `{ "amount": 78000, "reason": "DELIVERY_DELAY", "note": "고객 요청" }`
- **응답 202**: 환불 워크플로우 개시
- **오류**: `403 FORBIDDEN` (`SUPPORT.INSUFFICIENT_PERMISSION`)

### 3.7 재고 관리

#### POST /admin/inventory/adjustments
- **설명**: 재고 관리자 재고 조정
- **권한**: `ROLE_INVENTORY_MANAGER`
- **요청**
```json
{
  "sku": "P001",
  "delta": 50,
  "reason": "RESTOCK",
  "note": "3월 정기 입고"
}
```
- **응답 201**: 조정 ID, 조정 후 재고 수량
- **오류**
  - `400 BAD_REQUEST`: 음수 재고 허용 불가 (`INVENTORY.NEGATIVE_STOCK`)

#### GET /admin/inventory/audits
- **설명**: 재고 감사 로그 조회
- **쿼리**: `sku`, `actorId`, `from`, `to`

### 3.8 마케팅·쿠폰 캠페인

#### POST /admin/coupons
- **설명**: 신규 쿠폰 캠페인 생성
- **권한**: `ROLE_MARKETING`
- **요청**
```json
{
  "code": "FLASH10",
  "type": "PERCENT",
  "value": 10,
  "quantity": 5000,
  "perUserLimit": 1,
  "validFrom": "2024-03-01T00:00:00Z",
  "validUntil": "2024-03-07T14:59:00Z",
  "target": { "markets": ["KR"], "categories": ["ELECTRONICS"] }
}
```
- **응답 201**: 쿠폰 ID, 생성자, 상태

#### GET /admin/coupons/{couponId}/stats
- **설명**: 쿠폰 발급/사용 현황 모니터링
- **응답**: 발급 수, 사용 수, 잔여 수량, 사용자별 사용 요약

#### PATCH /admin/coupons/{couponId}
- **설명**: 쿠폰 비활성화(회수) 또는 속성 변경
- **요청**: `{ "status": "DISABLED", "reason": "SYSTEM_ISSUE" }`
- **응답 200**

### 3.9 운영·모니터링

#### GET /admin/metrics/core
- **설명**: 주문/재고/쿠폰 핵심 지표(P95 지연, 성공률, 재고 예약 실패율)
- **응답 200**: 시간대별 지표 데이터, 알람 상태

#### GET /admin/integrations/outbox
- **설명**: 외부 데이터 플랫폼 전송 큐 상태 모니터링
- **쿼리**: `status` (`PENDING`, `FAILED`), `from`, `to`
- **응답**: 이벤트 ID, 타입, 시도 횟수, 최종 오류

#### POST /admin/integrations/outbox/{eventId}/retry
- **설명**: 실패한 이벤트 재전송 트리거
- **응답 202**: 재전송 작업 수락

### 3.10 외부 데이터 플랫폼

#### GET /integrations/events/pull
- **설명**: 외부 플랫폼이 이벤트를 배치로 수신 (Long Polling)
- **인증**: mTLS + HMAC
- **쿼리**: `max=100`, `types=ORDER_CREATED,COUPON_REDEEMED`
- **응답 200**
```json
{
  "data": {
    "events": [
      {
        "eventId": "EVT-123",
        "type": "ORDER_CREATED",
        "payload": { "orderId": "ORD-1", "market": "KR", "total": 78000 },
        "occurredAt": "2024-03-01T02:15:00Z"
      }
    ]
  }
}
```

#### POST /integrations/events/ack
- **설명**: 수신 완료 이벤트 확인
- **요청**: `{ "eventIds": ["EVT-123", "EVT-124"] }`
- **응답 204**

#### POST /integrations/events/retry
- **설명**: 처리 실패 이벤트 재전송 요청
- **요청**: `{ "eventIds": ["EVT-321"], "reason": "TEMPORARY_FAILURE" }`
- **응답 202**

## 4. 오류 코드 및 상태

| HTTP 상태 | 코드                          | 설명 |
|-----------|-------------------------------|------|
| 400       | `CATALOG.INVALID_SORT`        | 지원하지 않는 정렬 조건 |
| 400       | `INVENTORY.NEGATIVE_STOCK`    | 재고 조정 결과 음수 불가 |
| 401       | `AUTH.UNAUTHORIZED`           | 토큰 누락 또는 만료 |
| 403       | `COMMON.FORBIDDEN`            | 권한 없음 |
| 403       | `SUPPORT.INSUFFICIENT_PERMISSION` | 상담사 권한 부족 |
| 404       | `CATALOG.NOT_FOUND`           | 상품 없음 |
| 404       | `CART.ITEM_NOT_FOUND`         | 장바구니 항목 없음 |
| 404       | `ORDER.NOT_FOUND`             | 주문 없음 |
| 409       | `ORDER.OUT_OF_STOCK`          | 주문 중 재고 부족 |
| 409       | `CART.OUT_OF_STOCK`           | 장바구니 추가 시 재고 부족 |
| 409       | `CART.LIMIT_EXCEEDED`         | 구매 한도 초과 |
| 409       | `COUPON.SOLD_OUT`             | 쿠폰 수량 소진 |
| 409       | `ORDER.CANNOT_CANCEL`         | 주문 취소 불가 상태 |
| 409       | `ORDER.IDEMPOTENT_REPLAY`     | 멱등 키 재사용 |
| 402       | `PAYMENT.INSUFFICIENT_FUNDS`  | 지갑 잔액 부족 |
| 422       | `COUPON.USER_LIMIT`           | 사용자 쿠폰 한도 초과 |
| 422       | `COUPON.INVALID_TARGET`       | 대상 상품/시장 불일치 |
| 500       | `COMMON.INTERNAL_ERROR`       | 시스템 오류 |
| 503       | `COMMON.SERVICE_UNAVAILABLE`  | 서비스 일시 중단 |

## 5. 상태 값 참조
- **OrderStatus**: `PENDING`, `PAID`, `FULFILLED`, `CANCELLED`, `REFUNDED`
- **CouponStatus**: `ACTIVE`, `DISABLED`, `EXPIRED`
- **InventoryAdjustmentReason**: `RESTOCK`, `CORRECTION`, `DAMAGE`, `LOST`
- **EventStatus**: `PENDING`, `DELIVERED`, `FAILED`, `RETRYING`

## 6. 예외 처리 정책
- 모든 오류 응답은 `traceId` 포함, 로그/모니터링과 연동
- 재시도 가능한 오류(`503`, `COMMON.SERVICE_UNAVAILABLE`)에는 `Retry-After` 헤더 제공
- 멱등 엔드포인트는 동일 키 요청 시 최초 응답 재전달
- Validation 오류(`422`)는 `errors[].field` 속성으로 문제 필드 명시
