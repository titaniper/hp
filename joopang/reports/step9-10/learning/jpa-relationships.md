# JPA 연관관계 매핑 (Entity Relationships) 완벽 가이드

JPA를 사용할 때 가장 중요하고 어려운 부분이 바로 **객체의 참조와 테이블의 외래 키(FK)를 매핑**하는 것입니다. 이 문서에서는 4가지 주요 연관관계 어노테이션과 핵심 옵션, 그리고 실제 DB 동작 방식을 상세히 정리합니다.

## 1. 핵심 개념: 연관관계의 주인 (Owner)

객체는 양방향 참조가 가능(서로 다른 단방향 2개)하지만, DB 테이블은 외래 키 하나로 양방향 연관관계를 관리합니다. 따라서 **객체의 두 관계 중 하나를 정해서 외래 키를 관리**하게 해야 하는데, 이를 **연관관계의 주인**이라고 합니다.

- **주인(Owner)**: 외래 키(FK)를 관리(등록, 수정)하는 엔티티.
- **주인이 아닌 쪽(Inverse)**: 읽기만 가능. `mappedBy` 속성으로 주인을 지정해야 함.
- **Rule**: **외래 키가 있는 곳을 주인으로 정해라.** (DB 테이블 기준 N쪽이 무조건 주인)

---

## 2. 다대일 (N:1) - `@ManyToOne`

가장 많이 사용되는 연관관계입니다.

### ManyToOne 특징

- **DB 매핑**: 해당 엔티티 테이블에 **외래 키(FK)**가 생성됩니다.
- **기본 Fetch 전략**: `EAGER` (즉시 로딩) -> **반드시 `LAZY`로 변경해야 함.**

### ManyToOne 예시 (Member N : 1 Team)

```java
@Entity
public class Member {
    @Id @GeneratedValue
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) // [중요] 지연 로딩 설정
    @JoinColumn(name = "team_id")      // 외래 키 컬럼명 지정
    private Team team;
}
```

### ManyToOne DB 동작

- `member` 테이블에 `team_id` 컬럼(FK)이 생성됩니다.
- `member.setTeam(team)`을 호출하고 저장하면 `INSERT INTO member ... team_id = ?` 쿼리가 실행됩니다.

---

## 3. 일대다 (1:N) - `@OneToMany`

보통 `@ManyToOne`의 양방향 짝으로 사용됩니다.

### OneToMany 특징

- **DB 매핑**: 테이블에 컬럼이 추가되지 않습니다. (가상의 컬럼)
- **기본 Fetch 전략**: `LAZY` (지연 로딩)
- **주의**: `mappedBy` 없이 단독으로 사용하면 중간 테이블이 생기거나 비효율적인 업데이트 쿼리가 발생할 수 있어 권장하지 않습니다.

### OneToMany 예시 (Team 1 : N Member)

```java
@Entity
public class Team {
    @Id @GeneratedValue
    private Long id;

    // 연관관계의 주인이 아님을 명시 (Member의 team 필드가 주인)
    @OneToMany(mappedBy = "team") 
    private List<Member> members = new ArrayList<>();
}
```

### OneToMany DB 동작

- `team` 테이블에는 아무런 변화가 없습니다.
- `team.getMembers()`를 호출하는 시점에 `SELECT * FROM member WHERE team_id = ?` 쿼리가 실행됩니다.

---

## 4. 일대일 (1:1) - `@OneToOne`

주 테이블이나 대상 테이블 중 어디에 외래 키를 둘지 선택 가능합니다.

### OneToOne 특징

- **DB 매핑**: 외래 키에 **Unique Constraint(유니크 제약조건)**가 추가됩니다.
- **기본 Fetch 전략**: `EAGER` -> **`LAZY`로 변경 필수.**
- **주의**: 프록시 한계로 인해, **외래 키가 없는 쪽(Inverse)에서 조회할 때 지연 로딩이 동작하지 않고 강제로 즉시 로딩되는 문제**가 있습니다 (Bytecode Instrumentation 없이는 null 여부를 알 수 없기 때문).

### OneToOne 예시 (Member 1 : 1 Locker)

```java
@Entity
public class Member {
    @Id @GeneratedValue
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "locker_id") // Member가 외래키 관리 (주인)
    private Locker locker;
}

@Entity
public class Locker {
    @Id @GeneratedValue
    private Long id;

    @OneToOne(mappedBy = "locker") // 읽기 전용
    private Member member;
}
```

---

## 5. 다대다 (N:M) - `@ManyToMany`

**실무에서 절대 사용하지 말아야 할 연관관계입니다.**

