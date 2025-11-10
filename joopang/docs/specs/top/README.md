# 초기 쿼리
```sql
SELECT
  p.id,
  p.name,
  SUM(oi.quantity) as sales_count,
  SUM(oi.subtotal) as revenue
FROM products p
JOIN order_items oi ON p.id = oi.product_id
JOIN orders o ON oi.order_id = o.id
WHERE o.status = 'PAID'
  AND o.paid_at >= DATE_SUB(NOW(), INTERVAL 3 DAY)
GROUP BY p.id, p.name
ORDER BY sales_count DESC
LIMIT 5;

UPDATE products
SET stock = stock - :quantity
WHERE id = :productId
  AND stock >= :quantity;
```

# GPT
좋아요. “최근 3일 기준으로 상품 조회수(view)와 상품수(count)를 기준으로 정렬해서 노출”을 구현하는 실무식 방법을 두 갈래로 정리해줄게요.

* A) **상품 목록을 노출**: 최근 3일 조회수 합계로 정렬(동률 시 보조 정렬)
* B) **카테고리/컬렉션을 노출**: 각 그룹의 최근 3일 조회수와 **그룹 내 상품 수**로 정렬

아래 설계는 이벤트 수집 → 일단위 집계 → 3일 롤링 합산 → API 응답까지 한 번에 이어집니다.

---

# 1) 데이터 수집 & 스키마(핵심만)

### (1) 뷰 이벤트 원천 테이블 (append-only)

```sql
-- product view raw logs (OLTP에는 무거우니 가능하면 분리)
CREATE TABLE product_view_events (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  product_id BIGINT NOT NULL,
  user_id BIGINT NULL,
  session_id VARCHAR(64) NULL,
  occurred_at DATETIME NOT NULL,
  user_agent VARCHAR(255) NULL,
  ip VARBINARY(16) NULL
);

-- 봇/중복 필터링은 애널리틱스 파이프라인에서 처리 (UA/AS 목록, 헤더, 속도, 쿠키/세션 기준 등)
```

### (2) 일단위 집계 테이블 (매일 증분 upsert)

```sql
CREATE TABLE product_metrics_daily (
  metric_date DATE NOT NULL,
  product_id BIGINT NOT NULL,
  views INT NOT NULL DEFAULT 0,
  sales INT NOT NULL DEFAULT 0,
  PRIMARY KEY (metric_date, product_id),
  INDEX (product_id, metric_date)
);
```

> 파이프라인: 로그 → (배치/스트리밍) → `product_metrics_daily`에 upsert
>
> * 배치는 매 시/5분 단위도 OK
> * 스트리밍이면 Kafka/Flink/ClickHouse/BigQuery 같은 OLAP이 더 편함

---

# 2) “최근 3일” 롤링 합산 뷰

## 옵션 1: 질의 시 계산(간단/유연)

**PostgreSQL**

```sql
SELECT p.id, p.name,
       COALESCE(SUM(d.views),0) AS views_3d
FROM products p
LEFT JOIN product_metrics_daily d
  ON d.product_id = p.id
 AND d.metric_date BETWEEN CURRENT_DATE - INTERVAL '2 day' AND CURRENT_DATE
WHERE p.is_active = TRUE
GROUP BY p.id, p.name
ORDER BY views_3d DESC, p.created_at DESC
LIMIT 50 OFFSET 0;
```

**MySQL 8**

```sql
SELECT p.id, p.name,
       COALESCE(SUM(d.views),0) AS views_3d
FROM products p
LEFT JOIN product_metrics_daily d
  ON d.product_id = p.id
 AND d.metric_date BETWEEN CURRENT_DATE - INTERVAL 2 DAY AND CURRENT_DATE
WHERE p.is_active = 1
GROUP BY p.id, p.name
ORDER BY views_3d DESC, p.created_at DESC
LIMIT 50 OFFSET 0;
```

## 옵션 2: 3일 합계를 미리 저장(성능/캐시)

