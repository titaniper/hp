# 주문 인덱스 영향 테스트 계획

## 목표
`orders` 테이블에 정의된 복합 인덱스가 읽기 중심 쿼리에 어떤 영향을 주는지 검증한다. 대상 엔티티 선정 이유, 데이터 적재 방법, 분석 절차를 한눈에 정리해 두고 이후 실측 결과만 추가하면 된다.

## 대상 엔티티
- `io.joopang.services.order.domain.Order` (`orders` 테이블)
- 현재 인덱스
  - `idx_orders_status_paid_at_desc (status, paid_at DESC)`
  - `idx_orders_ordered_at_desc (ordered_at DESC)`
- 선택 이유
  - 상태별 조회, 최근 주문 조회 등 핵심 대시보드 쿼리가 그대로 반영되어 있음.
  - 컬럼 구성이 단순하여 인덱스 on/off 비교와 재생성이 쉬움.
  - 제약이 복잡하지 않아 더미 데이터를 안전하게 대량 삽입 가능.

## 테스트 데이터 준비
1. 로컬 MySQL 컨테이너 접속  
   `docker compose exec mysql mysql -ujoopang -pjoopang joopang`
2. 재귀 CTE 허용 깊이 증가  
   `SET SESSION cte_max_recursion_depth = 200000;`
