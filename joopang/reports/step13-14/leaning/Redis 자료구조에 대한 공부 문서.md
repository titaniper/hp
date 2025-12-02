# Redis 자료구조 완벽 가이드

이 문서는 Redis가 제공하는 주요 자료구조의 개념, 내부 동작 원리, 주요 명령어, 그리고 실무 사용 패턴을 상세하게 정리한 가이드입니다. 이 문서 하나로 Redis 자료구조를 마스터하고 실무에 바로 적용할 수 있도록 구성했습니다.

---

## 1. String (문자열)

가장 기본이 되는 자료구조로, 키 하나에 값 하나가 매핑됩니다.

- **특징**: 바이너리 세이프(Binary Safe)하여 문자열뿐만 아니라 JPEG 이미지, 직렬화된 객체 등 어떤 데이터도 저장 가능합니다. (최대 512MB)
- **내부 구조**: 단순 문자열이 아닌 SDS(Simple Dynamic String)라는 구조체를 사용하여 길이 정보를 O(1)로 조회하고 버퍼 오버플로우를 방지합니다.

### 주요 명령어 및 사용법

| 명령어 | 설명 | 시간 복잡도 |
| --- | --- | --- |
| `SET key value [EX seconds] [NX]` | 값을 저장합니다. `EX`로 만료시간, `NX`로 존재하지 않을 때만 저장(락 구현) 가능. | O(1) |
| `GET key` | 값을 조회합니다. | O(1) |
| `MSET / MGET` | 여러 키를 한 번에 저장/조회합니다. 네트워크 RTT를 줄이는 데 효과적입니다. | O(N) |
| `INCR / INCRBY` | 값을 정수로 해석하여 증가시킵니다. (원자성 보장) | O(1) |
| `GETSET key value` | 새 값을 설정하고 이전 값을 반환합니다. | O(1) |

### 실무 상세 예시

#### A. 단순 캐싱 (JSON 객체)

DB 부하를 줄이기 위해 사용자 정보를 캐싱합니다.

```bash
# 사용자 1001의 정보를 1시간(3600초) 동안 캐싱
> SET user:1001 '{"name":"kang", "age":30}' EX 3600
OK

> GET user:1001
"{\"name\":\"kang\", \"age\":30}"
```

#### B. 분산 락 (Distributed Lock)

여러 서버에서 동시에 접근하면 안 되는 리소스를 제어합니다.

```bash
# lock:order:123 키가 없을 때만 설정 (성공 시 1, 실패 시 0 반환)
# 10초 뒤 자동 해제되어 데드락 방지
> SET lock:order:123 "holder_server_1" NX EX 10
OK (락 획득 성공)

> SET lock:order:123 "holder_server_2" NX EX 10
(nil) (락 획득 실패)
```

#### C. 원자적 카운터 (좋아요 수)

동시성 이슈 없이 숫자를 셉니다.

```bash
> SET article:55:likes 0
OK
> INCR article:55:likes
(integer) 1
> INCRBY article:55:likes 10
(integer) 11
```

---

## 2. Hash (해시)

하나의 키 안에 여러 개의 필드(Field)와 값(Value) 쌍을 저장하는 맵 구조입니다.

- **특징**: 객체 하나를 표현하기에 적합합니다. 필드 수가 적을 때는 ZipList(메모리 효율적)로 저장되다가 커지면 Hash Table로 변환됩니다.
- **장점**: 관련된 데이터를 하나의 키로 관리하므로 관리가 용이하고, 개별 필드만 읽고 쓸 수 있어 네트워크 대역폭을 아낄 수 있습니다.

### 주요 명령어 및 사용법

| 명령어 | 설명 | 시간 복잡도 |
| --- | --- | --- |
| `HSET key field value` | 해시에 필드와 값을 저장합니다. | O(1) |
| `HGET key field` | 특정 필드의 값을 조회합니다. | O(1) |
| `HMGET key field1 field2` | 여러 필드의 값을 조회합니다. | O(N) |
| `HGETALL key` | 모든 필드와 값을 조회합니다. (필드가 많으면 주의) | O(N) |
| `HINCRBY key field increment` | 특정 필드의 값을 증가시킵니다. | O(1) |

