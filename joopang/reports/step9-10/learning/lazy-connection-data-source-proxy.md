
* **“JPA + 단일 DB”만 쓰는 일반 웹 앱** 기준으로는
  👉 *지금 올린 설정만으로 사실상 끝이라고 봐도 돼요.*
* 다만,

  * 다른 곳에서 `DataSource`를 직접 주입 받아 쓰는 코드들(JdbcTemplate, 배치, 별도 TxManager 등)이 있다면 **그쪽에서도 Lazy 프록시를 쓰도록** 정리해야 하고,
  * 커넥션 풀 사이즈/타임아웃 같은 건 여전히 **Hikari 설정으로 별도 튜닝**해야 합니다. (Lazy는 “언제 빌리느냐” 문제일 뿐, “얼마나 빌릴 수 있느냐”는 다른 이야기라서)

이제, 이 설정이 어떤 개념 위에서 돌아가는지 **블로그 글 포맷**으로 한 번 쭉 써볼게요.
(코드는 질문에 준 그대로 사용합니다.)

---

## Spring에서 `LazyConnectionDataSourceProxy`로 커넥션 점유 시간 줄이기

서비스 운영하다 보면, DB 커넥션 풀 모니터링 그래프를 보다가 이런 생각 한 번쯤 들죠.

> “쿼리도 안 나가는데 왜 이렇게 커넥션을 많이 잡아먹지?”

특히 트랜잭션 경계(`@Transactional`)는 넓게 잡혀 있고,
그 안에서 HTTP 호출, 파일 처리, 복잡한 비즈니스 로직을 한참 돌리다가
마지막에 딱 한 번 DB를 치는 구조라면…

* 커넥션은 **트랜잭션 시작 시점**에 빌리고
* 실제 쿼리는 트랜잭션 후반에야 나가니

그 사이 시간 동안 커넥션은 **“아무 일 안 하면서 점유”** 되고 있게 됩니다.

이 글에서는 이 문제를 줄이기 위해 Spring이 제공하는
`LazyConnectionDataSourceProxy`가 **무슨 역할을 하는지**,
그리고 **실제 코드에서는 어떻게 적용하는지**를 정리해봅니다.

---

## 1. 기본 구조 복습: Spring + Hikari + JPA

일반적인 Spring Boot + JPA + Hikari 구조는 대략 이렇게 생겼습니다.

1. `DataSourceProperties` → `HikariDataSource` 생성
2. Spring Boot가 이 `DataSource`를 이용해

   * `EntityManagerFactory`
   * `JpaTransactionManager`
     등을 자동 구성
3. `@Transactional`이 걸린 메서드 진입 시

   * 트랜잭션 매니저가 **커넥션을 풀에서 빌려옴**
   * 트랜잭션 끝날 때까지 유지했다가 반납

문제는 여기서 **“언제 커넥션을 빌리느냐”** 입니다.

* 기본 동작: **트랜잭션 시작 시점**에 커넥션 빌림
* 실제 쿼리 실행은 훨씬 나중일 수 있음

예를 들어 이런 코드가 있다고 가정해볼게요:

```kotlin
@Transactional
fun placeOrder() {
    // 1. 외부 API 호출 (몇 초 걸림)
    callExternalPaymentGateway()

    // 2. 복잡한 비즈니스 로직 계산
    calculateSomethingHeavy()

    // 3. 마지막에 DB INSERT
    orderRepository.save(order)
}
```

기본 설정에서는:

* `placeOrder()` 시작하는 순간 → **Hikari에서 커넥션 빌림**
* 1, 2 단계 동안 실제 DB는 전혀 사용하지 않지만 커넥션은 **점유 상태**
* 3 단계에서 비로소 쿼리가 나감

트래픽이 많아질수록 “하는 일 없는 커넥션”이 많아지고,
커넥션 풀 고갈/대기 시간 증가로 이어질 수 있습니다.

---

## 2. `LazyConnectionDataSourceProxy`가 하는 일

`LazyConnectionDataSourceProxy`는 이름 그대로:

> “커넥션을 **게으르게(lazy)** 빌리는 프록시 DataSource”

입니다.

조금 더 구체적으로:

* 애플리케이션 코드 입장에서는 여전히 `DataSource`처럼 보이지만
* 진짜 `HikariDataSource`를 바로 건드리지 않고
* **“실제로 커넥션이 꼭 필요해지는 순간”** 까지

  * 커넥션 풀에서 커넥션을 빌리지 않도록 미룹니다.

즉, 트랜잭션은 이미 시작되어 있지만,
**DB를 한 번도 건드리지 않는다면 커넥션을 아예 빌리지 않을 수** 있습니다.

### 2-1. 타이밍 비교

**기본 동작 (Lazy 없음)**

1. `@Transactional` 진입
2. 트랜잭션 매니저가 `dataSource.getConnection()` 호출
3. Hikari가 커넥션 하나 빌려줌
4. 트랜잭션 안에서 쿼리 실행 (있을 수도, 없을 수도)
5. 트랜잭션 종료 시 커넥션 반납

**`LazyConnectionDataSourceProxy` 사용 시**

1. `@Transactional` 진입
2. 트랜잭션 매니저가 `lazyDataSource.getConnection()` 호출
3. **여기서 당장 Hikari에 안 가고**, 현재 쓰레드에 “가짜 핸들”만 올려둠 (아직 물리 커넥션 없음)
4. 실제로 쿼리 실행 시 (`EntityManager`, `JdbcTemplate` 등이 내부에서 `getConnection()`을 진짜로 필요로 하는 시점)

   * 그때 비로소 Hikari에서 커넥션을 하나 빌림
5. 트랜잭션 종료 시 커넥션 반납

따라서:

* **DB를 안 쓰는 트랜잭션** → 커넥션 아예 안 빌림
* **DB를 쓰더라도** → 커넥션 점유 시간이 “트랜잭션 전체 시간”이 아니라 “실제 DB 사용 시간”에 더 가깝게 줄어듦

---

## 3. 실제 설정 코드 설명

질문에 주신 코드는 이런 구조입니다:

```kotlin
@Configuration(proxyBeanMethods = false)
@EnableJpaRepositories(basePackages = ["io.joopang"])
class LazyDataSourceConfig {

    @Bean
    fun hikariDataSource(dataSourceProperties: DataSourceProperties): HikariDataSource =
        dataSourceProperties.initializeDataSourceBuilder()
            .type(HikariDataSource::class.java)
            .build()

    @Bean
    @Primary
    fun lazyConnectionDataSourceProxy(hikariDataSource: HikariDataSource): LazyConnectionDataSourceProxy =
        LazyConnectionDataSourceProxy(hikariDataSource)

    @Bean
    @Primary
    fun entityManagerFactory(
        @Qualifier("lazyConnectionDataSourceProxy") dataSource: DataSource,
        builder: EntityManagerFactoryBuilder,
    ): LocalContainerEntityManagerFactoryBean =
        builder
            .dataSource(dataSource)
            .packages("io.joopang")
            .persistenceUnit("main")
            .build()
}
```

### 3-1. `HikariDataSource` 직접 등록

```kotlin
@Bean
fun hikariDataSource(dataSourceProperties: DataSourceProperties): HikariDataSource =
    dataSourceProperties.initializeDataSourceBuilder()
        .type(HikariDataSource::class.java)
        .build()
```

* Spring Boot의 `DataSourceProperties`를 이용해
* 직접 `HikariDataSource` 빈을 만듭니다.
* 이게 실제 물리 커넥션 풀 역할을 하는 “원본” DataSource입니다.

### 3-2. Lazy 프록시를 `@Primary` DataSource로

```kotlin
@Bean
@Primary
fun lazyConnectionDataSourceProxy(hikariDataSource: HikariDataSource): LazyConnectionDataSourceProxy =
    LazyConnectionDataSourceProxy(hikariDataSource)
```

* `LazyConnectionDataSourceProxy`가 원본 `HikariDataSource`를 감싸고
* **`@Primary`**로 지정되어 있기 때문에

  * 애플리케이션 어디에서든 `DataSource`를 주입 받으면
  * 기본적으로 이 Lazy 프록시가 주입 됩니다.

즉, JPA, `JdbcTemplate`, QueryDSL, etc.
**다 이 Lazy 래퍼를 타게 되죠.**

