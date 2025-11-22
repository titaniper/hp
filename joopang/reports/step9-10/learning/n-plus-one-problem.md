# JPA N+1 문제와 연관관계 매핑 전략

## 1. N+1 문제란?

**N+1 문제**는 ORM(JPA)을 사용할 때 가장 빈번하게 발생하는 성능 문제입니다.
연관 관계가 설정된 엔티티를 조회할 때, **조회된 데이터 갯수(N)만큼 연관된 데이터를 조회하기 위해 추가적인 쿼리(N개)가 발생하는 현상**을 말합니다.

* **1**: 최초 조회 쿼리 (예: `SELECT * FROM ORDER`)
* **N**: 조회된 각 Row마다 연관된 엔티티를 가져오기 위한 추가 쿼리 (예: `SELECT * FROM MEMBER WHERE ID = ?`)

### 1-1. 발생 예시

```java
@Entity
class Order {
    @Id @GeneratedValue
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) // 혹은 EAGER
    private Member member;
}
```

```java
// 1. 주문 목록 조회 (쿼리 1번 실행) -> 결과가 100개라고 가정
List<Order> orders = orderRepository.findAll(); 

// 2. 각 주문의 회원 정보 접근 (여기서 N+1 발생)
for (Order order : orders) {
    System.out.println(order.getMember().getName()); // 루프 돌 때마다 SELECT 쿼리 발생 (총 100번)
}
```

결과적으로 **1번의 요청을 처리하기 위해 101번(1 + 100)의 쿼리가 실행**되어 DB 부하가 급증합니다.

---

## 2. 해결 방법 및 적절한 사용 케이스

### 2-1. Fetch Join (패치 조인)

JPQL을 사용하여 조회 시점에 연관된 엔티티까지 **한 번의 쿼리(INNER JOIN)**로 가져오는 방법입니다.

```java
@Query("SELECT o FROM Order o JOIN FETCH o.member")
List<Order> findAllWithMember();
```

* **동작**: `SELECT o.*, m.* FROM Order o INNER JOIN Member m ON o.member_id = m.id`
* **장점**: 한 방 쿼리로 해결되므로 가장 확실한 성능 최적화 방법입니다.
* **단점**:
  * 불필요한 데이터까지 조회될 수 있음.
  * **페이징(Paging) 쿼리 시 주의**: `OneToMany` 컬렉션 페치 조인 시 메모리에서 페이징을 처리하려다 `OutOfMemory`가 발생할 수 있음. (`ManyToOne`은 괜찮음)
  * 둘 이상의 컬렉션을 페치 조인하면 `MultipleBagFetchException` 발생 가능.

> **추천 케이스**:
>
> * **단건 조회**나 **리스트 조회**에서 연관된 데이터가 반드시 필요한 경우.
> * `ManyToOne`, `OneToOne` 관계에서는 페이징과 무관하게 적극 사용.

### 2-2. @EntityGraph

JPQL 없이 어노테이션으로 Fetch Join과 유사한 효과를 냅니다. (주로 `LEFT OUTER JOIN` 사용)

```java
@EntityGraph(attributePaths = {"member"})
List<Order> findAll();
```

* **장점**: 쿼리 메서드(`findBy...`)와 함께 사용할 수 있어 간편함.
* **단점**: 조인 제어(Inner/Outer)가 어렵고 복잡한 쿼리에는 한계가 있음.

> **추천 케이스**:
>
> * 간단한 조회 메서드에서 빠르게 N+1을 해결하고 싶을 때.

### 2-3. Batch Size (hibernate.default_batch_fetch_size)

N개의 쿼리를 1개(혹은 소수)의 `IN` 쿼리로 묶어서 처리하는 방식입니다.

**설정 (application.yml):**

```yaml
spring:
  jpa:
    properties:
      hibernate:
        default_batch_fetch_size: 1000 # 보통 100~1000 사이 권장
```

**동작:**

1. `findAll()`로 Order 100개 조회 (쿼리 1)
2. 루프에서 `order.getMember()` 호출 시, 지연 로딩이 발생하지만...
3. 하이버네이트가 **"어? 아직 로딩 안 된 Member 프록시가 100개 있네? 1000개씩 묶어서 가져오자"** 라고 판단.
4. `SELECT * FROM Member WHERE id IN (?, ?, ..., ?)` (쿼리 1번 추가 발생)

결과적으로 **1 + 1 = 총 2번의 쿼리**로 해결됩니다.

* **장점**:
  * 코드 수정 없이 설정만으로 적용 가능.
  * **컬렉션 페치 조인 페이징 문제**를 해결할 수 있는 유일한 대안. (`OneToMany` 관계 페이징 시 필수)
* **단점**: Fetch Join(1번)보다는 쿼리가 더 나가지만(최소 2번), N번보다는 훨씬 효율적임.