### 실무 상세 예시

#### A. 사용자 프로필 관리

사용자 객체 전체를 직렬화해서 String으로 저장하는 것보다 필드별 수정이 잦을 때 유리합니다.

```bash
# 사용자 정보 저장
> HSET user:9123 name "Alice" email "alice@example.com" login_count 1
(integer) 3

# 로그인 횟수만 증가 (전체 데이터를 읽고 쓸 필요 없음)
> HINCRBY user:9123 login_count 1
(integer) 2

# 필요한 정보만 조회
> HMGET user:9123 name login_count
1) "Alice"
2) "2"
```

#### B. 장바구니 (Cart)

상품 ID를 필드로, 수량을 값으로 저장합니다.

```bash
# 상품 101을 2개 담음
> HSET cart:user:9123 product:101 2
(integer) 1

# 상품 102를 1개 추가
> HSET cart:user:9123 product:102 1
(integer) 1

# 장바구니 전체 목록 조회
> HGETALL cart:user:9123
1) "product:101"
2) "2"
3) "product:102"
4) "1"
```

---

## 3. List (리스트)

순서가 있는 문자열의 목록으로, Linked List로 구현되어 있습니다.

- **특징**: 양쪽 끝(Head, Tail)에서의 삽입/삭제가 매우 빠릅니다(O(1)). 반면 인덱스로 특정 요소에 접근하는 것은 느립니다(O(N)).
- **용도**: 메시지 큐, 최근 본 목록, 타임라인 등.

### 주요 명령어 및 사용법

| 명령어 | 설명 | 시간 복잡도 |
| --- | --- | --- |
| `LPUSH / RPUSH` | 왼쪽(Head) / 오른쪽(Tail)에 요소를 추가합니다. | O(1) |
| `LPOP / RPOP` | 왼쪽 / 오른쪽에서 요소를 꺼내고 제거합니다. | O(1) |
| `LRANGE key start stop` | 범위 내의 요소를 조회합니다. (전체 조회: 0 -1) | O(S+N) |
| `LTRIM key start stop` | 지정한 범위 밖의 요소를 삭제합니다. (길이 제한 유지) | O(N) |
| `BLPOP / BRPOP` | 리스트가 비어있으면 데이터가 들어올 때까지 대기(Blocking)합니다. | O(1) |

### 실무 상세 예시

#### A. 작업 큐 (Job Queue)

생산자(Producer)가 작업을 넣고, 소비자(Consumer)가 가져갑니다.

```bash
# [Producer] 작업 추가
> LPUSH job:queue "send_email:101"
(integer) 1
> LPUSH job:queue "resize_image:202"
(integer) 2

# [Consumer] 작업 처리 (대기 모드)
# 큐에 데이터가 없으면 최대 10초 대기, 있으면 즉시 리턴
> BRPOP job:queue 10
1) "job:queue"
2) "resize_image:202"  # 먼저 들어간게 아니라 나중에 들어간게 나옴(Stack) -> 큐로 쓰려면 RPUSH/LPOP 조합 사용
```

*Tip: 큐(FIFO)로 쓰려면 `RPUSH`로 넣고 `LPOP`으로 꺼내거나, `LPUSH`로 넣고 `RPOP`으로 꺼내야 합니다.*

#### B. 최근 본 상품 목록 (Capped List)

최근 5개만 유지합니다.

```bash
# 상품 A 조회
> LPUSH recent:view:user:1 "product:A"
> LTRIM recent:view:user:1 0 4  # 0~4번 인덱스(5개)만 남기고 나머지 삭제
OK

# 상품 B 조회
> LPUSH recent:view:user:1 "product:B"
> LTRIM recent:view:user:1 0 4
OK

# 목록 조회
> LRANGE recent:view:user:1 0 -1
1) "product:B"
2) "product:A"
```

---

## 4. Set (집합)