### 3-3. `EntityManagerFactory`도 Lazy DataSource를 사용

```kotlin
@Bean
@Primary
fun entityManagerFactory(
    @Qualifier("lazyConnectionDataSourceProxy") dataSource: DataSource,
    builder: EntityManagerFactoryBuilder,
): LocalContainerEntityManagerFactoryBean =
    builder
        .dataSource(dataSource)
        .packages("io.joopang")
        .persistenceUnit("main")
        .build()
```

* JPA용 `EntityManagerFactory`가 사용할 DataSource를

  * 직접 `lazyConnectionDataSourceProxy`로 지정
* 결과적으로 `JpaTransactionManager`도 이 Lazy DataSource 경로를 타게 됩니다.

> 👉 정리하면:
> “JPA 관련 모든 커넥션 사용 흐름을 Lazy 프록시를 통해서만 가도록 만든 구성”입니다.

---

## 4. 이렇게만 하면 진짜 끝일까?

실무적인 체크리스트로 정리해보면:

### 4-1. 단일 DB + JPA 위주라면? → 거의 끝

* JPA가 메인 데이터 접근 수단이고
* 별도의 커스텀 `DataSourceTransactionManager`나
* Raw JDBC, MyBatis가 `DataSource`를 따로 주입받지 않는 구조라면

👉 **지금 설정만으로 충분한 경우가 대부분입니다.**

### 4-2. 예외/주의 포인트

1. **다른 곳에서 `HikariDataSource`를 직접 주입받는 코드가 있는지**

   ```kotlin
   @Autowired
   lateinit var dataSource: DataSource   // → OK, Lazy 프록시를 받음

   @Autowired
   lateinit var hikariDataSource: HikariDataSource  // → 이건 원본 풀을 직접 씀
   ```

   * `HikariDataSource` 타입으로 직접 주입해서 쿼리를 날리면

     * 그 코드는 Lazy 프록시를 건너뛰게 됩니다.
   * 가능하면 **항상 `DataSource` 타입으로만 주입**하고,

     * 풀 설정/모니터링은 별도의 설정 클래스로 관리하는 방향이 안전합니다.

2. **별도의 트랜잭션 매니저가 있는 경우**

   * 예: 별도의 `DataSourceTransactionManager`를 등록해놓고 쓰는 코드
   * 그 트랜잭션 매니저가 **Lazy 프록시가 아닌 Hikari 원본**을 쓰고 있다면

     * Lazy 효과를 못 봅니다.
   * 이 경우, `TransactionManager`도 Lazy DataSource를 주입받도록 맞춰야 합니다.

3. **배치, 마이그레이션, Flyway, Liquibase 등**

   * 애플리케이션 레벨이 아니라, 툴이나 별도 프로세스에서 DB를 붙는 경우에는,

     * Lazy 프록시가 개입하지 않을 수 있습니다.
   * 보통은 문제 되지 않지만,
     “모든 DB 접근이 Lazy해야 한다”는 전제는 아니므로
     **어디에 Lazy가 적용되고 어디엔 적용되지 않는지 인지**하고 있는 게 좋습니다.

---

## 5. 언제 이게 진짜 도움 될까?

### 5-1. 트랜잭션 범위가 넓고, DB 사용은 뒤쪽에 몰려 있을 때

* 외부 API 호출이 긴 서비스
* 여러 도메인 서비스 로직을 거친 뒤 마지막에만 저장하는 구조
* 이벤트 핸들러에서 다양한 작업을 하다가 마지막에 로그를 DB에 기록하는 경우 등

이런 경우 Lazy를 쓰면:

* **트랜잭션 시작~DB 사용 전까지는 커넥션 미점유**
* DB 고갈 상황을 한 단계 늦출 수 있음
* 풀 사이즈를 너무 크게 키우지 않아도 되는 여유가 생김

### 5-2. “거의 DB를 안 쓰는” 트랜잭션이 많은 경우

* 로깅/모니터링용 트랜잭션
* 캐시 hit가 대부분이라 DB를 잘 안 타는 서비스
* 조건에 따라 DB를 안 건드리고 끝나는 경우가 잦은 비즈니스 로직

이럴 때는:

* 트랜잭션이 있어도 실제 DB를 안 쓰면 **커넥션을 아예 안 빌림**
* 커넥션 풀 리소스 낭비를 확실하게 줄일 수 있습니다.

---

## 6. 오해/주의할 점

마지막으로, `LazyConnectionDataSourceProxy`가 **해결해 주지 않는 것들**도 생각해봐야 합니다.

1. **쿼리 자체가 느린 문제**는 해결 못 함

   * 커넥션을 언제 빌릴지 바꾸는 것이지,
   * 쿼리 성능이나 인덱스 문제를 해결해 주는 건 아닙니다.

2. **풀 사이즈가 절대적으로 부족한 상황**도 그대로

   * 동시 접속 수, 트랜잭션 길이, 쿼리 횟수에 비해
   * Hikari `maximumPoolSize`가 너무 작으면
   * Lazy를 써도 기다리긴 기다립니다.
   * 다만, 불필요하게 오래 점유되던 커넥션이 줄어들어
     “체감”이 좋아질 수는 있습니다.

3. **Open Session In View 패턴과는 별도 이슈**

   * Lazy는 “언제 커넥션을 빌릴지” 문제이고
   * OSIV는 “트랜잭션/영속성 컨텍스트를 뷰 렌더링까지 열어둘지” 문제
   * 둘은 서로 다른 관심사입니다.

---

## 7. 마무리

요약하면:

* Spring + Hikari + JPA 환경에서
* `LazyConnectionDataSourceProxy`를 `@Primary DataSource`로 설정하고
* `EntityManagerFactory`에도 그 DataSource를 물려주는 것만으로,

👉 “**트랜잭션 = 커넥션 점유 시간**”이라는 강한 결합을 느슨하게 만들 수 있습니다.

질문에서 올려준 설정은:

* **단일 DB + JPA 중심 애플리케이션**에서는

  * 거의 “표준”에 가깝게 깔끔한 구성이고,
* 추가로 확인할 것은

  * 다른 DataSource 주입 지점이 Lazy를 타고 있는지,
  * 별도의 트랜잭션 매니저/배치 설정 등에서 원본 Hikari를 직접 쓰지 않는지 정도입니다.

원하면 다음엔 Hikari 설정 튜닝(풀 사이즈, idleTimeout, maxLifetime, leakDetectionThreshold 등)을
“Lazy 프록시와 함께 쓸 때 어떻게 잡으면 좋은지”도 예시 들어서 정리해줄게요.



아주 좋은 질문임.
**“왜 별도 설정도 안 했는데 LazyConnectionDataSourceProxy가 ‘기본 DataSource’처럼 동작하냐?”**
이 부분은 Spring Boot **자동 구성 + @Primary 우선순위 규칙** 때문에 벌어지는 일임.

결론부터 말하면:

> **Spring Boot가 자동으로 만드는 DataSource 자동 구성(auto-config)은
> 네가 직접 만든 @Bean 이 있으면 그 즉시 비활성화된다.**
> 그리고 네가 등록한 `@Primary DataSource`가 전체 앱의 기본 DataSource가 된다.

즉,
“기본 DataSource를 프록시로 갈아치우는 것”이 아니라
“Spring Boot가 기본 DataSource를 만들어주려다가, 네가 먼저 만들어서 포기한 것”임.

이걸 아주 자세히 뜯어서 설명해줄게.

---

# 🔍 Spring Boot가 DataSource를 만들려고 할 때 벌어지는 일

Spring Boot는 보통 이런 자동 설정 클래스를 사용함:

```
DataSourceAutoConfiguration
HikariDataSourceAutoConfiguration
DataSourceTransactionManagerAutoConfiguration
JpaBaseConfiguration (JPA 켜져 있으면)
```

이 클래스들이 하는 일은:

* application.yml에 있는 `spring.datasource.*` 설정 값을 기반으로
* `DataSource`(보통 Hikari)를 하나 만들어서 Bean으로 등록
* 그걸로 `EntityManagerFactory`, `TxManager` 등을 자동 구성

근데 여기에는 아주 중요한 메커니즘이 있음:

---

# ⭐ Spring Boot 자동 구성의 핵심 규칙

> **“동일 타입의 Bean을 사용자가 직접 정의하면 자동 구성은 꺼진다.”**

