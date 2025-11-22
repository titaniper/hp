/## Testcontainers & JPA 환경 정리 (2025-11-21)

- **`@EnableJpaRepositories` 제거 배경**
  - `LazyDataSourceConfig`에 `@EnableJpaRepositories(basePackages = ["io.joopang"])`가 붙어 있으면 스프링 부트의 기본 JPA 자동설정보다 먼저 리포지토리를 수동 등록하려고 시도한다.
  - 이 타이밍에는 엔티티 스캔/엔티티 매니저 팩토리 초기화가 아직 끝나지 않아, `CartItem` 등 엔티티 메타데이터가 미등록 상태라 “Not a managed type” 예외가 터진다.
  - 스프링 부트는 `@SpringBootApplication` 기준으로 이미 동일 패키지를 자동 스캔하기 때문에, 커스텀 애노테이션이 오히려 초기화 순서를 어긋나게 만든 셈.
  - 부트가 기본 스캔 경로(`io.joopang`)를 이미 알고 있으므로 커스텀 설정을 제거하고 자동설정에 맡겨 문제를 해소했다.

- **`SnowflakeIdGeneratorInitializer` 추가 이유**
  - `BaseEntityListener`가 companion object 안에서 `lateinit var snowflakeIdGenerator`를 사용하고 있었는데, JPA가 Listener를 인스턴스화하는 시점에는 의존성이 아직 채워지지 않아 `UninitializedPropertyAccessException`이 터짐.
  - `SnowflakeIdGeneratorInitializer`를 `@Component`로 두고 `@PostConstruct`에서 Listener에 Generator를 주입해 두면 JPA가 Listener를 호출할 때 이미 안전하게 초기화된 상태가 된다.

- **테스트 프로파일에서 H2 설정을 유지한 이유**
  - `spring.sql.init`이 seed 데이터를 넣을 때 Testcontainers의 `JdbcConnectionDetails`가 준비돼 있지 않으면 Hikari가 연결 정보를 몰라 `Failed to determine a suitable driver class` 에러가 발생한다.
  - `application-test.yml`에 H2 기본 설정을 넣어 두면 초기화 단계에서 최소한의 드라이버/URL이 보장되고, 이후 Testcontainers가 올라오면 `LazyDataSourceConfig`가 MySQL 정보로 덮어쓴다.
  - 즉, H2 설정은 fallback이며 실제 통합 테스트는 계속 MySQL 컨테이너를 사용한다.

- **`withTmpFs`를 MySQL 컨테이너에 붙인 이유**
  - macOS Docker Desktop 환경에서 `/var/lib/mysql`이 overlayfs에 쌓이면서 `no space left on device` 오류로 컨테이너가 자주 죽었다.
  - `withTmpFs(mapOf("/var/lib/mysql" to "rw,size=256m"))` 옵션으로 데이터 디렉터리를 메모리 기반(tmpfs)으로 올리면 I/O가 끝나면 자동으로 버려져 디스크 누수를 막을 수 있다.

- **`Order.discounts`를 `MutableSet`으로 바꾼 이유**
  - `OrderRepository`가 `@EntityGraph(attributePaths = ["items", "discounts"])`로 두 개의 bag 컬렉션을 동시에 fetch할 때 Hibernate가 `MultipleBagFetchException`을 던진다.
  - discounts를 `MutableSet`으로 바꾸면 하나는 bag(List), 다른 하나는 set으로 인식돼 동시에 fetch가 가능해지고, OrderService 통합 테스트에서 더 이상 예외가 발생하지 않는다.
  - 다른 대안: 한 컬렉션만 eager fetch하고 나머지는 분리된 쿼리로 읽거나, `@BatchSize`/`FetchMode.SUBSELECT` 같은 Hibernate 전용 옵션으로 batch fetch를 구성하는 방법도 있다. 하지만 discounts의 순서/중복 요구사항이 없어 set 전환이 가장 부담이 적었다.