```sql
CREATE TABLE product_metrics_3d (
  product_id BIGINT PRIMARY KEY,
  views_3d INT NOT NULL,
  refreshed_at DATETIME NOT NULL,
  INDEX (views_3d)
);

-- 5분마다 리프레시(증분): 어제/오늘/그제만 다시 계산
REPLACE INTO product_metrics_3d (product_id, views_3d, refreshed_at)
SELECT product_id,
       SUM(views) AS views_3d,
       NOW()
FROM product_metrics_daily
WHERE metric_date BETWEEN CURRENT_DATE - INTERVAL 2 DAY AND CURRENT_DATE
GROUP BY product_id;
```

---

# 3) 정렬 규칙 두 가지 시나리오

## A) “상품 노출” 정렬 (조회수 기준)

* 1차: `views_3d` 내림차순
* 2차: 재고/가격/신상품 가중치 등 보조 점수(선택)
* 3차: 동률 시 `recent_sales_3d` 같은 보조 지표, 또는 `created_at DESC`

**예시 SQL**

```sql
SELECT p.id, p.name, m.views_3d
FROM products p
JOIN product_metrics_3d m ON m.product_id = p.id
WHERE p.is_active = 1
ORDER BY m.views_3d DESC, p.created_at DESC
LIMIT 50;
```

**가중치 스코어(선택)**

```sql
-- views + 최근 판매 가중치 + 신상품 보너스 예시
SELECT p.id, p.name,
       (m.views_3d * 1.0
        + COALESCE(s.sales_3d,0) * 3.0
        + CASE WHEN p.created_at >= NOW() - INTERVAL 14 DAY THEN 50 ELSE 0 END
       ) AS score
FROM products p
JOIN product_metrics_3d m ON m.product_id = p.id
LEFT JOIN product_sales_3d s ON s.product_id = p.id
WHERE p.is_active = 1
ORDER BY score DESC
LIMIT 50;
```

## B) “카테고리/컬렉션 노출” 정렬 (조회수 + **상품수**)

여기서 “상품수”를 **카테고리 내 노출 가능한 상품 개수**로 정의합니다.

사전 준비:

```sql
-- 카테고리-상품 맵
CREATE TABLE category_products (
  category_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  PRIMARY KEY (category_id, product_id),
  INDEX (product_id)
);
```

집계 쿼리:

```sql
-- 각 카테고리의 최근 3일 조회수와 상품수
WITH cat_views AS (
  SELECT cp.category_id,
         SUM(d.views) AS views_3d
  FROM category_products cp
  JOIN product_metrics_daily d ON d.product_id = cp.product_id
  WHERE d.metric_date BETWEEN CURRENT_DATE - INTERVAL 2 DAY AND CURRENT_DATE
  GROUP BY cp.category_id
),
cat_counts AS (
  SELECT cp.category_id,
         COUNT(*) AS product_count
  FROM category_products cp
  JOIN products p ON p.id = cp.product_id AND p.is_active = 1
  GROUP BY cp.category_id
)
SELECT c.id, c.name,
       COALESCE(v.views_3d,0) AS views_3d,
       COALESCE(cnt.product_count,0) AS product_count
FROM categories c
LEFT JOIN cat_views v ON v.category_id = c.id
LEFT JOIN cat_counts cnt ON cnt.category_id = c.id
ORDER BY v.views_3d DESC, cnt.product_count DESC
LIMIT 30;
```

> 필요하면 `views_3d`와 `product_count`를 정규화해서 **혼합 점수**(예: 70% 조회 + 30% 상품수)로 한 번에 정렬해도 됩니다.

---

# 4) 초고속 실시간 버전(선택): Redis 슬라이딩 윈도우

페이지뷰가 매우 많고 “거의 실시간” 정렬이 필요하면 Redis에 **시간 버킷**을 둡니다.

* 키: `pv:{productId}:{yyyyMMddHH}` (시간별 카운터, TTL 72h+여유)
* 접근: 뷰 발생 시 `INCR pv:123:20251104-12`
* 읽기: `views_3d = SUM(pv:123:최근 72시간 키들)`
* 정렬: `ZADD z:views_3d score=views_3d member=productId`
* 1~5분 주기로 백그라운드 워커가 zset 갱신 → API는 zset `ZRANGE`로 즉시 응답
* 주기적으로 RDB/OLAP에 플러시하여 영속 집계(`product_metrics_daily`)와 동기화

