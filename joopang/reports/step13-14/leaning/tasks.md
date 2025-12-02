# Step 13-14 Tasks

## 1. 학습 (Study)

- [ ] **Redis 기본 자료구조 학습**
  - [ ] `Redis 자료구조에 대한 공부 문서.md` 정독 및 실습
  - [ ] Strings, Sets, Sorted Sets, List, Hash 특징 및 명령어 익히기
- [ ] **Redis 심화 학습**
  - [ ] Key-Value 구조 및 TTL(Time To Live) 활용 전략 이해
  - [ ] Redis Atomicity (원자성) 및 Single Thread 모델 이해
  - [ ] Redis Transaction (`MULTI`/`EXEC`) & Pipeline 학습
- [ ] **개발 환경 세팅**
  - [ ] Spring Data Redis / Redisson 라이브러리 설정
  - [ ] Redis Test Container 환경 구성 (통합 테스트용)

## 2. Step 13: 랭킹 시스템 구현 (Ranking System)

### 설계 (Design)

- [ ] **시나리오 선택**: 이커머스(주문 많은 상품)
- [ ] **Redis Key 설계**: `ranking:daily:{date}`, `ranking:weekly:{week}` 등 Naming Rule 정의
- [ ] **자료구조 선정**: `Sorted Set (ZSet)` 활용 방안 구체화
- [ ] **데이터 흐름 설계**:
  - [ ] 점수 집계 시점 (주문 완료 시점 등)
  - [ ] 트랜잭션 범위 설정 (DB 업데이트와 Redis 업데이트의 일관성 고려)
- [ ] **만료 전략(TTL) 수립**: 일간/주간 랭킹 초기화 전략

### 구현 (Implementation)

- [ ] 랭킹 점수 업데이트 로직 (`ZADD`, `ZINCRBY`)
- [ ] Top N 랭킹 조회 API (`ZREVRANGE`)
- [ ] 특정 상품의 현재 순위 조회 API (`ZREVRANK`)
- [ ] (선택) 랭킹 정보 시각화/그래프를 위한 데이터 제공 API

### 테스트 (Test)

- [ ] Redis Test Container 기반 통합 테스트 작성
- [ ] 동시성 테스트: 동시에 여러 주문 발생 시 점수 누락 없는지 검증

## 3. Step 14: 비동기/대기열 시스템 구현 (Asynchronous Design)

### 설계 (Design)

- [ ] **시나리오 선택**: 이커머스(선착순 쿠폰)
- [ ] **[이커머스] 선착순 쿠폰 발급 설계**
  - [ ] 재고 관리: `List` or `Sorted Set` or `String(Counter)`
  - [ ] 중복 발급 방지: `Set` 활용
  - [ ] RDBMS 부하 분산 전략 (Redis에서 처리 후 DB 비동기 반영 등)

### 구현 (Implementation)

- [ ] 기존 RDBMS 기반 로직 -> Redis 기반 로직으로 마이그레이션
- [ ] **동시성 제어**: 분산 환경에서 Race Condition 해결 (Lua Script 활용 등)
- [ ] **안정성 확보**:
  - [ ] 쿠폰 초과 발급 방지
- [ ] (필요시) 스케줄러 구현 (DB 동기화)

### 테스트 (Test)

- [ ] 대량 트래픽 상황 가정 부하 테스트 (k6 등 활용)
- [ ] 정합성 검증: 재고 0일 때 발급 요청 실패 확인, 중복 요청 차단 확인

## 4. 문서화 및 회고 (Documentation & Review)

- [ ] **시스템 설계 보고서 작성** (`reports/` 디렉토리)
  - [ ] 배경 (Background): 기존 시스템의 문제점 및 Redis 도입 필요성
  - [ ] 문제 해결 (Problem Solving): 자료구조 선택 이유, 설계 내용
  - [ ] 테스트 (Test): 부하 테스트 결과, 정합성 검증 결과
  - [ ] 한계점 (Limitations): Redis 장애 시 대응, 메모리 한계 등
  - [ ] 결론 (Conclusion)
- [ ] **회고 (Retrospective)**
  - [ ] KPT (Keep, Problem, Try) 작성
  - [ ] 잘한 점, 어려운 점, 다음 시도 정리
- [ ] **PR 제출**
  - [ ] PR 템플릿에 맞춰 내용 작성
  - [ ] 체크리스트 확인 (Ranking Design, Asynchronous Design, 통합 테스트)
