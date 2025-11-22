# Spring Dependency Injection (스프링 의존성 주입)

## 1. 의존성 주입(Dependency Injection, DI)이란?

의존성 주입(DI)은 객체가 필요로 하는 의존 객체(Dependency)를 직접 생성하지 않고, **외부(스프링 컨테이너)에서 주입받는 디자인 패턴**입니다. 이를 통해 객체 간의 결합도를 낮추고 유연한 코드를 작성할 수 있습니다.

### 1.1. 제어의 역전 (IoC, Inversion of Control)

- 기존: 개발자가 직접 객체를 생성(`new`)하고 의존성을 연결함.
- 스프링: 프레임워크(컨테이너)가 객체의 생명주기와 의존성을 관리함. 제어권이 개발자에서 프레임워크로 넘어감.

---

## 2. 의존성 주입 방식 3가지

스프링에서 빈(Bean)을 주입받는 방법은 크게 3가지가 있습니다.

### 2.1. 생성자 주입 (Constructor Injection) - **[권장]**

생성자를 통해 의존성을 주입받는 방식입니다.

```java
@Service
public class OrderService {

    private final MemberRepository memberRepository;
    private final DiscountPolicy discountPolicy;

    // @Autowired (생성자가 1개면 생략 가능)
    public OrderService(MemberRepository memberRepository, DiscountPolicy discountPolicy) {
        this.memberRepository = memberRepository;
        this.discountPolicy = discountPolicy;
    }
}
```

#### 왜 권장하는가?

1. **불변성(Immutability)**: 필드를 `final`로 선언할 수 있어, 객체 생성 시점에 의존성이 한 번만 설정되고 변경되지 않음을 보장합니다.
2. **누락 방지**: 컴파일 시점에 필수 의존성이 누락되었는지 확인할 수 있습니다. (생성자 파라미터 강제)
3. **테스트 용이성**: 순수 자바 코드로 단위 테스트를 작성할 때, 생성자를 통해 가짜 객체(Mock)를 쉽게 주입할 수 있습니다.
4. **순환 참조 방지**: 애플리케이션 구동 시점에 순환 참조(A -> B -> A)가 발생하면 에러를 띄워 방지해줍니다.

### 2.2. 세터 주입 (Setter Injection)

수정자(Setter) 메서드를 통해 의존성을 주입받는 방식입니다.

```java
@Service
public class OrderService {

    private MemberRepository memberRepository;

    @Autowired
    public void setMemberRepository(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }
}
```

- **특징**: 선택적인 의존성이나 변경 가능성이 있는 의존성에 사용합니다.
- **단점**: `final`을 사용할 수 없으며, 객체 생성 후 의존성이 주입되지 않은 상태로 메서드가 호출될 위험(NPE)이 있습니다.

### 2.3. 필드 주입 (Field Injection)

필드에 `@Autowired`를 붙여 바로 주입받는 방식입니다.

```java
@Service
public class OrderService {

    @Autowired
    private MemberRepository memberRepository;
}
```

- **특징**: 코드가 간결해서 과거에 많이 사용되었습니다.
- **단점 (비권장 이유)**:
  - 외부에서 의존성을 변경할 방법이 없어 테스트하기 어렵습니다. (DI 프레임워크 없이는 아무것도 할 수 없음)
  - `final` 키워드를 사용할 수 없습니다.
  - 순환 참조 문제를 애플리케이션 구동 시점에 발견하기 어렵습니다.

---

## 3. 롬복(Lombok)을 활용한 생성자 주입

실무에서는 롬복의 `@RequiredArgsConstructor`를 사용하여 생성자 코드를 생략하고 깔끔하게 작성하는 것이 일반적입니다.

```java
@Service
@RequiredArgsConstructor // final이 붙은 필드를 파라미터로 받는 생성자를 자동 생성
public class OrderService {

    private final MemberRepository memberRepository;
    private final DiscountPolicy discountPolicy;
    
    // 생성자 코드 작성 불필요
}
```

---

## 4. 같은 타입의 빈이 2개 이상일 때 (조회 빈 충돌)

`DiscountPolicy` 인터페이스를 구현한 빈이 `FixDiscountPolicy`, `RateDiscountPolicy` 두 개가 있다면, 스프링은 어떤 것을 주입해야 할지 몰라 에러(`NoUniqueBeanDefinitionException`)를 발생시킵니다.

### 해결 방법

1. **`@Autowired` 필드 명 매칭**: 파라미터 이름을 빈 이름으로 변경합니다.

   ```java
   // rateDiscountPolicy라는 이름의 빈을 우선 주입
   public OrderService(DiscountPolicy rateDiscountPolicy) { ... }
   ```

