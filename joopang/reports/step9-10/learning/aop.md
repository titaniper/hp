# AOP (Aspect-Oriented Programming) 심화 정리

## 1. 핵심 개념 (Core Concepts)

AOP는 **관심사의 분리(Separation of Concerns)**를 통해 코드의 중복을 줄이고 유지보수성을 높이는 프로그래밍 패러다임입니다. 핵심 비즈니스 로직(Core Concern)과 부가 기능(Cross-cutting Concern)을 분리하여 모듈화합니다.

### 주요 용어

- **Target**: 부가 기능이 적용될 대상 객체 (핵심 비즈니스 로직을 가진 객체).
- **Aspect**: 여러 객체에 공통으로 적용되는 기능(횡단 관심사)을 모듈화한 것. (Advice + Pointcut)
- **Join Point**: Aspect가 적용될 수 있는 실행 지점.
  - *Spring AOP는 프록시 방식을 사용하므로 **메서드 실행 시점**만 지원합니다.*
- **Advice**: 특정 Join Point에서 실행되는 실제 부가 기능 코드.
  - `@Before`: 메서드 실행 전
  - `@AfterReturning`: 메서드 성공 실행 후
  - `@AfterThrowing`: 예외 발생 시
  - `@After`: (성공/실패 여부와 상관없이) 메서드 실행 후 (finally와 유사)
  - `@Around`: 메서드 실행 전후 제어, 예외 처리 등 가장 강력한 기능
- **Pointcut**: Advice를 적용할 Join Point를 선별하는 표현식.
  - 예) `execution(* io.joopang..*Service.*(..))`
- **Weaving**: Aspect를 Target에 적용하여 Proxy 객체를 생성하는 과정.
  - Spring AOP는 **Runtime Weaving** (프록시 생성) 방식을 사용합니다.
  - AspectJ는 Compile-time, Load-time Weaving도 지원합니다.

## 2. Spring AOP 동작 원리: 프록시 (Proxy)

Spring AOP는 **프록시 패턴**을 기반으로 동작합니다. 클라이언트가 Target 객체를 직접 호출하는 것이 아니라, 프록시 객체를 호출하면 프록시가 부가 기능을 수행하고 Target을 호출합니다.

> **상세 내용**: 프록시 패턴의 상세한 동작 원리와 JDK Dynamic Proxy vs CGLIB 비교는 [Spring Proxy Pattern (스프링 프록시 패턴)](./proxy-pattern.md) 문서를 참고하세요.

### JDK Dynamic Proxy vs CGLIB (요약)

Spring은 상황에 따라 두 가지 프록시 방식을 사용합니다.

| 구분 | JDK Dynamic Proxy | CGLIB (Code Generator Library) |
| :--- | :--- | :--- |
| **대상** | 인터페이스가 있는 경우 | 인터페이스가 없거나 구체 클래스를 강제할 때 |
| **기술** | Java Reflection API | 바이트코드 조작 (상속 기반) |
| **Spring Boot** | 과거 기본값 | **Spring Boot 2.0 이상 기본값** |

> **Note**: CGLIB는 상속을 사용하므로 `final` 클래스나 `final` 메서드에는 AOP를 적용할 수 없습니다.

## 3. 구현 예시 (Logging Aspect)

### 3.1. 커스텀 어노테이션 정의

특정 메서드에만 로깅을 적용하기 위해 마커 어노테이션을 만듭니다.

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LogExecutionTime {
}
```

### 3.2. Aspect 구현

`@Around`를 사용하여 메서드 실행 시간을 측정하는 Aspect입니다.

```java
@Slf4j
@Aspect
@Component
public class LogAspect {

    @Around("@annotation(LogExecutionTime)") // 위에서 만든 어노테이션이 붙은 메서드 타겟
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();

        log.info("[Start] {}", joinPoint.getSignature());

        try {
            // 실제 타깃 메서드 실행
            return joinPoint.proceed();
        } finally {
            long executionTime = System.currentTimeMillis() - start;
            log.info("[End] {} executed in {} ms", joinPoint.getSignature(), executionTime);
        }
    }
}
```

### 3.3. 적용

```java
@Service
public class OrderService {