장점: 실시간성, 낮은 레이턴시
주의: 키 수/메모리 관리, 시간대 경계, TTL 설정

---

# 5) API/레포 구현 예시 (TypeORM)

### 상품 정렬(3일 뷰 기준)

```ts
// ProductRepository (TypeORM QueryBuilder; MySQL/Postgres 공용 패턴)
async findTrendingProducts3d(limit = 50, offset = 0) {
  return this.dataSource
    .createQueryBuilder()
    .select('p.id', 'id')
    .addSelect('p.name', 'name')
    .addSelect('COALESCE(SUM(d.views),0)', 'views_3d')
    .from('products', 'p')
    .leftJoin('product_metrics_daily', 'd', `
      d.product_id = p.id
      AND d.metric_date BETWEEN CURRENT_DATE - INTERVAL 2 DAY AND CURRENT_DATE
    `)
    .where('p.is_active = 1')
    .groupBy('p.id')
    .orderBy('views_3d', 'DESC')
    .addOrderBy('p.created_at', 'DESC')
    .limit(limit)
    .offset(offset)
    .getRawMany();
}
```

### 카테고리 정렬(3일 뷰 → 상품수)

```ts
async findTrendingCategories3d(limit = 30) {
  return this.dataSource.query(`
    WITH cat_views AS (
      SELECT cp.category_id, SUM(d.views) AS views_3d
      FROM category_products cp
      JOIN product_metrics_daily d ON d.product_id = cp.product_id
      WHERE d.metric_date BETWEEN CURRENT_DATE - INTERVAL 2 DAY AND CURRENT_DATE
      GROUP BY cp.category_id
    ),
    cat_counts AS (
      SELECT cp.category_id, COUNT(*) AS product_count
      FROM category_products cp
      JOIN products p ON p.id = cp.product_id AND p.is_active = 1
      GROUP BY cp.category_id
    )
    SELECT c.id, c.name,
           COALESCE(v.views_3d,0) AS views_3d,
           COALESCE(cnt.product_count,0) AS product_count
    FROM categories c
    LEFT JOIN cat_views v ON v.category_id = c.id
    LEFT JOIN cat_counts cnt ON cnt.category_id = c.id
    ORDER BY views_3d DESC, product_count DESC
    LIMIT ?;
  `, [limit]);
}
```

---

# 6) 운영 팁 & 엣지 케이스

* **중복/봇 필터링**: 동일 사용자/세션의 아주 짧은 재조회는 쿨다운(예: 30초) 적용, 알려진 봇 UA 제외.
* **신상품/롱테일 가중치**: 노출 다양성 위해 신상품 보너스, 탐색 슬롯(예: Top N 중 일부는 랜덤/개인화) 도입.
* **캐시**: 3~5분 TTL 캐시(Redis/CloudFront)로 API 부하 감소.
* **지표 정의 고정**: “조회수”를 “페이지뷰”로 고정할지, UV(고유 사용자)로 할지 사전에 확정. 바꾸면 지표 단절.
* **카테고리 상품수**: “판매중/전시중” 조건을 명확히. 옵션 품목은 별도 취급 권장.
* **시간대**: 기준 타임존(Asia/Seoul) 고정해서 날짜 경계 이슈 제거.
* **A/B 테스트**: 정렬식(조회 100% vs 조회70%+상품수30%) 실험 후 전개.

---

원하시면:

* ClickHouse/BigQuery용 스키마/머티리얼라이즈드뷰 버전,
* Redis 시간버킷 워커 코드(노드/파이썬),
* “뷰=UV” 정의(세션/쿠키/로그인 기준) 쿼리 예시,
* 개인화(카테고리 선호도, 가격대) 섞은 랭킹 함수도 바로 만들어줄게.