2. **`@Qualifier` 사용**: 주입받는 곳과 빈 등록하는 곳에 식별자를 붙여줍니다.

   ```java
   @Component
   @Qualifier("mainDiscountPolicy")
   public class RateDiscountPolicy implements DiscountPolicy { ... }

   // 주입받는 곳
   public OrderService(@Qualifier("mainDiscountPolicy") DiscountPolicy discountPolicy) { ... }
   ```

3. **`@Primary` 사용 (추천)**: 자주 사용하는 빈에 `@Primary`를 붙이면 우선순위를 가집니다.

   ```java
   @Component
   @Primary // 우선권 가짐
   public class RateDiscountPolicy implements DiscountPolicy { ... }
   ```

---

## 6. 심화: 동작 원리 및 추가 팁

### 6.1. 컴포넌트 스캔 (Component Scan)과 빈 등록

우리가 흔히 쓰는 `@Service`, `@Controller`, `@Repository`는 모두 내부적으로 **`@Component`** 어노테이션을 포함하고 있습니다. (메타 어노테이션)

- **동작 과정**:
  1. 스프링 부트 애플리케이션이 시작되면 `@SpringBootApplication`에 포함된 `@ComponentScan`이 동작합니다.
  2. 설정된 패키지 하위의 모든 클래스를 스캔하여 `@Component`가 붙은 클래스를 찾습니다.
  3. 찾은 클래스들을 스프링 빈(Bean)으로 등록합니다. (기본적으로 싱글톤)

#### `@ComponentScan`이란?

스프링이 빈을 찾을 때 **"어디서부터 찾을지"** 지정하는 어노테이션입니다.

```java
@Configuration
@ComponentScan(
    basePackages = "io.joopang", // 이 패키지 하위를 모두 뒤짐
    excludeFilters = @ComponentScan.Filter(...) // 특정 대상 제외 가능
)
public class AppConfig { ... }
```

> **참고**: 스프링 부트의 시작점인 `@SpringBootApplication` 어노테이션 안에 이미 `@ComponentScan`이 포함되어 있습니다. 그래서 별도 설정 없이도 메인 클래스가 있는 패키지 하위의 모든 빈을 자동으로 찾아줍니다.

### 6.2. `@Autowired` 동작 원리

스프링 컨테이너가 빈을 생성하고 의존성을 주입할 때, 내부적으로 `AutowiredAnnotationBeanPostProcessor`라는 **빈 후처리기(Bean Post Processor)**가 동작하여 주입을 처리합니다.

**빈 조회 순서**:

1. **타입(Type)**으로 조회: 먼저 주입받으려는 타입(예: `MemberRepository`)과 일치하는 빈을 찾습니다.
2. **이름(Name)**으로 조회: 타입이 같은 빈이 2개 이상이면, **필드 이름**이나 **파라미터 이름**과 같은 이름의 빈을 찾습니다.
3. **`@Qualifier` / `@Primary`**: 그래도 해결되지 않으면 설정된 우선순위를 따릅니다.

### 6.3. 빈 생명주기 (Bean Lifecycle) 콜백

의존성 주입이 끝난 직후에 초기화 작업을 하고 싶다면 어떻게 해야 할까요? 생성자 호출 시점에는 아직 의존성 주입이 완료되지 않았을 수도 있습니다(특히 세터/필드 주입의 경우).

- **`@PostConstruct`**: 의존성 주입이 완료된 후 실행되는 메서드에 붙입니다. 초기 데이터 로딩 등에 유용합니다.
- **`@PreDestroy`**: 컨테이너가 종료되기 직전에 실행됩니다. 자원 해제 등에 사용합니다.

```java
@Service
public class MyService {
    
    @PostConstruct
    public void init() {
        // 의존성 주입 완료 후 실행됨 (초기화 로직)
        System.out.println("Initialization done");
    }

    @PreDestroy
    public void close() {
        // 종료 전 실행됨 (자원 해제)
        System.out.println("Closing bean");
    }
}
```

---

## 7. 요약

- **생성자 주입**을 기본으로 사용하자. (`final` 키워드 + `@RequiredArgsConstructor`)
- 필수 의존성은 생성자 주입, 선택적 의존성은 세터 주입을 고려할 수 있다.
- 필드 주입은 테스트와 유지보수에 좋지 않으므로 지양하자.
- `@Service` 등은 `@Component`의 특수화된 형태이며, 컴포넌트 스캔에 의해 자동 등록된다.