### ManyToMany 이유

1. **중간 테이블 통제 불가**: 연결 테이블에 단순히 FK 두 개만 들어갑니다. 실무에서는 연결 테이블에도 '등록일자', '수량' 같은 추가 데이터가 필요한 경우가 99%입니다.
2. **쿼리 예측 불가**: 숨겨진 쿼리가 많이 발생합니다.

### ManyToMany 해결책

**연결 엔티티(Mapping Entity)를 직접 만들어서 `@OneToMany`, `@ManyToOne`으로 풀어내야 합니다.**

- `Member` (1) <-> (N) `Order` (N) <-> (1) `Product`
- -> `Member` (1) <-> (N) `OrderItem` (N) <-> (1) `Product`

---

## 6. 주요 옵션 상세

### 6.1. `fetch` (로딩 전략)

- `FetchType.EAGER`: 엔티티를 조회할 때 연관된 엔티티도 함께 조회 (조인 쿼리).
  - **문제점**: 예측하지 못한 SQL 발생, **N+1 문제**의 주원인.
- `FetchType.LAZY`: 연관된 엔티티를 실제 사용할 때 조회 (프록시 초기화).
  - **권장**: **모든 연관관계는 지연 로딩(`LAZY`)으로 설정**하고, 필요할 때만 `Fetch Join`으로 가져옵니다.

### 6.2. `cascade` (영속성 전이)

특정 엔티티를 영속 상태로 만들거나 삭제할 때, 연관된 엔티티도 함께 처리하는 옵션입니다.

- `CascadeType.ALL`: 모든 변경 전파.
- `CascadeType.PERSIST`: 저장할 때만 전파. (부모 저장 시 자식도 자동 저장)
- `CascadeType.REMOVE`: 삭제할 때만 전파. (부모 삭제 시 자식도 자동 삭제)
- **주의**: 소유자가 하나일 때(단일 소유자)만 사용해야 합니다. (예: 게시글-첨부파일). 다른 곳에서도 참조하는 엔티티라면 절대 사용하면 안 됩니다.

### 6.3. `orphanRemoval` (고아 객체 제거)

- `orphanRemoval = true`: 컬렉션에서 엔티티를 제거하면, DB에서도 해당 데이터가 삭제됩니다.
  - `team.getMembers().remove(0)` -> `DELETE FROM member WHERE id = ?`
- `CascadeType.REMOVE`와의 차이:
  - `CascadeType.REMOVE`: 부모가 삭제될 때 자식도 삭제.
  - `orphanRemoval=true`: 부모가 삭제될 때 자식 삭제 + **부모와의 관계가 끊어지면 자식 삭제**.

### 6.4. 물리적 FK 제약조건 제거 (`ConstraintMode.NO_CONSTRAINT`)

JPA로 연관관계를 맺으면 기본적으로 DB에 **물리적인 Foreign Key 제약조건**이 생성됩니다. 하지만 성능 최적화나 레거시 DB 연동을 위해 **논리적인 연관관계만 맺고, 물리적 제약조건은 생성하지 않는 경우**가 있습니다.

- **사용 이유**:
  - **성능**: 대량의 데이터 `INSERT`/`UPDATE` 시 FK 정합성 체크 비용을 줄이기 위해.
  - **샤딩/파티셔닝**: 물리적으로 다른 DB/테이블에 데이터가 분산되어 있어 FK를 걸 수 없는 경우.
  - **레거시**: 이미 FK 없이 운영되던 DB에 JPA를 붙이는 경우.

```java
@Entity
public class Member {
    @Id @GeneratedValue
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "team_id", 
        foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT) // 물리적 FK 생성 안 함
    )
    private Team team;
}
```

- **주의사항**: DB가 데이터 무결성을 보장해주지 않으므로, **애플리케이션 로직에서 잘못된 참조가 발생하지 않도록 철저히 검증**해야 합니다.

---

## 7. 직접 참조 vs 간접 참조 (Direct vs Indirect Reference)

JPA를 사용하다 보면 모든 연관관계를 객체로 묶을지(직접 참조), 아니면 ID만 가지고 있을지(간접 참조) 고민하게 됩니다. 최근 DDD(Domain-Driven Design)나 MSA(Microservices) 환경에서는 **간접 참조**가 중요하게 다뤄집니다.

### 7.1. 직접 참조 (Direct Reference)

우리가 지금까지 본 표준 JPA 방식입니다. 객체 필드에 연관된 **객체**를 둡니다.

```java
@Entity
public class Member {
    @ManyToOne
    private Team team; // 객체를 직접 참조
}
```