# 트래픽이 많은 경우
트래픽 많은 서비스에서 “상품 목록에 조회수·판매수·장바구니 수”를 **가볍게** 보여주려면, 실시간 증가를 DB에 직접 쓰지 말고 **이벤트 → 캐시(실시간) → 집계 스냅샷(주기적)** 3계층으로 나눠서 서빙하는 게 정석입니다.

---

# 권장 아키텍처(요약)

**수집(Write)**

* 앱/웹에서 발생: `view`, `purchase`, `cart_add`, `cart_remove`
* 비동기 전송: Kafka(또는 큐) → 실시간 카운터( Redis ) + 원천 로그( OLAP: ClickHouse/BigQuery )

**집계(Compute)**

* 1~5분마다 배치(또는 스트리밍)로 기간별 집계 → `product_stats_snapshot` 테이블에 업서트

  * 예: `views_3d`, `sales_7d`, `carts_active`(현재 장바구니 담긴 유저수) 등

**서빙(Read)**

* 목록 API는 `products` ↔ `product_stats_snapshot`만 조인 (OLTP)
* 초핫 상품은 Redis 값(초단위)로 “스냅샷 + δ(델타)” 합산해 미세 보정
* 정렬/페이지네이션은 스냅샷의 사전계산 필드(점수/기간합계) 사용

---

# 스키마/키 설계

## 1) 스냅샷 테이블(OLTP)

```sql
CREATE TABLE product_stats_snapshot (
  product_id        BIGINT PRIMARY KEY,
  views_3d          INT NOT NULL DEFAULT 0,
  sales_7d          INT NOT NULL DEFAULT 0,
  carts_active      INT NOT NULL DEFAULT 0, -- 현재 장바구니에 담긴 유저 수
  total_views       BIGINT NOT NULL DEFAULT 0,
  total_sales       BIGINT NOT NULL DEFAULT 0,
  refreshed_at      DATETIME NOT NULL,
  INDEX (views_3d),
  INDEX (sales_7d),
  INDEX (carts_active)
);
```

> 기간 값(3d/7d 등)은 **정렬/필터**에 필요한 것만 최소로. 더 필요하면 추가 컬럼로 확장.

## 2) Redis 실시간 카운터

* 조회수(빠르게 증가): `INCRBY`
* 판매수(주문 확정 시): `INCRBY`
* 장바구니 수: “유저 단위 증감”이므로 **집합성** 고려

**키 예시**

```
rv:pv:{productId}:{yyyyMMddHH}     -> 뷰 시간버킷 (TTL=72h+여유)
rv:sale:{productId}:{yyyyMMdd}     -> 판매 일버킷 (TTL=8~15d)
rc:cart:u:{userId}                 -> 유저 장바구니(SET or HASH)
rc:cart_count:{productId}          -> 상품별 현재 카트 수(ZSET or HASH로 관리)
```

* 장바구니 수는 두 가지 중 하나:

  1. **실시간**: `rc:cart_count:{productId}` 를 증감(+1/-1) (장바구니에 담김/삭제 시)

     * 중복 방지: 유저별 `rc:cart:u:{userId}`에 productId가 새로 들어오는 경우에만 +1, 이미 있으면 무시.
  2. **정확한 재계산**: 매시간 `rc:cart:u:*` 스캔 or OLAP 집계로 truth를 계산 후 스냅샷 갱신

     * 대규모라면 유저 카트는 **DB/OLAP이 진실**, Redis는 **서빙용 캐시**로 두는 편이 안전.

---

# 집계 파이프라인(배치 예시)

1. **OLAP 집계** (ClickHouse/BigQuery)

* `views_3d = sum(views)` where `occurred_at >= now()-3d`
* `sales_7d = sum(confirmed_orders)` where `paid_at >= now()-7d`
* `carts_active = approx_distinct(user_id where product in cart)` 또는 DB truth 조인

2. **스냅샷 업서트**

