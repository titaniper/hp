# k6 부하 테스트 학습 노트

프로덕트 API들의 피크 부하를 안정적으로 검증하기 위해 `k6/` 디렉터리를 어떻게 구성했고, 실전 실행 시 어떤 절차와 매개변수를 조합하는지 정리했습니다. 예시 커맨드는 모두 리포지터리 루트 기준입니다.

## 1. 디렉터리 구조 & 핵심 파일

- `k6/main.js`: `SCENARIO` 환경 변수를 읽어 `k6/scenarios/<name>.js`를 동적으로 import 합니다. 기본값은 `smoke`.
- `k6/scenarios/*.js`: Smoke, load, rush, coupon-issue, purchase-stock, popular-products 등의 실제 시나리오. 공통 패턴은 `options` 선언 + `default` 함수 안에서 그룹/검증 처리.
- `k6/lib/`: 재사용 라이브러리. `httpClient.js`는 공통 헤더/에러 체크, `metrics.js`는 커스텀 Trend/Counter, `config.js`는 환경별 JSON (`k6/config/*.json`)을 로드합니다.
- `k6/data/`: CSV/JSON 형태의 입력 데이터. 예) `users.sample.csv`를 복사해 실제 토큰, 쿠폰 코드를 채워 넣습니다.
- `k6/config/<env>.json`: `BASE_URL`, 기본 사용자 수, 임계치(thresholds) 등 환경 변수 세트. `K6_ENV`로 선택.
- 루트 `pp_summary.json` 처럼 `--summary-export` 옵션으로 성능 리포트를 남길 수 있습니다.

## 2. 실행 전 준비 체크리스트

1. **의존성**: `brew install k6` 혹은 최신 버전 업그레이드 `brew upgrade k6`.
2. **환경 설정**: `k6/config/local.json`을 베이스로 dev/stage 값을 복사해 맞춤 설정. Slack webhook, Redis 호스트, 쿠폰 템플릿 ID 등 시나리오 전용 필드를 포함.
3. **데이터 파일**: `k6/data/users.sample.csv` -> `k6/data/users.local.csv` 복사 후 실 계정, 인증 토큰, 테스트 쿠폰을 입력. Rush 시나리오에서 VU마다 다른 사용자를 쓰려면 충분한 row 확보.
4. **백엔드 시드**: 인기상품 API 부하 테스트 전에는 Flyway `V4__popular_products_loadtest.sql`을 적용해 상품/주문 데이터를 채우고 Redis 캐시를 비웁니다.

## 3. k6 옵션/개념 요약

| 용어/옵션 | 의미 | 비고 |
| --- | --- | --- |
| **VU (Virtual User)** | 동시 실행되는 가상의 사용자 스레드. 각 VU는 JS 코드를 순차적으로 실행하지만, 전체 VU 수가 동시에 서버에 요청을 던짐. | `--vus`, `options.vus`, `stages[*].target`. `100 VUs` ≒ 동시 요청 100. |
| **iterations** | 각 VU의 실행 횟수. `--iterations 1000`은 총 1000 루프 후 종료. | `duration`과 병행하면 duration 동안 반복. |
| **duration** | 테스트 시간을 절대값으로 지정. `--duration 5m` → 5분. | stages 없이 단일 duration만으로도 실행 가능. |
| **stages** | ramping-vus executor 설정. 각 stage에 `duration` + `target` VU. | 급격한 부하 변화를 피하고 안정 구간 확보. |
| **ramping-arrival-rate** | VU 대신 RPS(초당 시작 요청 수)를 고정하고 싶을 때 사용. | `options.scenarios`에서 executor `ramping-arrival-rate` 지정. |
| **thresholds** | 메트릭의 합격 조건. `http_req_duration: ['p(95)<350']`. | 실패 시 exit code 99 → CI 실패. |
| **checks** | `check(res, { 'status is 200': r => r.status === 200 })`. | 실패 비율은 summary의 `checks` 섹션에 표시. |
| **Trend / Counter / Gauge / Rate** | 사용자 정의 메트릭 타입. Trend=분포, Counter=누적, Gauge=현재 값, Rate=성공률. | `k6/lib/metrics.js` 예시. |
| **summary-export** | 실행 결과를 JSON으로 저장 (`--summary-export result.json`). | Grafana, jq로 사후 분석. |
| **web-dashboard** | `--out web-dashboard` → `http://localhost:5665`. | 팀원과 실시간 모니터링 시 편리. |

샘플 `export const options`:

```javascript
export const options = {
  scenarios: {
    default: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '2m', target: 50 },
        { duration: '5m', target: 50 },
        { duration: '2m', target: 0 },
      ],
      gracefulRampDown: '30s',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<350'],
    http_req_failed: ['rate<0.01'],
  },
};
```

부하 테스트 유형 예시:

- **Smoke Test**: 기능/인증이 정상인지 확인. 적은 VU, 짧은 duration. 실패 시 빠르게 중단.
- **Load Test**: 서비스에 기대되는 평균/최대 트래픽을 재현. 단계적 `stages`로 ramp-up/steady-state 구성.
- **Stress/Rush Test**: 한계 이상으로 트래픽을 밀어 넣어 병목을 찾음. 우리 repo에서는 `rush` 시나리오가 쿠폰/재고를 동시 발급.
- **Spike Test**: 순간적으로 VU를 급격히 높였다가 다시 낮추어 캐시나 락의 회복력을 확인.

## 4. 실행 패턴

