# Self-Invocation (자기 호출) 문제 정리

## 1. 무엇이 문제인가?
- Spring의 AOP(트랜잭션, 캐시, 보안 등)는 프록시 객체를 통해 동작한다.
- 같은 클래스 내부에서 `this.someMethod()`처럼 **자기 자신을 직접 호출**하면 프록시를 거치지 않는다.
- 결과적으로 `@Transactional`, `@Async`, `@Cacheable` 등의 어노테이션이 적용되지 않는다.

## 2. 발생 시나리오
```kotlin
@Service
class OrderService(
    private val repository: OrderRepository,
) {

    fun publicMethod() {
        // self-invocation
        internalTransactionalMethod()
    }

    @Transactional
    fun internalTransactionalMethod() {
        repository.save(...)
    }
}
```
- `publicMethod()`를 호출하면, 내부에서 호출되는 `internalTransactionalMethod()`는 AOP 프록시를 거치지 않기 때문에 트랜잭션이 시작되지 않는다.

## 3. 해결 방법
1. **메서드 분리**: 동일 빈 내에서 호출하지 말고, 별도의 컴포넌트/서비스로 분리하여 주입 후 호출한다.
2. **자기 자신 주입**: 동일 타입의 프록시를 주입받아 호출한다.
   ```kotlin
   @Lazy
   @Autowired
   private lateinit var self: OrderService

   fun publicMethod() {
       self.internalTransactionalMethod()
   }
   ```
   단, 순환 참조에 주의.
3. **AspectJ 컴파일타임/로더타임 위빙**: 프록시가 아닌 바이트코드 위빙을 사용하면 self-invocation 문제를 피할 수 있으나 설정이 복잡하다.

## 4. 확인 방법
- 로그에서 `Creating new transaction` 메시지가 나오지 않는다면 self-invocation을 의심.
- `AopContext.currentProxy()`로 현재 호출이 프록시인지 검사할 수도 있다. (`expose-proxy=true` 필요)

## 5. 설계 체크리스트
1. **@Transactional 메서드는 외부에서 호출되도록 클래스 책임을 나눈다.**
2. **팩토리/팩토리 메서드 패턴**으로 내부 호출을 없앤다.
3. **@TransactionalEventListener**처럼 대안 메커니즘을 활용하여 로직을 이벤트로 분리한다.
