# Spring Proxy Pattern (스프링 프록시 패턴)

## 1. 프록시 패턴(Proxy Pattern)이란?

프록시(Proxy)는 '대리자'라는 뜻으로, **어떤 객체(Target)를 직접 사용하는 대신, 그 객체를 대행하는 객체(Proxy)를 통해 접근하는 디자인 패턴**입니다.

클라이언트는 실제 객체인 줄 알고 프록시 객체의 메서드를 호출하지만, 실제로는 프록시 객체가 흐름을 제어하고 최종적으로 실제 객체를 호출합니다.

### 1.1. 왜 사용하는가?

1. **접근 제어**: 권한이 없는 사용자의 접근을 막거나, 무거운 객체의 로딩을 지연(Lazy Loading)시킬 수 있습니다.
2. **부가 기능 추가**: 원래 코드를 변경하지 않고 로그 출력, 트랜잭션 관리, 실행 시간 측정 같은 기능을 추가할 수 있습니다. (Spring AOP의 핵심)

---

## 2. Spring에서의 프록시

Spring은 AOP(Aspect Oriented Programming)와 트랜잭션(`@Transactional`) 등을 구현하기 위해 프록시 패턴을 적극적으로 사용합니다.

### 2.1. 동작 흐름

1. **Client**가 메서드 호출 (`service.doSomething()`)
2. **Proxy**가 요청을 가로챔 (Intercept)
3. **Proxy**가 부가 기능 수행 (예: 트랜잭션 시작, 로그 기록)
4. **Proxy**가 실제 **Target** 객체의 메서드 호출 (`target.doSomething()`)
5. **Target**이 비즈니스 로직 수행 후 리턴
6. **Proxy**가 후처리 수행 (예: 트랜잭션 커밋, 종료 로그)
7. **Client**에게 결과 반환

### 2.2. 핵심: "Service는 언제 프록시가 되는가?"

AOP(트랜잭션 포함)가 적용된 Service는 스프링 컨테이너에 **'프록시 객체'**로 등록됩니다.

- **개발자가 작성한 코드**: `MyService` 클래스 (순수 자바 객체, Target)
- **스프링이 빈으로 등록하는 객체**: `MyService`를 감싼 **프록시 객체** (예: `MyService$$CGLIB...`)
- **Controller가 주입받는 객체**: **프록시 객체**

즉, 외부(Controller 등)에서 볼 때는 `Service`가 프록시이지만, `Service` 내부 코드(`this`) 입장에서는 자기 자신(Target)인 것입니다. 이 괴리 때문에 **내부 호출(Self-Invocation)** 문제가 발생합니다.

---

## 3. 프록시 구현 방식 (JDK Dynamic Proxy vs CGLIB)

Spring은 프록시를 생성할 때 두 가지 방식을 상황에 따라 선택해서 사용합니다.

### 3.1. JDK Dynamic Proxy

- **Java 표준 라이브러리**(`java.lang.reflect.Proxy`)에서 제공하는 기능입니다.
- **인터페이스(Interface)**가 반드시 있어야 합니다.
- 프록시 객체는 타겟의 **인터페이스를 구현**하여 만들어집니다.
- Spring AOP 초창기에는 이 방식이 기본이었습니다.

### 3.2. CGLIB (Code Generator Library)

- 오픈소스 바이트코드 조작 라이브러리입니다.
- **클래스(Class)**만 있어도 프록시를 만들 수 있습니다.
- 프록시 객체는 타겟 클래스를 **상속(Extends)**받아 만들어집니다.
- **Spring Boot**에서는 기본적으로 CGLIB를 사용합니다 (`proxyTargetClass=true` 설정이 기본).

### 3.3. 비교 요약

| 특징 | JDK Dynamic Proxy | CGLIB |
| :--- | :--- | :--- |
| **기반 기술** | Java Reflection | Bytecode Generation |
| **대상** | 인터페이스 구현체 | 클래스 (구체 클래스) |
| **생성 방식** | 인터페이스 구현 (Implements) | 클래스 상속 (Extends) |
| **제약 사항** | 인터페이스 필수 | `final` 클래스/메서드 오버라이딩 불가 |
| **성능** | Reflection 사용으로 상대적으로 느릴 수 있음 (최적화 됨) | 바이트코드 직접 조작으로 빠름 |

---

## 4. Spring Boot와 프록시

과거 Spring Framework는 인터페이스가 있으면 JDK Dynamic Proxy를, 없으면 CGLIB를 사용했습니다. 하지만 **Spring Boot 2.0**부터는 **CGLIB가 기본값**으로 설정되었습니다.

**이유:**

- JDK Dynamic Proxy를 쓰면 인터페이스 타입으로만 주입받아야 해서, 구체 클래스 타입으로 의존성 주입(DI)을 받으려 할 때 예외가 발생할 수 있습니다.
- CGLIB의 성능이 많이 개선되었고, 의존성 주입 시 타입 문제에서 더 자유롭기 때문입니다.

---

## 5. 주의사항 (Self-Invocation)

프록시 패턴의 가장 큰 한계점은 **"객체 내부에서 자신의 메서드를 호출할 때(Self-Invocation)"** AOP가 동작하지 않는다는 것입니다.

### 5.1. 왜 동작하지 않는가? (상세 흐름)

AOP가 적용된 빈을 호출할 때의 흐름을 단계별로 살펴보면 이유를 알 수 있습니다.

1. **외부 호출**: 클라이언트가 `myService.outer()`를 호출합니다. 이때 `myService`는 실제 객체가 아니라 **프록시 객체**입니다.
2. **프록시 동작**: 프록시는 `outer()` 메서드에 AOP가 걸려있는지 확인하고, 부가 기능을 수행한 뒤 실제 타겟 객체(`target`)의 `outer()`를 호출합니다.
3. **타겟 진입**: 이제 실행 흐름은 실제 타겟 객체(`MyService` 인스턴스) 내부로 들어왔습니다.
4. **내부 호출**: 타겟 객체의 `outer()` 메서드 안에서 `this.inner()`를 호출합니다.
   - 여기서 **`this`**는 프록시 객체가 아니라, **실제 타겟 객체 자신**을 가리킵니다.
   - 프록시를 거치지 않고 다이렉트로 자신의 메서드를 호출하게 됩니다.
5. **AOP 누락**: `inner()` 메서드에 `@Transactional`이 붙어있더라도, 프록시가 개입할 기회가 없었으므로 트랜잭션 기능이 적용되지 않은 채 순수 자바 코드로만 실행됩니다.

### 5.2. 코드 예시

```java
@Service
public class MyService {

    public void outer() {
        // 여기서 inner()를 호출할 때, 암묵적으로 this.inner()가 호출됨.
        // 'this'는 프록시가 아니라 실제 타겟 객체(Target)임.
        // 따라서 프록시를 거치지 않으므로 @Transactional이 무시됨.
        inner(); 
    }

    @Transactional
    public void inner() {
        // 트랜잭션이 필요한 로직
    }
}
```

### 5.3. 해결 방법

1. **구조 변경 (권장)**: 내부 호출이 발생하지 않도록 `inner()` 메서드를 별도의 서비스 클래스로 분리하고, 그 서비스를 주입받아 사용합니다.
2. **자기 자신 주입 (Self-Injection)**: 자기 자신을 Bean으로 주입받아 호출합니다. (순환 참조 문제로 `@Lazy` 사용 필요)
3. **AopContext 사용**: `((MyService) AopContext.currentProxy()).inner()` 형태로 프록시를 강제로 가져와 호출합니다. (설정 필요, 비권장)