    @LogExecutionTime // AOP 적용
    public void createOrder(OrderDto dto) {
        // 핵심 비즈니스 로직
        try {
            Thread.sleep(1000); 
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
```

## 4. 주의사항: 내부 호출 (Self-Invocation) 문제

Spring AOP의 가장 흔한 함정입니다. 프록시 객체를 거치지 않고, 객체 내부에서 자신의 메서드를 호출(`this.method()`)하면 AOP가 적용되지 않습니다.

### 문제 상황

```java
@Service
public class MemberService {

    public void externalCall() {
        // this.internalCall()과 같음. 프록시를 거치지 않고 인스턴스의 메서드를 직접 호출함.
        internalCall(); 
    }

    @Transactional // AOP 적용 안됨!
    public void internalCall() {
        // ...
    }
}
```

### 해결 방법

#### 1. 자기 자신 주입 (Self-Injection)

Spring Bean은 싱글톤이므로 자기 자신을 주입받을 수 있습니다. 단, 순환 참조 문제가 발생할 수 있으므로 `@Lazy`나 `@Autowired`를 필드 주입으로 사용해야 합니다(생성자 주입 불가).

```java
@Service
public class MemberService {

    @Autowired
    @Lazy // 순환 참조 방지
    private MemberService self;

    public void externalCall() {
        // this.internalCall() 대신 self를 사용
        self.internalCall(); 
    }

    @Transactional
    public void internalCall() {
        // ...
    }
}
```

#### 2. 구조 변경 (권장)

가장 깔끔한 방법은 내부 호출이 필요한 메서드를 **별도의 서비스 클래스로 분리**하는 것입니다.

```java
@Service
@RequiredArgsConstructor
public class MemberService {
    private final InternalMemberService internalMemberService;

    public void externalCall() {
        internalMemberService.internalCall();
    }
}

@Service
public class InternalMemberService {
    @Transactional
    public void internalCall() {
        // ...
    }
}
```

#### 3. AopContext 사용

`AopContext.currentProxy()`를 통해 현재 프록시 객체를 가져와 호출합니다.

- 설정: `@EnableAspectJAutoProxy(exposeProxy = true)` 필요.
- 단점: Spring API에 의존적이게 되며, 이식성이 떨어짐.

```java
@Service
public class MemberService {
    public void externalCall() {
        ((MemberService) AopContext.currentProxy()).internalCall();
    }

    @Transactional
    public void internalCall() { ... }
}
```

## 5. AOP 중첩 적용 시 주의사항 (AOP Nesting & Ordering)

하나의 메서드에 `@Transactional`, `@Cacheable`, `@Retryable` 등 여러 AOP 어노테이션이 동시에 붙을 경우, **Aspect 실행 순서**가 매우 중요합니다. 순서가 잘못되면 의도치 않은 버그가 발생합니다.

### 5.1. @Transactional vs @Retryable

**시나리오**: DB 데드락이나 일시적 오류 발생 시 트랜잭션을 통째로 재시도하고 싶음.

- **올바른 순서**: `@Retryable` (Outer) -> `@Transactional` (Inner)
  - 재시도할 때마다 **새로운 트랜잭션**이 생성되어야 깔끔하게 재시도 가능.
- **잘못된 순서**: `@Transactional` (Outer) -> `@Retryable` (Inner)
  - 이미 시작된 트랜잭션 안에서 예외가 발생하면, 해당 트랜잭션은 `rollback-only`로 마킹될 수 있음.
  - 재시도 로직이 성공하더라도, 상위 트랜잭션이 커밋될 때 `TransactionSystemException` (Rollback marked) 발생 가능.

### 5.2. @Transactional vs @Cacheable

**시나리오**: DB 조회 결과를 캐싱.

- **권장 순서**: `@Cacheable` (Outer) -> `@Transactional` (Inner)
  - Cache Hit 시 트랜잭션을 시작할 필요가 없음 (성능 이점).
- **주의**: `@Transactional`이 Outer인 경우
  - Cache Miss -> 트랜잭션 시작 -> DB 조회 -> 캐시 저장 -> 커밋.
  - 캐시 저장 중에 예외가 터지면 트랜잭션이 롤백되어야 하는가? 등 복잡도 증가.
  - 불필요하게 트랜잭션을 오래 잡고 있을 수 있음.

### 5.3. @Async vs @Transactional

- **문제**: `@Async`는 별도 스레드에서 실행됨.
- **@Transactional (Outer) -> @Async (Inner)**:
  - 호출자 스레드에서 트랜잭션 시작 -> 비동기 메서드 호출(다른 스레드) -> 호출자 트랜잭션 커밋.
  - **비동기 스레드는 호출자의 트랜잭션에 참여할 수 없음.** (ThreadLocal 기반이므로)
  - 비동기 작업 도중 예외가 발생해도 호출자 트랜잭션은 이미 커밋되었거나 롤백되지 않음.
- **해결**: 비동기 메서드 내부에서 독자적인 `@Transactional`을 선언해야 함.

### 5.4. 순서 제어 방법

Spring의 AOP 순서는 `Ordered` 인터페이스 값으로 결정됩니다 (낮은 값이 우선순위 높음 = Outer).

```java
@EnableTransactionManagement(order = Ordered.LOWEST_PRECEDENCE) // 기본값
@EnableRetry(order = Ordered.LOWEST_PRECEDENCE - 1) // 트랜잭션보다 먼저 실행되도록 설정
```

## 6. Pointcut 표현식 가이드

- `execution(* io.joopang.service..*(..))`: `io.joopang.service` 패키지 하위의 모든 메서드.
- `within(io.joopang.service.UserService)`: 특정 클래스 내부의 모든 메서드.
- `@annotation(io.joopang.annotation.MyAnnotation)`: 특정 어노테이션이 붙은 메서드.
- `bean(*Service)`: 이름이 Service로 끝나는 모든 빈.

## 7. 설계 및 테스트 팁

1. **순서 제어 (`@Order`)**: 여러 Aspect가 동시에 적용될 때 순서가 중요하다면 `@Order`를 클래스 레벨에 적용하여 순서를 명시해야 합니다. (낮은 숫자가 먼저 실행됨).
2. **예외 번역**: Repository에서 발생하는 DB 예외를 Service 계층의 비즈니스 예외로 변환할 때 `@AfterThrowing`을 유용하게 쓸 수 있습니다.
3. **테스트**: `@SpringBootTest`는 무겁습니다. AOP 로직만 테스트하고 싶다면, Aspect 클래스만 빈으로 등록하거나 일반 단위 테스트에서 `Aspect` 객체를 직접 생성해서 테스트하는 방법도 고려하세요.
