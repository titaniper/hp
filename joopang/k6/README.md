# 설치
```
brew install k6

brew upgrade k6

curl -I http://localhost:8080/actuator/health

# 간단한 테스트로 확인
cd joopang/k6
k6 run --vus 1 --iterations 1 scenarios/coupon-issue.js

SCENARIO=coupon_issue K6_ENV=local k6 run --out --out web-dashboard  --summary-export summary.json --vus 1 k6/main.js


SCENARIO=popular-products K6_ENV=local k6 run --out web-dashboard  --summary-export pp_summary.json --vus 1 k6/main.js


-- http://localhost:5665/ui/?endpoint=/
K6_WEB_DASHBOARD_EXPORT=ppreport.html \
K6_WEB_DASHBOARD=true \
K6_WEB_DASHBOARD_EXPORT=ppreport.html \
SCENARIO=popular-products K6_ENV=local k6 run --out web-dashboard  --summary-export pp_summary.json --vus 5  k6/main.js


전; 3.3k	29.68/s
    3.3k	27.32/s

전; 3.3k	25.68/s
    3.3k	27.84/s



K6_WEB_DASHBOARD_EXPORT=ppreport.html \
K6_WEB_DASHBOARD=true \
K6_WEB_DASHBOARD_EXPORT=ppreport.html \
SCENARIO=purchase-stock K6_ENV=local k6 run --out web-dashboard  --summary-export ps_summary.json --vus 5  k6/main.js




```






 ./scripts/send-single-coupon.sh

## 단일 쿠폰 발급 k6 테스트

`scripts/send-single-coupon.sh` 와 동일한 환경 변수를 사용하는 독립 실행용 k6 스크립트(`k6/send-single-coupon.js`)를 추가했습니다. 기존 `k6/main.js` 시나리오들과 분리돼 있으므로 간단한 수동 검증 시 다음과 같이 사용할 수 있습니다.


```

BASE_URL=http://localhost:8083 k6 run --http-debug=full k6/send-single-coupon.js
BASE_URL=http://localhost:8080 k6 run --http-debug=full k6/send-single-coupon.js
BASE_URL=http://host.docker.internal:8080 k6 run --http-debug=full k6/send-single-coupon.js

sudo brew services stop nginx
```


```bash
# 기본값(1 VU, 1 iteration)으로 단 한 번 발급 요청
k6 run k6/send-single-coupon.js

# 환경 변수로 템플릿/사용자/시크릿/부하 강도 조정
BASE_URL=http://localhost:8080 \
COUPON_TEMPLATE_ID=700 \
COUPON_USER_ID=100 \
X_SECRET_KEY=6BEQMfIRywavqv6tQGJ0H1nMDNW1mzTh \
SINGLE_COUPON_VUS=5 SINGLE_COUPON_ITERATIONS=20 \
k6 run k6/send-single-coupon.js
```

필요 시 `BASE_URL`, `COUPON_TEMPLATE_ID`, `COUPON_USER_ID`, `X_SECRET_KEY` 를 덮어써 다른 서버나 템플릿을 대상으로도 실행할 수 있습니다.


# k6 Performance Tests


이 디렉터리는 주팡 API를 대상으로 한 k6 시나리오를 한 곳에서 관리할 수 있도록 구성돼 있습니다. 다음과 같이 구조화돼 있습니다.

```
k6/
  main.js               # 실행 진입점, 환경 변수로 시나리오 선택
  README.md
  config/               # 환경별 공통 설정 (base URL, 헤더, 임계값 등)
  data/                 # 시나리오에서 공유하는 페이로드/CSV 데이터
  lib/                  # 헬퍼 모듈 (HTTP 클라이언트, config loader, metrics)
  scenarios/            # smoke/load/stress 등 목적별 시나리오
```

## 실행 방법

1. 환경별 설정 파일 작성
   ```bash
   cp k6/config/local.json k6/config/dev.json # 필요 시 복사 후 값 수정
   ```
   - `baseUrl`: 타겟 API의 베이스 URL
   - `headers`: 모든 요청에 공통으로 붙일 헤더 (예: 인증 토큰, 언어)
   - `scenarios`: 각 시나리오별 기본 옵션 (VUs, duration, stages 등)
   - `thresholds`: 전체 테스트에 공통으로 적용할 k6 임계값 표현식