예를 들어 너가 이렇게 적으면:

```kotlin
@Bean
fun hikariDataSource(...): HikariDataSource
```

Spring Boot는 파악함:

```
사용자가 이미 DataSource를 직접 만들었네?
그럼 내 자동설정은 필요없겠군.
```

그래서 Boot는 **기본 DataSource를 만들지 않음.**

즉, 내 DataSource = 네가 만든 DataSource.

이게 1단계.

---

# 2단계: LazyConnectionDataSourceProxy를 @Primary 로 선언

너의 설정엔 이런 코드가 있음:

```kotlin
@Bean
@Primary
fun lazyConnectionDataSourceProxy(
    hikariDataSource: HikariDataSource
): LazyConnectionDataSourceProxy =
    LazyConnectionDataSourceProxy(hikariDataSource)
```

여기서 @Primary의 의미는:

> **“같은 타입 후보가 여러 개일 때 기본 선택은 이 Bean으로 해라.”**

Spring은 “DataSource 빈이 두 개네?”

* HikariDataSource
* LazyConnectionDataSourceProxy

이 둘은 **둘 다 DataSource 타입임.**

*근데 기본 DataSource가 뭔지 정해야 하니까 @Primary 확인 → Lazy 프록시를 선택.*

그 이후의 JPA 자동 구성, JDBC, 트랜잭션 매니저 구성은 모두 같은 규칙 사용:

* `DataSource`가 필요하다?
* 후보가 둘?
* 그럼 **@Primary 붙은 것(Lazy 프록시)을 주입.**

---

# 🧬 그래서 "기본 DataSource"가 Lazy 프록시가 됨

원래는 원본 HikariDataSource가 기본인데
너는 이렇게 해버림:

| Bean                            | 타입         | 우선순위             |
| ------------------------------- | ---------- | ---------------- |
| `HikariDataSource`              | DataSource | 기본(X)            |
| `LazyConnectionDataSourceProxy` | DataSource | **@Primary(기본)** |

결과적으로:

* Spring Boot 자동설정이 만드는 DataSource는 **아예 생성되지 않음** (네가 직접 bean 등록해서)
* 그 중에서도 기본(Default) DataSource는 **LazyConnectionDataSourceProxy가 됨**
* 그 프록시는 **실제 물리 커넥션을 HikariDataSource에게 위임**

그래서 JPA, JdbcTemplate, QueryDSL 등 모든 DataSource 경로가 Lazy 프록시를 타는 것임.

---

# 🔥 정리: “따로 설정 없이 프록시가 기본처럼 동작하는 이유”

### ✔ 1) Spring Boot는 DataSource를 자동 생성하려고 하지만

네가 먼저 DataSource(Hikari)를 Bean으로 등록했으므로 **자동생성 중지**

### ✔ 2) LazyConnectionDataSourceProxy도 DataSource 타입이고

`@Primary`라서 **‘기본 DataSource’ 역할을 차지**

### ✔ 3) JPA, 트랜잭션, JDBC 모두 DataSource 주입 시

우선순위 규칙에 따라 Lazy 프록시가 주입됨

### ✔ 4) Lazy 프록시는 실제 커넥션이 필요해질 때만

**하위 HikariDataSource에서 커넥션을 빌려 감**

---

# ⚡ 즉, 동작 메커니즘은 간단히 말해 이렇게임

```
너가 직접 hikariDataSource Bean 등록
→ Spring Boot 자동 DataSource 생성 OFF

너가 LazyConnectionDataSourceProxy + @Primary 등록
→ 기본 DataSource = Proxy로 지정

→ 모든 DB 접근이 Lazy 프록시를 통해서만 진행됨
→ 실제 커넥션은 '필요한 순간'에만 가져오게 됨
```

---

원하면
**1) Spring 내부 동작 흐름 (Bean resolution 과정)**
**2) ---

## 8. 심화: Lazy 프록시 내부 구조와 동작 원리

`LazyConnectionDataSourceProxy`가 어떻게 커넥션을 "게으르게" 가져오는지, 그리고 비슷한 역할을 하는 `TransactionAwareDataSourceProxy`와는 무엇이 다른지 내부 구조를 뜯어보겠습니다.