순서가 없고 중복을 허용하지 않는 문자열의 모음입니다.

- **특징**: 데이터의 존재 여부 확인(Membership Check)이 매우 빠릅니다. 교집합, 합집합, 차집합 연산을 서버 측에서 수행할 수 있습니다.
- **용도**: 태그, 팔로워 관리, 유니크 방문자(UV) 집계(데이터가 적을 때).

### 주요 명령어 및 사용법

| 명령어 | 설명 | 시간 복잡도 |
| --- | --- | --- |
| `SADD key member` | 집합에 요소를 추가합니다. (이미 있으면 무시) | O(1) |
| `SREM key member` | 요소를 삭제합니다. | O(1) |
| `SISMEMBER key member` | 요소가 존재하는지 확인합니다. (1: 있음, 0: 없음) | O(1) |
| `SMEMBERS key` | 모든 요소를 반환합니다. (요소가 많으면 주의 - `SSCAN` 권장) | O(N) |
| `SINTER / SUNION / SDIFF` | 교집합 / 합집합 / 차집합을 구합니다. | O(N*M) |

### 실무 상세 예시

#### A. 좋아요 누른 사용자 관리 (중복 방지)

한 사용자가 여러 번 좋아요를 눌러도 카운트는 1번만 되어야 합니다.

```bash
# user:100이 게시글 55에 좋아요
> SADD like:article:55 100
(integer) 1

# user:100이 다시 좋아요 (중복 무시)
> SADD like:article:55 100
(integer) 0

# 좋아요 누른 사람 수
> SCARD like:article:55
(integer) 1

# user:100이 좋아요 눌렀는지 확인
> SISMEMBER like:article:55 100
(integer) 1
```

#### B. 공통 팔로워 찾기 (교집합)

```bash
> SADD followers:user:A "user:1" "user:2" "user:3"
> SADD followers:user:B "user:2" "user:3" "user:4"

# A와 B를 모두 팔로우하는 사람 (교집합)
> SINTER followers:user:A followers:user:B
1) "user:2"
2) "user:3"
```

---

## 5. Sorted Set (ZSet, 정렬된 집합)

Set과 유사하지만, 각 요소가 Score(점수)라는 실수 값을 가집니다.

- **특징**: 요소는 Score를 기준으로 정렬됩니다. Score가 같으면 사전 순(Lexicographical)으로 정렬됩니다. Skip List와 Hash Map을 사용하여 구현되어 있어 삽입/삭제/조회가 모두 빠릅니다.
- **용도**: 리더보드(랭킹), 우선순위 큐, 비율 제한(Rate Limiter).

### 주요 명령어 및 사용법

| 명령어 | 설명 | 시간 복잡도 |
| --- | --- | --- |
| `ZADD key score member` | 점수와 함께 요소를 추가합니다. | O(log N) |
| `ZRANGE key start stop` | 순위 범위로 조회합니다. (점수 낮은 순) | O(log N + M) |
| `ZREVRANGE key start stop` | 역순(점수 높은 순)으로 조회합니다. | O(log N + M) |
| `ZRANK / ZREVRANK` | 특정 요소의 순위를 조회합니다. | O(log N) |
| `ZSCORE key member` | 특정 요소의 점수를 조회합니다. | O(1) |
| `ZINCRBY key increment member` | 점수를 증가시킵니다. | O(log N) |

### 실무 상세 예시

#### A. 게임 리더보드 (실시간 랭킹)

점수가 높은 순서대로 랭킹을 보여줍니다.

```bash
# 점수 등록
> ZADD leaderboard 1000 "player:A"
> ZADD leaderboard 1500 "player:B"
> ZADD leaderboard 800 "player:C"

# player:A가 200점 더 획득
> ZINCRBY leaderboard 200 "player:A"
"1200"

# 상위 3명 조회 (점수 높은 순)
> ZREVRANGE leaderboard 0 2 WITHSCORES
1) "player:B"
2) "1500"
3) "player:A"
4) "1200"
5) "player:C"
6) "800"

# player:A의 현재 등수 (0부터 시작하므로 +1 필요)
> ZREVRANK leaderboard "player:A"
(integer) 1  # 2등
```