2. (선택) 데이터 파일 준비
   - `k6/data/users.sample.csv` 를 복사해 실제 액세스 토큰, 쿠폰 코드 등을 채웁니다.
   - CSV는 `SharedArray` 로 한 번만 로드되므로 VU 수가 많아도 메모리 사용량이 일정합니다.

3. k6 실행
   ```bash
   # smoke 시나리오 (기본값)
   K6_ENV=local k6 run k6/main.js

   # load 시나리오
   SCENARIO=load K6_ENV=local k6 run k6/main.js

   # 2,000명 동시 러시 (쿠폰 → 주문)
   SCENARIO=rush K6_ENV=local k6 run k6/main.js
   # 필요 시 가변: RUSH_VUS=2500 RUSH_MAX_DURATION=3m SCENARIO=rush K6_ENV=local k6 run k6/main.js
   ```
   - `SCENARIO`: `k6/scenarios` 디렉터리의 파일명과 매칭됩니다.
   - `K6_ENV`: `k6/config/<env>.json` 을 선택합니다 (기본값 `local`).
   - `SCENARIO=rush` 실행 전 `./gradlew flywayMigrate -Pspring.profiles.active=local` 로 V3 시드 데이터를 적용해 사용자/쿠폰/상품을 준비하세요.



### 실행 방법 2
 1. DB 준비
     docker-compose up -d mysql (또는 직접 MySQL을 띄운 뒤) build.gradle.kts의 local 프로파일 환경변수(DB_URL 등 기본값 사용)를 그대로 써도 됩니다.
  2. 스키마+러시 데이터 적재
     앱 루트에서 ./gradlew flywayMigrate -Pspring.profiles.active=local 을 실행하면 V1~V3까지 적용돼 러시용 상품/쿠폰/사용자(1000~2999)가 채워집니다.
  3. 애플리케이션 실행
     SPRING_PROFILES_ACTIVE=local ./gradlew bootRun 으로 서버를 띄워 http://localhost:8080 에서 API가 응답하도록 합니다.
  4. k6 러시 시나리오 실행
     다른 터미널에서:

     SCENARIO=rush K6_ENV=local k6 run k6/main.js

     필요하면 RUSH_VUS(동시 사용자 수)와 RUSH_MAX_DURATION을 환경변수로 덮어쓸 수 있습니다.



4. QPS/TPS 측정
   - k6 요약에 `http_reqs.............: 4000  2000/s` 형태로 초당 처리량이 표기됩니다.
   - `--summary-trend-stats "avg,min,med,p(90),p(95),p(99)"` 옵션을 추가하면 응답시간 퍼센타일을 더 자세히 확인할 수 있습니다.
   - 커스텀 메트릭(`coupon_issue_duration`, `order_creation_duration`)은 InfluxDB/Cloud 출력 시 별도 시계열로 집계됩니다.