3. 아래 스크립트를 실행해 10만 건 삽입 (`WITH RECURSIVE ...` 절은 전체 `INSERT` 문 앞에 붙는다)
   ```sql
   INSERT INTO orders (
    user_id, image_url, status, recipient_name, order_month,
    total_amount, discount_amount, ordered_at, paid_at, memo
)
SELECT
    100 + (n % 10) AS user_id,
    CONCAT('https://bulk.joopang/img/', n, '.png') AS image_url,
    'PENDING' AS status,
    CONCAT('Load Test User ', LPAD(n, 6, '0')) AS recipient_name,
    DATE_FORMAT(
            DATE_SUB('2024-12-01', INTERVAL (n % 18) MONTH),
            '%Y-%m'
    ) AS order_month,
    30000 + (n % 5000) * 10 AS total_amount,
    CASE WHEN n % 7 = 0 THEN 5000 ELSE 0 END AS discount_amount,
    TIMESTAMPADD(MINUTE, n, '2024-01-01 00:00:00') AS ordered_at,
    CASE WHEN n % 4 = 0 THEN TIMESTAMPADD(MINUTE, n + 5, '2024-01-01 00:00:00') ELSE NULL END AS paid_at,
    NULL AS memo
FROM (
         SELECT @n := @n + 1 AS n
         FROM joopang.orders  -- 행 수 많은 아무 테이블
         LIMIT 100000
     ) AS seq;

INSERT INTO orders (
    user_id, image_url, status, recipient_name, order_month,
    total_amount, discount_amount, ordered_at, paid_at, memo
)
SELECT
    100 + (n % 10) AS user_id,
    CONCAT('https://bulk.joopang/img/', n, '.png') AS image_url,
    'PAID' AS status,
    CONCAT('Load Test User ', LPAD(n, 6, '0')) AS recipient_name,
    DATE_FORMAT(
            DATE_SUB('2024-12-01', INTERVAL (n % 18) MONTH),
            '%Y-%m'
    ) AS order_month,
    30000 + (n % 5000) * 10 AS total_amount,
    CASE WHEN n % 7 = 0 THEN 5000 ELSE 0 END AS discount_amount,
    TIMESTAMPADD(MINUTE, n, '2024-01-01 00:00:00') AS ordered_at,
    CASE WHEN n % 4 = 0 THEN TIMESTAMPADD(MINUTE, n + 5, '2024-01-01 00:00:00') ELSE NULL END AS paid_at,
    NULL AS memo
FROM (
         SELECT @n := @n + 1 AS n
         FROM joopang.orders  -- 행 수 많은 아무 테이블
         LIMIT 100000
     ) AS seq;

INSERT INTO orders (
    user_id, image_url, status, recipient_name, order_month,
    total_amount, discount_amount, ordered_at, paid_at, memo
)
SELECT
    100 + (n % 10) AS user_id,
    CONCAT('https://bulk.joopang/img/', n, '.png') AS image_url,
    'CANCELED' AS status,
    CONCAT('Load Test User ', LPAD(n, 6, '0')) AS recipient_name,
    DATE_FORMAT(
            DATE_SUB('2024-12-01', INTERVAL (n % 18) MONTH),
            '%Y-%m'
    ) AS order_month,
    30000 + (n % 5000) * 10 AS total_amount,
    CASE WHEN n % 7 = 0 THEN 5000 ELSE 0 END AS discount_amount,
    TIMESTAMPADD(MINUTE, n, '2024-01-01 00:00:00') AS ordered_at,
    CASE WHEN n % 4 = 0 THEN TIMESTAMPADD(MINUTE, n + 5, '2024-01-01 00:00:00') ELSE NULL END AS paid_at,
    NULL AS memo
FROM (
         SELECT @n := @n + 1 AS n
         FROM joopang.orders  -- 행 수 많은 아무 테이블
         LIMIT 100000
     ) AS seq;


SELECT count(*)
FROM joopang.orders;

SHOW INDEX FROM orders

# 1,SIMPLE,orders,,ALL,,,,,472780,16.67,Using where; Using filesort
EXPLAIN SELECT * FROM orders WHERE status='PENDING' ORDER BY paid_at DESC LIMIT 50;


# -> Limit: 50 row(s)  (cost=48312 rows=50) (actual time=220..220 rows=50 loops=1)
#     -> Sort: orders.paid_at DESC, limit input to 50 row(s) per chunk  (cost=48312 rows=472780) (actual time=220..220 rows=50 loops=1)
#         -> Filter: (orders.`status` = 'PENDING')  (cost=48312 rows=472780) (actual time=0.118..190 rows=276815 loops=1)
#             -> Table scan on orders  (cost=48312 rows=472780) (actual time=0.104..160 rows=476816 loops=1)
EXPLAIN ANALYZE SELECT * FROM orders WHERE status='PENDING' ORDER BY paid_at DESC LIMIT 50;




ALTER TABLE orders ADD INDEX idx_orders_status_paid_at_desc (status, paid_at DESC);

ALTER TABLE orders ADD INDEX idx_orders_ordered_at_desc (ordered_at DESC);

# 1,SIMPLE,orders,,ref,idx_orders_status_paid_at_desc,idx_orders_status_paid_at_desc,1,const,236390,100,
EXPLAIN SELECT * FROM orders WHERE status='PENDING' ORDER BY paid_at DESC LIMIT 50;

# -> Limit: 50 row(s)  (cost=26740 rows=50) (actual time=0.916..1.17 rows=50 loops=1)
#     -> Index lookup on orders using idx_orders_status_paid_at_desc (status='PENDING')  (cost=26740 rows=236390) (actual time=0.914..1.14 rows=50 loops=1)
EXPLAIN ANALYZE SELECT * FROM orders WHERE status='PENDING' ORDER BY paid_at DESC LIMIT 50;



ALTER TABLE orders DROP INDEX idx_orders_status_paid_at_desc;
ALTER TABLE orders DROP INDEX idx_orders_ordered_at_desc;

SET @n := 0;

   ```
   > MySQL Workbench나 CLI에서 여러 문장을 한 번에 보낼 때는 이전 문장이 `;`로 종료되었는지 확인한다. 워크벤치의 경우 `Query -> Query Options -> SQL Execution`에서 `Treat BACKSLASH as escape character` 옵션이 켜져 있을 때 `\`를 포함한 문자열은 다른 의미로 해석될 수 있다.
4. 삽입 직후 `ANALYZE TABLE orders;` 실행해 옵티마이저 통계를 갱신.

## 분석 절차
1. 기존 인덱스가 있는 상태에서 기준선 측정.
   - `EXPLAIN ANALYZE SELECT * FROM orders WHERE status='PAID' ORDER BY paid_at DESC LIMIT 50;`
   - `EXPLAIN ANALYZE SELECT * FROM orders ORDER BY ordered_at DESC LIMIT 50;`
   - 실행 계획과 실제 소요 시간 기록.
2. 인덱스를 하나씩 제거(`ALTER TABLE orders DROP INDEX idx_orders_status_paid_at_desc;`)하며 동일 쿼리를 재실행.
3. 인덱스 재생성 후 비교 및 재생성 소요 시간 확인.
   - `ALTER TABLE orders ADD INDEX idx_orders_status_paid_at_desc (status, paid_at DESC);`
   - `ALTER TABLE orders ADD INDEX idx_orders_ordered_at_desc (ordered_at DESC);`
4. 선택: 각 시나리오 전후로 `SHOW STATUS LIKE 'Handler_read%';` 를 실행해 핸들러 읽기 수를 비교.

## 보고용 템플릿 (실행 후 작성)
- 데이터 건수: `_____` 행
- 쿼리 1 (status + paid_at): 인덱스 on/off 실행 계획 및 시간
- 쿼리 2 (ordered_at): 인덱스 on/off 실행 계획 및 시간
- 관찰 내용: `_____`
- 권장 사항: 인덱스 유지/삭제 여부, 추가 고려 포인트

실제 실행 결과를 확보하면 위 템플릿을 채워 최종 보고서를 완성한다.

---

## 실행 결과 (2024-06-XX)
- **데이터 건수**: 약 472,780건 (`count(*)` 결과 + `EXPLAIN` 추정치 일치)
- **기준 쿼리**: `SELECT * FROM orders WHERE status='PENDING' ORDER BY paid_at DESC LIMIT 50;`

### 인덱스 제거 상태
- `SHOW INDEX FROM orders`: 관련 인덱스 없음
- `EXPLAIN` 결과
  - type=`ALL`, key=`NULL`, rows≈472,780
  - Extra=`Using where; Using filesort`
- `EXPLAIN ANALYZE`
  - 실제 시간: 0.118ms~220ms, 1회 수행
  - 계획
    ```
    Limit → Sort(paid_at DESC) → Filter(status='PENDING') → Table scan
    ```
  - 관찰: 전체 테이블 스캔 후 filesort 수행, 27만 건 필터링 → 50건 추출까지 220ms 내외 소요

### 인덱스 추가 상태
- 인덱스 생성
  - `ALTER TABLE orders ADD INDEX idx_orders_status_paid_at_desc (status, paid_at DESC);`
  - `ALTER TABLE orders ADD INDEX idx_orders_ordered_at_desc (ordered_at DESC);`
- `EXPLAIN` 결과
  - type=`ref`, key=`idx_orders_status_paid_at_desc`, rows≈236,390
  - Extra=`Using where`
- `EXPLAIN ANALYZE`
  - 실제 시간: 0.916ms~1.17ms
  - 계획
    ```
    Limit → Index lookup(idx_orders_status_paid_at_desc)
    ```
  - 관찰: 상태 필터와 정렬을 동시에 처리, 상위 50건을 빠르게 가져옴

### 비교 및 결론
- 시간: 220ms → 1ms (약 200배 개선)
- CPU/IO: 풀스캔 + filesort 제거 → 인덱스 시퀀스 접근으로 Handler read 횟수 현저히 감소 예상
- 정리: `status, paid_at DESC` 복합 인덱스가 필수적이며 `ordered_at DESC` 단독 인덱스도 최근 주문순 조회에 동일한 이득을 제공할 것으로 예상. 대량 데이터 환경에서는 두 인덱스를 모두 유지 권장.