```sql
REPLACE INTO product_stats_snapshot (product_id, views_3d, sales_7d, carts_active, total_views, total_sales, refreshed_at)
SELECT
  product_id,
  SUM(views_last_3d)     AS views_3d,
  SUM(sales_last_7d)     AS sales_7d,
  SUM(carts_active_now)  AS carts_active,
  SUM(total_views_all)   AS total_views,
  SUM(total_sales_all)   AS total_sales,
  NOW()
FROM olap_materialized_table
GROUP BY product_id;
```

> **정렬/검색 성능**을 위해 필요한 컬럼은 스냅샷에 **반드시** 넣고 인덱싱하세요.

---

# 목록 API 설계(가벼운 조인만)

```sql
SELECT
  p.id, p.name, p.price,
  s.views_3d, s.sales_7d, s.carts_active
FROM products p
LEFT JOIN product_stats_snapshot s ON s.product_id = p.id
WHERE p.is_active = 1
ORDER BY s.views_3d DESC, p.created_at DESC
LIMIT ? OFFSET ?;
```

* **캐시**: 결과 리스트를 30~120초 캐싱 (카테고리/정렬조건별 키)
* **개인화 리스트**는 캐시 키를 세분화(또는 상위 N만 캐시 + 나머지는 on-demand)

---

# 이벤트 처리(예: Node/Nest, Redis + Lua)

**뷰 카운트 증가 (세션 중복 억제)**

* 30~60초 쿨다운 키: `antidup:pv:{productId}:{sessionId}` (SETEX)
* 처음이면 시간버킷 `INCR rv:pv:{productId}:{yyyyMMddHH}`

**장바구니 증감**

* `SADD rc:cart:u:{userId} {productId}` 가 **새로 추가**될 때만 `HINCRBY rc:cart_count {productId} 1`
* `SREM` 성공 시에만 `HINCRBY rc:cart_count {productId} -1`

> 멀티연산은 Lua 스크립트로 원자화(경합 방지).

---

# “근사치 vs 정확도” 가이드

* **조회수**: 페이지뷰는 근사치 허용(쿨다운, 봇필터). 기간별은 스냅샷 기준 + Redis 델타로 충분.
* **판매수**: 주문 확정 시에만 증가 → 정확치 유지. 취소/환불 반영 루틴 필수.
* **장바구니 수**: “현재 담긴 유저 수”를 보여줄지, “최근 N일 장바구니 추가 수”를 보여줄지 **정의** 먼저.

  * “현재 담긴 유저 수”는 정확도가 중요 → DB truth 주기 반영 + Redis는 캐시.
  * “최근 N일 장바구니 추가 수”는 이벤트 집계(근사 가능).

---

# 성능/운영 팁

* **정렬 컬럼을 스냅샷에 넣고 인덱스**: 목록 쿼리의 조인은 PK 1번 + 인덱스 범위 탐색 1번으로 끝나게.
* **핫키 보호**: 상위 상품은 별도 Redis ZSET에 랭킹 유지(`z:hot:views_3d`), API는 상위 N개를 이 값으로 보정.
* **TTL/용량 관리**: Redis 시간버킷은 72~168h TTL, 스냅샷은 1~5분 단위 리프레시.
* **봇/중복 필터**: 서버단 UA/헤더 체크 + 세션 쿨다운. 프록시/사내아이피 제외.
* **타임존 고정**: KST 기준 집계 윈도우 고정(날짜 경계 혼선 방지).
* **A/B**: 노출 지표(views 70 + sales 30 가중 등) 스코어화 해 실험.

---

# 최소 구현 세트(현실적인 스타트)

1. Redis 시간버킷 + 델타
2. OLAP 일별 집계 → 3d/7d 스냅샷 업서트
3. 목록 API는 `products` + `product_stats_snapshot`만 조회
4. 30~120초 캐시

이렇게 가면 **트래픽 커도** DB에 잦은 UPDATE 없이, 리스트에 **조회수/판매수/장바구니 수**를 안정적으로 보여줄 수 있어요.
원하면 Redis 키/루아 스크립트 샘플이랑, TypeORM/Nest 기준 코드 골격 바로 붙여줄게.