- smoke: 아주 가벼운 확인용 시나리오입니다. 1 VU·30초 정도로 핵심 엔드포인트만 순회해 “서비스가 살아 있고 주요 플로우가 응답한다”는 걸 빠르게 확인하는 데 쓰입니다. 장애 감지나 배포 직후 헬스 체크용에 적합합니다.
- load: 정상 트래픽 수준을 모사하는 부하 시나리오입니다. 여러 단계(stage)로 VU를 늘리고 유지하면서 카탈로그·장바구니·쿠폰 검증까지 묶어 실제 사용자 행동을 재현합니다. 응답 시간/에러율 임계값을 검증하고 캐시나 컨커런시 문제를 찾는 데 목적이 있습니다.
- coupon_issue: `/api/coupons/{templateId}/issue` 엔드포인트만 집중적으로 호출해 분산락, 템플릿 잔여 수량 차감, 사용자 중복 검증이 병목 없이 동작하는지 측정합니다. `COUPON_TEMPLATE_ID`, `COUPON_ISSUE_USER_START`, `COUPON_ISSUE_USER_COUNT`로 템플릿/사용자 범위를 덮어쓸 수 있습니다.
- popular_products: `/api/products/top` 인기 상품 목록 조회 전용 시나리오입니다. `POPULAR_PRODUCTS_DAYS`, `POPULAR_PRODUCTS_LIMIT`, `POPULAR_PRODUCTS_CACHE_BYPASS_PERCENT` 로 기간/목록 길이/랜덤 캐시 우회 비율을 조정해 캐시 효과와 백엔드 부하를 동시에 살필 수 있습니다.
- purchase_stock: `/api/products/{id}/stock` → `/api/orders` → `/api/orders/{id}/payment` 플로우 전체를 재현해 재고 확인·차감과 주문 생성·결제를 동시에 검증합니다. `PURCHASE_PRODUCT_ID`, `PURCHASE_PRODUCT_ITEM_ID`, `PURCHASE_QUANTITY`로 타겟 SKU와 주문 수량을 조정할 수 있습니다.

## 실행 예시

### 쿠폰 발급 부하 (coupon_issue)

```bash
# 템플릿 700, 사용자 1000~2999 범위를 사용하는 기본값 실행
SCENARIO=coupon_issue K6_ENV=local k6 run k6/main.js

# 템플릿·사용자 풀 덮어쓰기
COUPON_TEMPLATE_ID=700 COUPON_ISSUE_USER_START=100 COUPON_ISSUE_USER_COUNT=100 SCENARIO=coupon_issue K6_ENV=local k6 run k6/main.js
```

### 상품 구매/재고 부하 (purchase_stock)

```bash
# 상품 400 / 옵션 500, 수량 1로 주문+결제를 반복
SCENARIO=purchase_stock K6_ENV=local k6 run k6/main.js

# 특정 SKU와 수량 지정
PURCHASE_PRODUCT_ID=1000 PURCHASE_PRODUCT_ITEM_ID=1500 PURCHASE_QUANTITY=2 \
  SCENARIO=purchase_stock K6_ENV=staging k6 run k6/main.js
```

> 두 시나리오는 충분한 사용자 더미 데이터가 필요합니다. `./gradlew flywayMigrate -Pspring.profiles.active=local` 로 V3 seed를 적용해 사용자(1000~2999), 부하전용 쿠폰 템플릿(900), 상품/상품옵션(1000/1500)을 채운 뒤 애플리케이션을 띄우고 실행하세요.

## 새 시나리오 추가 가이드

1. `k6/scenarios/<name>.js` 파일을 만들고 `export const options = {...}` 와 `export default function` 을 정의합니다.
2. 공용 헬퍼, 커스텀 메트릭은 `k6/lib` 에 모듈로 추가 후 import 해서 사용합니다.
3. `k6/main.js` 는 파일명을 자동으로 로드하도록 구성돼 있으므로 별도 수정이 필요 없습니다.

## lint/format

k6 스크립트는 ES modules 을 사용합니다. 필요 시 `npm install --save-dev prettier eslint` 후 `k6` 디렉터리 내에서 별도 설정을 둘 수 있습니다.
### 인기 상품 조회 부하 (popular_products)

```bash
# 기본 기간 7일, 상위 20개 기준으로 인기 상품 캐시 부하 관찰
SCENARIO=popular_products K6_ENV=local k6 run k6/main.js

# 3일 이내 상위 10개, 20% 확률로 fresh 파라미터를 붙여 캐시 미스 상황을 섞어 측정
POPULAR_PRODUCTS_DAYS=3 POPULAR_PRODUCTS_LIMIT=10 POPULAR_PRODUCTS_CACHE_BYPASS_PERCENT=20 \
  SCENARIO=popular_products K6_ENV=local k6 run k6/main.js
```

응답이 캐시에서 떨어지는지, Redis miss 비율이 어떤지 확인하려면 APM/로그에서 `fresh` 쿼리 파라미터가 붙은 요청을 필터링해 비교할 수 있습니다.
