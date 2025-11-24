아래는 **더 읽기 쉽고 구조적으로 정돈된 형태**로 재작성한 요약본입니다.
톤은 **멘토 리뷰 문서 스타일**을 유지하면서, 핵심 포인트만 명확히 정리했습니다.

---


## 👍 잘한 점

### 1. 체계적인 도메인 설계

* DDD 원칙을 잘 따르고 있으며 엔티티와 서비스 레이어가 명확하게 분리됨.
* `Order`, `OrderItem`, `OrderDiscount` 등 도메인 모델이 비즈니스 로직을 크게 훼손 없이 잘 표현됨.

### 2. 동시성 제어 고려

* `ProductLockManager`를 활용해 재고 관리 동시성 문제를 신경쓴 점이 좋음.
* 비관적/낙관적 락 사용 목적을 이해하려는 시도가 보임.

### 3. 테스트 코드 작성

* 단위·통합 테스트 모두 AssertJ 기반으로 잘 작성됨.
* 테스트 구조가 비교적 명확함.

### 4. 불변 객체(Value Object) 활용

* `Money`, `Quantity`, `StockQuantity` 등을 Value Object로 설계해 도메인 의도가 잘 드러남.

### 5. BaseEntity 활용

* `id` 등 공통 필드를 BaseEntity로 추출한 점이 좋음.
* 실무에서는 `createdAt`, `updatedAt`도 기본적으로 포함되므로 함께 추가되면 더 좋음.

### 6. LazyConnectionDataSourceProxy 활용

* 트랜잭션이 실제로 필요할 때에만 DB 커넥션을 가져오도록 구성한 점이 인상적.
* 불필요한 커넥션 점유를 줄이는 데 도움이 됨.

### 7. 보고서 정리 능력

* AOP, 낙관적 락의 동작 원리 등 멘토링 내용이 잘 정리됨.
* 개념량이 방대하므로 지속적인 복습 추천.

---

## ✏️ 개선할 점

### 1. 테스트 코드 실패

* 이번 변경은 “비즈니스 변화”가 아니라 “인프라 기술 변경”이므로 기존 테스트는 그대로 통과해야 함.
* 테스트가 구현에 너무 강하게 결합되어 있어, 테스트 독립성을 높이도록 리팩토링 필요.
* 참고: [https://toss.tech/article/test-strategy-server](https://toss.tech/article/test-strategy-server)

### 2. BaseEntity의 ID nullable

* JPA 특성상 영속화 전에는 id가 null이지만, **도메인 모델 관점에서는 id는 항상 존재하는 값**이므로 nullable 지양.
* 영속화 전에는 `0` 또는 `-1`과 같은 sentinel value로 처리하는 패턴도 있음.

### 3. LoggerFactory 직접 사용

* `LoggerFactory.getLogger()` 직접 사용보다 Lombok의 `@Slf4j` 또는 Kotlin의 `kotlin-logging` 사용을 권장.
* 코드 간결성과 일관성 향상.

### 4. Mutable Collection 노출

* `items`, `discounts`가 `MutableList`로 그대로 노출되고 있어 외부 수정 가능 → 캡슐화 위반.
* 읽기 전용 리스트로 반환하는 방식 필요.

```kotlin
private val _items = mutableListOf<OrderItem>()
val items: List<OrderItem> get() = _items.toList()
```

### 5. 반복되는 패턴 리팩토링

* `findById().orElseThrow()` 형태가 반복됨.
* Repository 공통 메서드로 추출하면 코드 중복과 예외 처리 일관성이 개선됨.

### 6. 개행 및 코드 스타일

* import–class, 메서드 간 개행 등이 파일별로 일관되지 않음.
* 통일된 스타일 적용 시 가독성 향상.

### 7. JPA 연관관계

* 실무에서는 복잡성 문제로 연관 관계 매핑을 최소화하는 추세.
* 학습 단계에서는 지금처럼 사용해보는 것이 의미 있지만, 실무에서는 유의 필요.

### 8. 과제 진도 방향성

* K6 등 다양한 도전을 하는 점은 좋으나, 당장은 Java/Kotlin, Spring Boot, RDBMS, 테스트 코드 등 **핵심 기본기**에 더 집중하면 더 큰 성장 가능.
* 부하 테스트 등은 이후 발제에서 다룰 예정이니 우선순위를 조절하는 것이 좋음.

---

필요하시면 **다른 문체로 변환**(예: 회사 인사 리뷰 스타일, 교육 기관 평가 스타일)하거나
**요약본 버전(초간단 / 한 장 요약)**으로도 만들어드릴게요.