- **장점**: 객체 그래프 탐색 가능 (`member.getTeam().getName()`), JPA의 편리함 활용.
- **단점**: 결합도가 높음. 성능 문제(N+1) 발생 가능성 높음. 트랜잭션 범위가 모호해짐.

### 7.2. 간접 참조 (Indirect Reference)

객체 대신 **식별자(ID)**만 보관하는 방식입니다.

```java
@Entity
public class Member {
    @Column(name = "team_id")
    private Long teamId; // ID만 참조
}
```

- **장점**:
  - **결합도 감소**: Member와 Team이 물리적으로 분리됨 (MSA 전환 용이).
  - **성능 최적화**: 불필요한 조인이나 로딩이 원천 차단됨.
  - **복잡도 관리**: 애그리거트(Aggregate) 단위로 경계를 명확히 할 수 있음.
- **단점**: 객체 탐색 불가. 필요할 때 `Repository`를 통해 다시 조회해야 함.

### 7.3. `NO_CONSTRAINT`는 간접 참조인가?

엄밀히 말하면 **아닙니다**.

- `NO_CONSTRAINT`는 **JPA 레벨에서는 직접 참조(객체 매핑)**를 유지하되, **DB 레벨에서만 제약조건을 끈 것**입니다.
- 코드상으로는 여전히 `member.getTeam()`이 가능하므로 **직접 참조**의 변형으로 보는 것이 맞습니다.
- **간접 참조**는 아예 객체 필드 없이 `Long teamId` 필드만 가지는 것을 의미합니다.

### 7.4. 실무 권장 전략 (DDD 관점)

1. **같은 애그리거트(Aggregate) 내부**: **직접 참조** 사용.
   - 예: `Order` -> `OrderItem` (라이프사이클을 같이 함, 강한 결합).
2. **다른 애그리거트 간**: **간접 참조** 사용 권장.
   - 예: `Order` -> `Member` (주문과 회원은 별개의 라이프사이클).
   - `Order` 엔티티에는 `Member member` 대신 `Long memberId`를 가짐.
   - **이유**: 모든 것을 직접 참조로 연결하면, 조회 시 어디까지 로딩될지 예측하기 어렵고, 수정 시 트랜잭션 범위가 너무 넓어지는 문제가 발생합니다.

### 7.5. 심화: Order와 OrderItem 관계에서의 FK 전략

**Q: 같은 애그리거트인 `Order` -> `OrderItem` 관계에서는 `NO_CONSTRAINT`를 사용하나요?**

**A: 기본적으로는 사용하지 않고, 물리적 FK를 유지합니다.**

1. **데이터 무결성(Integrity)이 최우선**: 같은 애그리거트 내부는 **강한 일관성**을 보장해야 합니다. 주문이 삭제되었는데 주문 항목이 남거나, 존재하지 않는 주문을 참조하는 항목이 생기는 것을 DB 차원에서 막아야 합니다.
2. **성능 영향 미미**: 보통 한 주문에 포함된 항목(Item) 수는 제한적(수십 개 이내)이므로, FK 검증으로 인한 성능 저하보다 데이터 안전성이 더 중요합니다.
3. **예외 상황 (NO_CONSTRAINT 고려)**:
   - **DB 샤딩(Sharding)**: `Order` 테이블과 `OrderItem` 테이블이 물리적으로 다른 DB 서버에 저장될 때 (물리적 FK 불가능).
   - **초대용량 처리**: 초당 수만 건의 주문이 발생하여 `INSERT` 성능을 극한으로 끌어올려야 할 때.

**결론**: 특별한 아키텍처 요구사항이 없다면 **직접 참조 + 물리적 FK(기본값)**를 사용하는 것이 정석입니다.

---

## 8. 요약: 실무 권장 가이드

1. **모든 연관관계는 지연 로딩(`LAZY`)으로 설정한다.**
   - `@ManyToOne(fetch = FetchType.LAZY)`
   - `@OneToOne(fetch = FetchType.LAZY)`
2. **양방향 연관관계는 필요할 때만 추가한다.**
   - 단방향 매핑만으로도 테이블 설계는 완료됩니다. 역방향 탐색이 꼭 필요할 때 추가하세요.
3. **연관관계 편의 메서드를 작성한다.**
   - 양방향 관계에서는 양쪽 객체에 값을 다 넣어줘야 합니다. 이를 원자적으로 처리하는 메서드를 만드세요.

   ```java
   public void setTeam(Team team) {
       this.team = team;
       team.getMembers().add(this);
   }
   ```

4. **`@ManyToMany`는 쓰지 말고 연결 엔티티로 승격시킨다.**