> **추천 케이스**:
>
> * **`OneToMany` 컬렉션 관계를 조회하면서 페이징이 필요한 경우.** (Fetch Join 불가능한 상황)
> * 글로벌 설정으로 깔아두고, 특정 상황에서만 Fetch Join으로 최적화하는 전략이 실무 표준.

---

## 3. 직접 참조 vs 간접 참조 (ID 참조)

JPA를 쓰다 보면 객체 그래프 탐색의 편리함 때문에 모든 관계를 객체로 연결(`@ManyToOne`)하려는 유혹에 빠집니다. 하지만 이는 **결합도**를 높이고 성능 문제를 야기할 수 있습니다.

### 3-1. 직접 참조 (Direct Reference)

```java
@Entity
class Order {
    @ManyToOne
    private Member member; // 객체를 직접 참조
}
```

* **장점**: `order.getMember().getName()` 처럼 객체 그래프 탐색이 매우 편리함. JPA의 장점을 100% 활용.
* **단점**:
  * 조회 경계가 모호해짐 (어디까지 조회해야 하는가?).
  * N+1 문제 발생 가능성 높음.
  * 두 도메인(주문, 회원) 간의 강한 결합 발생.

### 3-2. 간접 참조 (Indirect Reference, ID 참조)

DDD(Domain-Driven Design)에서 권장하는 방식으로, **애그리거트(Aggregate) 경계**를 넘어가면 객체 대신 **ID**로 참조하는 방식입니다.

```java
@Entity
class Order {
    @Column(name = "member_id")
    private Long memberId; // ID만 보관
}
```

* **장점**:
  * **결합도 감소**: 주문 로직에서 실수로 회원을 수정하는 부작용 원천 차단.
  * **성능 예측 용이**: 연관된 데이터를 가져오려면 명시적으로 `MemberRepository.findById(memberId)`를 호출해야 하므로 쿼리가 보임.
  * **확장성**: 나중에 회원 서비스가 마이크로서비스(MSA)로 분리되어도 코드 수정이 적음.
* **단점**:
  * 데이터를 합쳐서 보여줘야 할 때(조인) 불편함. (별도 DTO 조회 쿼리나 Facade 패턴 필요)
  * JPA의 Lazy Loading, Cascade 기능을 사용할 수 없음.

> **실무 가이드**:
>
> * **같은 도메인/애그리거트 내** (예: Order -> OrderItem): **직접 참조** (`@OneToMany`, `@ManyToOne`)
> * **다른 도메인 간** (예: Order -> Member, Order -> Product): **간접 참조** (ID) 고려
> * 단, **조회 전용 모델**이나 **어드민**처럼 편의성이 중요한 곳에서는 직접 참조를 허용하기도 함.

---

## 4. 연관관계 매핑 팁

### 4-1. 모든 연관관계는 지연 로딩(LAZY)으로 설정하라

* `@ManyToOne`, `@OneToOne`은 기본값이 **EAGER(즉시 로딩)**입니다. -> **반드시 `fetch = FetchType.LAZY`로 변경해야 합니다.**
* EAGER는 예측 불가능한 쿼리를 발생시키고, N+1 문제의 주범입니다.
* `@OneToMany`는 기본값이 LAZY라 괜찮습니다.

### 4-2. 양방향 매핑은 필요할 때만 하라

* `Order -> Member` (단방향)만 있어도 개발하는 데 문제없는 경우가 90%입니다.
* `Member -> List<Order>` (양방향)을 추가하는 순간, 신경 써야 할 것(무한 루프, 편의 메서드 등)이 늘어납니다.
* **우선 단방향으로 설계하고, 반대쪽 탐색이 꼭 필요할 때만 양방향을 추가하세요.**

### 4-3. Cascade와 OrphanRemoval은 신중하게

* **Cascade.ALL**: 부모 저장/삭제 시 자식도 같이 처리. 라이프사이클이 완전히 동일할 때만 사용 (예: 게시글-첨부파일, 주문-주문항목).
* **OrphanRemoval=true**: 리스트에서 제거하면 DB에서도 삭제.
* **주의**: 다른 곳에서도 참조하는 엔티티(예: Member, Product)에는 절대 Cascade를 걸면 안 됩니다. (주문 지웠는데 회원이 지워지는 대참사 발생)

---

## 5. 요약 정리

| 상황 | 해결 전략 | 비고 |
| :--- | :--- | :--- |
| **기본 원칙** | 모든 연관관계 `LAZY` 설정 | `ManyToOne`은 기본이 EAGER니 주의 |
| **단건/리스트 조회 (N+1)** | `Fetch Join` 사용 | 가장 확실한 방법 |
| **컬렉션 조회 + 페이징** | `Batch Size` 설정 | Fetch Join 시 메모리 터짐 방지 |
| **도메인 간 결합도 낮추기** | **ID 참조 (간접 참조)** | MSA 고려 시 필수 |
| **라이프사이클 관리** | `Cascade` / `OrphanRemoval` | 부모-자식이 운명 공동체일 때만 |
