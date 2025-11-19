Flyway 설치 + 실제 프로젝트에서 바로 쓸 수 있는 **가장 간단한 사용법**까지 한 번에 정리해줄게.
(초보자용 → 실전용 순서)

---

# ✅ 1. Flyway 설치 방법

Flyway는 세 가지 방식으로 사용할 수 있어:

* CLI (명령어)
* Docker
* Spring Boot 자동 설정

아래에서 가장 많이 쓰는 1) **CLI 방식** + 2) **Docker** 정리해줄게.

---

# 🚀 1. Flyway CLI 설치 (macOS / Linux / Windows)

## macOS (Homebrew)

```bash
brew install flyway
```

확인:

```bash
flyway -v
```

## Linux (Debian/Ubuntu)

```bash
sudo apt-get install flyway
```

## Windows (Scoop)

```powershell
scoop install flyway
```

✔ 설치 끝

---

# 🚀 2. Flyway Docker 실행 (설치 없이 바로 가능)

```bash
docker run --rm \
  -v $(pwd)/sql:/flyway/sql \
  flyway/flyway \
  -url=jdbc:mysql://host.docker.internal:3306/mydb \
  -user=root \
  -password=1234 \
  migrate
```

* `./sql` 폴더에 migration 파일 넣으면 자동 실행됨

---

# ✅ 2. Flyway 폴더 구조

프로젝트에서 Flyway는 기본적으로 다음 폴더를 본다:

```
/project
 └── flyway/
     └── sql/
         ├── V1__init.sql
         ├── V2__add_users.sql
         └── V3__add_orders.sql
```

Spring Boot에서는 `classpath:db/migration`이 기본.

```
src/main/resources/db/migration/
 ├── V1__init.sql
 ├── V2__add_user_table.sql
 └── V3__add_index.sql
```

---

# 🧩 3. Flyway 파일 규칙

파일명 형식:

```
V{version}__{description}.sql
```

예:

```
V1__init.sql
V2__create_users.sql
V3__add_index_to_users.sql
```

규칙:

* `V` 로 시작
* 버전 숫자 (정수, 점 포함 가능: V1.1, V1.2)
* `__` (언더바 두 개)
* 설명 (언더바는 자동으로 공백 처리됨)
* `.sql` 확장자

---

# 🚀 4. 가장 간단한 사용법 (CLI)

### 1) 환경 파일 생성 (옵션)

`flyway.conf`

```conf
flyway.url=jdbc:mysql://localhost:3306/testdb
flyway.user=root
flyway.password=1234
flyway.locations=filesystem:sql
```

### 2) 첫 마이그레이션 파일 생성

`sql/V1__init.sql`

```sql
CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    email VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 3) 실행

```bash
flyway migrate
```

결과 예:

```
Successfully applied 1 migration!
```

---

# 🚀 5. Spring Boot에서 Flyway 사용법 (가장 많이 씀)

### 1) Gradle 의존성

```gradle
dependencies {
    implementation 'org.flywaydb:flyway-core'
}
```

### 2) application.yml 설정

```yaml
spring:
  flyway:
    enabled: true
    locations:
      - classpath:db/migration
    baseline-on-migrate: true
```

### 3) 마이그레이션 파일 만들기

`src/main/resources/db/migration/V1__create_user.sql`

```sql
CREATE TABLE user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL
);
```

스프링 실행하면 자동으로 실행됨.

---

# 👉 자주 쓰는 Flyway 명령어

### 마이그레이션 실행

```bash
flyway migrate
```

### 현재 상태 확인

```bash
flyway info
```

### 실패 시 이전 버전으로 롤백(단, undo 스크립트 필요)

```bash
flyway undo
```

### 초기화(baseline)

```bash
flyway baseline
```

### 전체 리셋(테이블 삭제)

```bash
flyway clean
```

⚠️ 실서비스에서는 `clean` 절대 금지 (drop하게 됨)

---

# 🚀 간단 예제로 완성 세트

## 1) 파일 만들기

`sql/V1__create_users.sql`

```sql
CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

`sql/V2__insert_users.sql`

```sql
INSERT INTO users(username) VALUES ('test1'), ('test2');
```

## 2) 실행

```bash
flyway migrate
```

✔ 끝.

---
\


```
flyway clean && flyway migrate 또는 flyway repair 후 flyway migrate로 다시 적용해 주세요.

  ./gradlew flywayClean flywayMigrate   # Gradle로 실행

```

# 원하면?

* 실무형 Flyway 구조 (DEV/QA/PROD 분리)
* 팀에서 version conflict 안 나게 하는 규칙
* flyway + jOOQ / flyway + Hibernate 전략
* Migrate 자동화 (GitHub Actions, Jenkins Pipeline)

이런 것들도 만들어줄게.