#### B. 최근 검색어 (시간순 정렬)

Score를 타임스탬프로 사용하여 최근 검색어를 관리합니다.

```bash
# 현재 시간(Unix Timestamp)을 점수로 사용
> ZADD search:history:user:1 1701480000 "redis"
> ZADD search:history:user:1 1701480100 "kafka"

# 가장 최근 검색어 1개 조회
> ZREVRANGE search:history:user:1 0 0
1) "kafka"
```

---

## 6. Bitmap (비트맵)

String 자료구조의 비트(bit) 하나하나를 제어하는 방식입니다.

- **특징**: 0 또는 1의 상태를 저장할 때 메모리를 극한으로 절약할 수 있습니다. (512MB String 하나로 약 40억 개의 비트 표현 가능)
- **용도**: 일일 활성 사용자(DAU), 출석 체크, 사용자별 기능 ON/OFF.

### 주요 명령어 및 사용법

| 명령어 | 설명 | 시간 복잡도 |
| --- | --- | --- |
| `SETBIT key offset value` | 특정 오프셋의 비트를 0 또는 1로 설정합니다. | O(1) |
| `GETBIT key offset` | 특정 오프셋의 비트 값을 확인합니다. | O(1) |
| `BITCOUNT key` | 1로 설정된 비트의 개수를 셉니다. | O(N) |
| `BITOP operation destkey key...` | AND, OR, XOR, NOT 연산을 수행합니다. | O(N) |

### 실무 상세 예시

#### A. 출석 체크

사용자 ID가 정수라면, 해당 ID를 오프셋으로 사용하여 출석 여부를 저장합니다.

```bash
# 2025년 12월 2일 출석부
# 사용자 ID 5번 출석
> SETBIT attendance:20251202 5 1
(integer) 0
# 사용자 ID 100번 출석
> SETBIT attendance:20251202 100 1
(integer) 0

# 사용자 5번 출석 했나?
> GETBIT attendance:20251202 5
(integer) 1

# 오늘 총 출석 인원 수
> BITCOUNT attendance:20251202
(integer) 2
```

---

## 7. HyperLogLog

집합의 원소 개수(Cardinality)를 추정하기 위한 확률적 자료구조입니다.

- **특징**: 데이터가 아무리 많아도 고정된 메모리(약 12KB)만 사용합니다. 단, 0.81% 정도의 오차가 발생할 수 있습니다.
- **용도**: 정확한 값이 필요 없는 대규모 데이터의 카운팅 (예: 검색어 수, 웹사이트 방문자 수).

### 주요 명령어 및 사용법

| 명령어 | 설명 | 시간 복잡도 |
| --- | --- | --- |
| `PFADD key element...` | 요소를 추가합니다. | O(1) |
| `PFCOUNT key` | 추정된 개수를 반환합니다. | O(1) |
| `PFMERGE destkey sourcekey...` | 여러 HyperLogLog를 병합합니다. | O(N) |

### 실무 상세 예시

#### A. 웹사이트 방문자 수 (UV)

하루에 수백만 명이 방문하는 사이트에서 모든 IP나 UserID를 Set에 저장하면 메모리가 부족할 수 있습니다.

```bash
# 방문자 IP 추가
> PFADD uv:20251202 "192.168.0.1"
(integer) 1
> PFADD uv:20251202 "192.168.0.2"
(integer) 1
> PFADD uv:20251202 "192.168.0.1"  # 중복 방문
(integer) 0

# 총 방문자 수 추정
> PFCOUNT uv:20251202
(integer) 2
```

---

## 8. Stream (스트림)

Redis 5.0에서 추가된 로그 자료구조로, Kafka와 유사한 Append-only Log입니다.

- **특징**: 메시지를 시간 순서대로 저장하고, Consumer Group을 통해 여러 소비자가 메시지를 분산 처리할 수 있습니다. 메시지 영속성과 ACK 처리를 지원합니다.
- **용도**: 이벤트 소싱, 로그 수집, 채팅 메시지 저장.