```bash
# smoke (기본값: /api/health, 인기상품 조회 등 가벼운 시나리오)
K6_ENV=local k6 run k6/main.js

# load (점진 상승, 예: VU 50, 10분)
SCENARIO=load K6_ENV=local k6 run --summary-export load_summary.json k6/main.js

# rush (선착순 쿠폰/주문 몰림, 사용자 지정)
COUPON_TEMPLATE_ID=700 RUSH_VUS=1500 RUSH_MAX_DURATION=2m \
  SCENARIO=rush K6_ENV=local k6 run --out web-dashboard k6/main.js

# popular products API 집중 테스트
POPULAR_PRODUCTS_DAYS=3 POPULAR_PRODUCTS_LIMIT=10 \
  SCENARIO=popular_products K6_ENV=local k6 run --vus 100 --duration 5m k6/main.js

# 단일 쿠폰 발급 독립 스크립트 (시나리오 엔트리 없이 빠른 검증)
BASE_URL=http://localhost:8083 k6 run k6/send-single-coupon.js
```

### 환경 변수 주요 항목

- `SCENARIO`: `k6/scenarios` 파일명 (`popular-products.js` → `popular_products`).
- `K6_ENV`: `k6/config/<env>.json` 선택. 파일 내 값은 `config.get('baseUrl')` 형태로 쓰입니다.
- `BASE_URL`, `COUPON_TEMPLATE_ID`, `POPULAR_PRODUCTS_*` 등 시나리오별 파라미터는 `.json` 파일 기본값 + CLI 환경 변수로 override.
- `--out web-dashboard`, `--summary-export <file>` 옵션으로 실시간 대시보드와 결과 파일을 동시에 생성 가능.

## 5. 시나리오별 주안점

| 시나리오 | 목적 | 팁 |
| --- | --- | --- |
| smoke | 배포 직후 기본 헬스체크 | `VU=5`, `duration=30s`로 빠르게 실패 지점 확인 |
| load | 평시 트래픽 재현 | `stages` 구성을 `10m ramp-up -> 10m hold -> 5m ramp-down` 패턴으로 정의 |
| rush | 주문/쿠폰 선착순 | 공유 자원(쿠폰 발급 수, 락)을 Redis/Mysql로 초기화, `SCENARIO=rush` 전용 metric 으로 실패율 모니터 |
| purchase_stock | 재고 소비 | `stock` 감소 API에 Race Condition이 없는지 확인, `memo` 필드에 k6 trace 남김 |
| popular_products | `/api/products/top` 집중 | Redis 캐시 잔류 여부 확인, `POPULAR_PRODUCTS_CACHE_BYPASS_PERCENT`로 캐시 미스 비율 조절 |

## 6. 메트릭 & 임계치(threshold)

- 기본 메트릭: `http_req_duration`, `http_req_failed`, `http_reqs`, `vus`, `data_sent/received`.
- `k6/lib/metrics.js`에서 `new Trend('joopang_api_latency')`, `new Counter('coupon_issuance_failures')` 등 도메인 맞춤 지표 정의.
- `k6/config/<env>.json`의 `thresholds` 영역 예시: `{ "http_req_duration": [ "p(95)<350" ], "http_req_failed": [ "rate<0.01" ] }`.
- 실행 로그 마지막에 나오는 `checks` 통계는 시나리오 내 `check(response, {...})` 호출 결과. 실패 항목은 즉시 서버 로그와 매칭.

## 7. 트러블슈팅

- **401/403 빈번**: `k6/data/users.*.csv` 토큰 만료. 새 토큰으로 교체하거나 시나리오에서 로그인 로직을 추가.
- **Redis 캐시 영향**: 인기상품 테스트 중 캐시가 비워지지 않으면 DB 부하가 기대만큼 나오지 않습니다. 테스트 전 `joopang:popularProducts:*` 키 삭제.
- **Too many open files**: macOS에서 `ulimit -n 65536` 등 파일 디스크립터 상향.
- **Network throttling**: k6 `--vus` 높게 줬는데 TPS가 낮다면 로컬/도커 네트워크 병목. 별도 k6 전용 호스트나 Cloud 실행을 고려.
- **요약 JSON 분석**: `k6 run ... --summary-export result.json` 후 `jq '.metrics.http_req_duration' result.json` 으로 P90/P95 확인.

## 8. 새 시나리오 추가 절차

1. `k6/scenarios/<name>.js` 작성 (`options`, `setup`, `default` 포함). 공용 헬퍼는 `k6/lib/*.js`에서 import.
2. 필요한 환경 변수/데이터 컬럼을 README와 본 문서에 기록하여 Onboarding 부담 최소화.
3. `SCENARIO=<name>`로 로컬 smoke 실행 → CI/CD 단계에 k6 잡 추가 시 `k6/main.js`의 auto-loader 덕분에 코드 수정 불필요.

## 9. 참고 명령 모음

```bash
# Web UI 프로파일링 + Slack 알림 (config 파일 예시에 webhook URL 있음)
SCENARIO=load K6_ENV=stage k6 run --out web-dashboard --summary-export stage_load.json k6/main.js

# 인기상품 캐시 우회 비율 20%로 설정하여 DB 부하 관찰
POPULAR_PRODUCTS_CACHE_BYPASS_PERCENT=20 SCENARIO=popular_products K6_ENV=local k6 run k6/main.js

# Rush 시나리오 VU 동적 조정 (기본 1000 → 2500)
RUSH_VUS=2500 RUSH_MAX_DURATION=3m SCENARIO=rush K6_ENV=local k6 run k6/main.js
```

이 문서를 토대로 k6 테스트를 반복해도 항상 동일한 방법으로 환경을 준비하고 실행 기록(요약 JSON, web-dashboard URL)을 남길 수 있습니다. 필요한 경우 `k6/README.md`와 함께 유지 보수하세요.