### 8-1. 어떻게 Lazy하게 가져오는가? (구현 원리)

핵심은 **"가짜 커넥션(Proxy Connection)"**에 있습니다.

1. **`getConnection()` 호출 시점**:
   * `LazyConnectionDataSourceProxy`는 실제 DB 커넥션을 맺지 않습니다.
   * 대신, 자바의 `java.lang.reflect.Proxy`를 이용해 만든 **가짜 커넥션 객체**를 즉시 리턴합니다.
   * 이 가짜 객체는 아직 물리적인 DB 연결이 없는 상태입니다.

2. **메서드 호출 가로채기 (InvocationHandler)**:
   * 사용자(트랜잭션 매니저 등)가 이 가짜 커넥션의 메서드를 호출할 때마다, 프록시 내부의 `InvocationHandler`가 이를 가로챕니다.

3. **설정 메서드 캐싱**:
   * `setAutoCommit(false)`, `setTransactionIsolation(...)`, `setReadOnly(true)` 같은 설정 메서드가 호출되면?
   * **DB에 연결하지 않고**, 그냥 메모리에 "이 설정 해야 함"이라고 **기록(Flagging)**만 해둡니다.
   * 아직도 물리 커넥션은 없습니다.

4. **실제 쿼리 실행 시점 (Statement 생성)**:
   * `prepareStatement()`, `createStatement()` 같이 **진짜 DB와 통신해야 하는 메서드**가 호출되면?
   * **그제서야** 원본 `DataSource`(`HikariDataSource`)에서 `getConnection()`을 호출해 물리 커넥션을 가져옵니다.
   * 그리고 아까 메모리에 기록해둔 설정(`autoCommit`, `readOnly` 등)을 물리 커넥션에 적용한 뒤, 쿼리를 수행합니다.

> **요약**: "설정은 메모리에 적어두고, 진짜 쿼리 날릴 때 커넥션 빌려서 한꺼번에 적용한다."

### 8-2. `TransactionAwareDataSourceProxy` vs `LazyConnectionDataSourceProxy`

스프링에는 비슷한 이름의 프록시가 하나 더 있습니다. 바로 `TransactionAwareDataSourceProxy`입니다. 둘은 비슷해 보이지만 목적이 다릅니다.

| 특징 | TransactionAwareDataSourceProxy | LazyConnectionDataSourceProxy |
| :--- | :--- | :--- |
| **주 목적** | **트랜잭션 동기화** | **커넥션 획득 지연** |
| **커넥션 획득 시점** | `getConnection()` 호출 **즉시** | 실제 쿼리 실행 시점 (**지연**) |
| **사용 시나리오** | JPA 없이 MyBatis/JdbcTemplate 등을 혼용할 때 트랜잭션 유지용 | 불필요한 커넥션 점유 시간을 줄이기 위한 성능 최적화용 |
| **기능 포함 여부** | Lazy 기능 없음 | **TransactionAware 기능 포함** |

* **`TransactionAwareDataSourceProxy`**:
  * 스프링 트랜잭션 매니저가 관리하는 커넥션을, 트랜잭션 밖에서 `DataSource.getConnection()`을 직접 호출하더라도 **똑같은 커넥션을 리턴**해주기 위해 존재합니다.
  * 즉, "이미 트랜잭션 중이면 그 커넥션 재사용해라"라는 기능입니다.
  * 하지만 **Lazy 로딩은 안 합니다.** 부르자마자 바로 가져옵니다.

* **`LazyConnectionDataSourceProxy`**:
  * `TransactionAwareDataSourceProxy`의 기능을 **기본적으로 포함**하고 있습니다.
  * 거기에 더해 **"커넥션 획득을 최대한 미루는 기능"**이 추가된 상위 호환 버전이라고 볼 수 있습니다.

### 8-3. 결론

우리가 사용하는 `LazyConnectionDataSourceProxy`는:

1. **트랜잭션 동기화**도 알아서 해주고 (Transaction Aware)
2. **커넥션도 최대한 늦게** 빌려줍니다 (Lazy Loading)

따라서 성능 최적화와 트랜잭션 안전성을 모두 챙길 수 있는 아주 강력한 도구입니다.
도 설명해줄 수 있음.