### 주요 명령어 및 사용법

| 명령어 | 설명 | 시간 복잡도 |
| --- | --- | --- |
| `XADD key ID field value...` | 스트림에 메시지를 추가합니다. ID를 `*`로 하면 자동 생성. | O(1) |
| `XREAD [BLOCK ms] STREAMS key ID` | 메시지를 읽습니다. | O(N) |
| `XGROUP CREATE` | 소비자 그룹을 생성합니다. | O(1) |
| `XREADGROUP GROUP group consumer ...` | 그룹의 소비자로 메시지를 읽습니다. | O(N) |
| `XACK key group ID` | 메시지 처리가 완료되었음을 알립니다. | O(1) |

### 실무 상세 예시

#### A. 비동기 이벤트 처리

주문 생성 이벤트를 스트림에 넣고, 결제 서비스와 배송 서비스가 각자 읽어갑니다.

```bash
# 1. 이벤트 발행 (Producer)
# order_stream에 주문 정보 추가. ID는 자동 생성(*)
> XADD order_stream * type "order_created" user_id 100 product_id 55
"1701480000000-0"

# 2. 소비자 그룹 생성 (최초 1회)
# order_stream의 처음($는 현재 이후, 0은 처음부터)부터 읽을 그룹 'payment_service' 생성
> XGROUP CREATE order_stream payment_service 0 MKSTREAM
OK

# 3. 메시지 읽기 (Consumer)
# payment_service 그룹의 consumer_1이 읽지 않은 메시지(>)를 읽음
> XREADGROUP GROUP payment_service consumer_1 COUNT 1 STREAMS order_stream >
1) 1) "order_stream"
   2) 1) 1) "1701480000000-0"
         2) 1) "type"
            2) "order_created"
            3) "user_id"
            4) "100"

# 4. 처리 완료 알림 (ACK)
# 처리가 끝났으면 ACK를 보내야 PEL(Pending Entries List)에서 제거됨
> XACK order_stream payment_service "1701480000000-0"
(integer) 1
```

---

## 9. Geospatial (지리 정보)

위도와 경도 좌표를 저장하고 거리를 계산하거나 반경 내 요소를 검색합니다. 내부적으로는 Sorted Set을 사용합니다(GeoHash).

### 실무 상세 예시

#### A. 내 주변 맛집 찾기

```bash
# 강남역(127.02, 37.49)과 역삼역(127.03, 37.50) 등록
> GEOADD places 127.0276 37.4979 "Gangnam" 127.0366 37.5007 "Yeoksam"
(integer) 2

# 강남역에서 반경 1km 내 장소 찾기
> GEORADIUS places 127.0276 37.4979 1 km WITHDIST
1) 1) "Gangnam"
   2) "0.0001"
2) 1) "Yeoksam"
   2) "0.8543"  # 약 0.85km 거리
```

---

## 요약: 어떤 자료구조를 써야 할까?

| 상황 | 추천 자료구조 | 이유 |
| --- | --- | --- |
| 단순 키-값 저장, 카운터 | **String** | 가장 기본적이고 빠름. |
| 객체(User, Product) 저장 | **Hash** | 필드별 접근 가능, 메모리 효율적. |
| 큐(Queue), 스택(Stack), 타임라인 | **List** | 양끝 접근 O(1), Blocking 지원. |
| 중복 없는 모음, 교집합/합집합 | **Set** | 집합 연산 최적화. |
| 순위, 랭킹, 우선순위 작업 | **Sorted Set** | 점수 기반 정렬 및 범위 검색. |
| O/X 상태 관리 (출석, 알림설정) | **Bitmap** | 메모리 사용량 최소화. |
| 대용량 유니크 카운팅 (방문자수) | **HyperLogLog** | 적은 메모리로 근사치 계산. |
| 로그 저장, 이벤트 브로커 | **Stream** | 소비자 그룹, ACK, 영속성 지원. |
