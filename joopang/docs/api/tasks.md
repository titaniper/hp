# 구현 태스크 — 주팡 API

## 1. 공통 인프라
- 인증/인가 계층 구현: OAuth2 JWT 발급/검증, 역할별 RBAC 매핑(`Guest`, `Customer`, `Support`, `Inventory`, `Marketing`, `Admin`, `External`).
- 요청 컨텍스트 미들웨어: `Accept-Language`, `X-Market`, `Idempotency-Key`, `X-Session-Id` 파싱 및 검증.
- 에러 핸들러 공통화: 오류 코드/메시지 표준화, `traceId` 발급, `Retry-After` 헤더 처리.
- 로깅/모니터링 기본 설정: 구조화 로그, 분산 트레이싱 헤더, 핵심 메트릭(응답 시간, 오류율) 노출.
- 데이터 모델 정의: 상품/재고/쿠폰/주문/사용자/지갑/아웃박스 테이블 및 인덱스 설계.
- 로캘/통화 유틸: 가격 포맷, 환율 적용, 언어별 메시지 번역 관리.

## 2. 카탈로그 서비스
- `GET /products` 구현: 카테고리 필터, 정렬, 페이지네이션, 로캘 반영.
- `GET /products/{id}`: 상품 상세, 옵션, 프로모션 정보 포함.
- `GET /products/top`: 최근 3일 판매량·매출 기반 Top 5 계산(시장별 캐시 전략 포함).
- 인기/재고 데이터 수집용 백오피스 잡: 판매 통계 집계 및 캐시 업데이트.
- 상품/재고 조회용 리드 레플리카 고려 및 장애시 폴백 로직 추가.

## 3. 장바구니 서비스
- 사용자/세션 장바구니 도메인 모델 정의 및 저장소 구현(세션 장바구니 TTL).
- `GET /carts/current`: 사용자 인증 여부에 따라 장바구니 식별자 결정.
- `PUT /carts/current/items`: upsert 로직, 재고/구매 한도 검증, 멱등 처리.
- `DELETE /carts/current/items/{itemId}`: 항목 삭제 및 합계 재계산.
- `POST /carts/merge`: 비회원 → 회원 전환 시 병합 규칙 정의(중복 SKU 합산, 한도 체크).
- 장바구니 합계 계산기: 할인 전/후 금액, 예상 세금/배송비 계산 모듈.

## 4. 쿠폰 관리
- 쿠폰 엔티티 및 발급 이력 저장소 설계(수량, 유효 기간, 대상 조건).
- `POST /coupons/{code}/claim`: 선착순 발급(동시성 잠금, 잔여 수량 감소, 사용자 한도 검증).
- `POST /coupons/validate`: 장바구니/주문 대상 검증 서비스 구현.
- 사용자 쿠폰 상태 추적(사용/취소/만료) 및 히스토리 API 노출.
- 관리자용 쿠폰 생성/수정/통계 API(`POST /admin/coupons`, `PATCH /admin/coupons/{id}`, `GET /admin/coupons/{id}/stats`).
- 쿠폰 상태 변경 시 이벤트 발행 및 모니터링 지표 수집.

## 5. 주문·결제 서비스
- 주문/지갑 도메인 모델과 상태 전이 로직 설계(`PENDING`→`PAID`→...).
- `POST /orders`: 멱등 키 처리, 재고 예약, 쿠폰 적용, 지갑 차감 트랜잭션.
- 재고 예약/해제 서비스: 결제 실패·타임아웃 시 롤백 스케줄러 구현.
- `GET /orders`, `GET /orders/{id}`: 고객별 필터링, 권한 검증, 세부 정보 반환.
- `POST /orders/{id}/cancel`: 취소 가능 상태 체크, 재고/잔액 복원, 로그 기록.
- 지갑 API (`GET /wallet/balance`, `GET /wallet/transactions`, `POST /wallet/refunds`) 구현 및 멱등 처리.
- 환불 프로세스: 취소 요청 → 환불 실행 → 사용자 알림 워크플로우 구성.

## 6. 재고 관리
- 재고 수량 테이블 및 감사 로그 테이블 구축.
- `POST /admin/inventory/adjustments`: 재고 관리자 권한 검증, 음수 방지, 감사 로그 기록.
- `GET /admin/inventory/audits`: 필터링 및 페이지네이션.
- 재고 변경 이벤트 발행(메시지 큐 또는 스트림) 및 다운스트림 연동.
- 예약/차감/복원과 재고 조정 간 일관성 보장을 위한 잠금/버전 관리 전략 수립.

## 7. 고객 지원 기능
- `GET /support/customers/{id}/orders`: 고객 상담사 권한으로 주문/쿠폰/메모 조회.
- `POST /support/orders/{id}/refunds`: 정책 검증, 환불 요청 생성, 상태 트래킹.
- 상담사 활동 로그 기록 및 감사 조회 기능.
- 민감 정보 마스킹 정책(주소, 결제 관련 데이터) 적용.

## 8. 운영·모니터링
- `GET /admin/metrics/core`: 주문/재고/쿠폰 주요 지표 계산 파이프라인 구현(P95, 실패율).
- `GET /admin/integrations/outbox`, `POST /admin/integrations/outbox/{id}/retry`: 아웃박스 상태 조회/재시도 API.
- 시스템 설정 변경 감사 로그와 승인 워크플로우(이중 승인 등) 설계.
- 알림 채널 연동(Slack/Email) 및 임계값 설정 UI/설정 API.

## 9. 외부 데이터 연동
- 아웃박스 패턴 구현: 이벤트 저장, 배치 전달 워커, 재시도 백오프.
- `GET /integrations/events/pull`: Long Polling/Batch 전달, mTLS + HMAC 인증 처리.
- `POST /integrations/events/ack`, `POST /integrations/events/retry`: 상태 전이 및 재전송 큐 관리.
- 이벤트 중복 처리 방지 로직(이벤트 ID 기준 idempotent 소비) 및 모니터링 대시보드.

## 10. 테스트·검증
- 단위/통합 테스트 작성: 재고 예약, 쿠폰 발급 동시성, 주문 멱등성, 권한 체크 케이스 포함.
- 로컬/스테이징 환경용 시드 데이터 및 목(Mock) 서비스 구성.
- 성능 테스트 계획: 인기 상품, 쿠폰 발급, 주문 생성 시나리오 부하 테스트.
- 관측성 검증: traceId 전파, 메트릭 노출, 알람 시뮬레이션.

## 11. 문서화·배포
- OpenAPI 스키마 작성 및 자동화된 문서 배포 파이프라인 구성.
- 운영 핸드북: 장애 대응 절차, 재시도 전략, 수동 조정 가이드 작성.
- API 변경 관리 절차(버전 정책, Deprecation Notice) 수립.
- 배포 전략 설계: Blue/Green 또는 Canary, 마이그레이션 스크립트 자동화.
