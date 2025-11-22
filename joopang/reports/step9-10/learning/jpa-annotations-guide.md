# 실무에서 자주 쓰는 JPA 핵심 어노테이션 정리

JPA와 Spring Data JPA를 사용할 때, `@Entity`나 `@Id` 같은 기본 어노테이션 외에도 실무에서 빈번하게 사용되는 중요한 어노테이션들을 정리했습니다.

## 1. 엔티티 공통 속성 분리: `@MappedSuperclass`

테이블마다 공통으로 들어가는 컬럼(생성일, 수정일, 생성자 등)을 부모 클래스로 분리할 때 사용합니다.

### MappedSuperclass 특징

- **매핑 정보 상속**: 테이블을 생성하지 않고, 자식 엔티티에게 매핑 정보만 상속합니다.
- **추상 클래스 권장**: 직접 생성해서 쓸 일이 없으므로 `abstract class`로 만드는 것을 권장합니다.

### MappedSuperclass 예시

```java
@MappedSuperclass // 테이블 생성 X, 매핑 정보만 제공
@EntityListeners(AuditingEntityListener.class)
@Getter
public abstract class BaseEntity {
    
    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}

@Entity
public class Member extends BaseEntity { // createdAt, updatedAt 컬럼을 가짐
    // ...
}
```

---

## 2. 변경된 필드만 업데이트: `@DynamicUpdate`

기본적으로 JPA는 모든 필드를 업데이트하는 SQL을 생성합니다. 이 어노테이션을 붙이면 **실제 값이 변경된 필드만** SQL에 포함시킵니다.

### DynamicUpdate 특징

- **장점**: 쿼리가 가벼워지고, 의도치 않은 필드 초기화를 막을 수 있음.
- **단점**: 캐싱된 SQL을 사용하지 못하고 매번 동적으로 SQL을 생성하므로 약간의 오버헤드 발생 (필드가 많을 때 유리).

### DynamicUpdate 예시

```java
@Entity
@DynamicUpdate
public class Product {
    // ...
}
```

---

## 3. DB 매핑 제외: `@Transient`

DB 테이블에는 컬럼으로 존재하지 않고, **객체에서만 임시로 값을 보관**하고 싶을 때 사용합니다.

### Transient 예시

```java
@Entity
public class Order {
    @Id @GeneratedValue
    private Long id;

    private int price;

    @Transient // DB에 저장되지 않음
    private boolean isDiscounted; 
}
```

---

## 4. Enum 안전하게 저장: `@Enumerated`

Java의 Enum 타입을 DB에 저장할 때 사용합니다.

### Enumerated 옵션

- `EnumType.ORDINAL` (기본값): Enum의 **순서(0, 1, 2...)**를 저장.
  - **치명적 단점**: Enum 순서가 바뀌면 데이터가 꼬임. **절대 사용 금지.**
- `EnumType.STRING`: Enum의 **이름("READY", "DONE")**을 저장.
  - **권장**: 문자열이 저장되므로 안전함.

### Enumerated 예시

```java
@Enumerated(EnumType.STRING) // 필수!
private OrderStatus status;
```

---

## 5. 낙관적 락(Optimistic Lock): `@Version`

DB의 락 기능을 사용하지 않고, 애플리케이션 레벨에서 버전을 관리하여 동시성을 제어합니다.

### Version 동작 원리

1. 데이터 조회 시 버전(version)도 함께 조회.
2. 업데이트 시 `UPDATE ... SET version = version + 1 WHERE id = ? AND version = ?` 실행.
3. 만약 그 사이 다른 트랜잭션이 수정해서 버전이 바뀌었다면, `Row count`가 0이 되어 `OptimisticLockException` 발생.

### Version 예시

```java
@Entity
public class Ticket {
    @Id @GeneratedValue
    private Long id;

    @Version
    private Long version; // 개발자가 직접 수정하면 안 됨
}
```

---

## 6. 컬럼 제약 조건: `@Column`

DDL 생성 시 제약조건을 걸거나, JPA 실행 동작을 제어합니다.

### Column 주요 속성

- `nullable = false`: DDL 생성 시 `NOT NULL` 제약조건 추가. (유효성 검사는 아님)
- `updatable = false`: **이 필드 값은 절대 수정되지 않음.** (JPA가 Update 쿼리에서 제외함).
  - 예: `createdAt`, `username`(변경 불가 정책 시)
- `insertable = false`: Insert 쿼리에서 제외.

### Column 예시

```java
@Column(nullable = false, updatable = false)
private String username;
```

---

## 7. 자동 감시(Auditing): `@EntityListeners`

엔티티의 생명주기 이벤트(생성, 수정 등)를 감지하여 특정 로직을 실행합니다. 주로 시간 정보를 자동 저장할 때 씁니다.

### Auditing 설정

1. 메인 클래스에 `@EnableJpaAuditing` 추가.
2. 엔티티(또는 BaseEntity)에 `@EntityListeners(AuditingEntityListener.class)` 추가.

### Auditing 예시

```java
@EntityListeners(AuditingEntityListener.class)
public class Board {
    @CreatedDate // 생성 시 자동 저장
    private LocalDateTime regDate;

    @LastModifiedDate // 수정 시 자동 업데이트
    private LocalDateTime modDate;
}
```

---

## 8. 요약

| 어노테이션 | 용도 | 핵심 포인트 |
| :--- | :--- | :--- |
| `@MappedSuperclass` | 공통 매핑 정보 상속 | 테이블 생성 X, 상속용 |
| `@DynamicUpdate` | 변경된 필드만 Update | 필드가 많을 때 유리 |
| `@Transient` | 매핑 제외 | 메모리 전용 필드 |
| `@Enumerated` | Enum 매핑 | **반드시 `STRING` 옵션 사용** |
| `@Version` | 낙관적 락 | 동시성 제어, 충돌 시 예외 발생 |
| `@Column(updatable=false)` | 수정 방지 | 읽기 전용 필드 설정 |
