# Spring 복잡한 동적 쿼리 처리 전략 (Complex & Dynamic Queries)

Spring Data JPA를 사용하다 보면 기본 제공 메서드(`findBy...`)만으로는 해결하기 어려운 상황을 마주하게 됩니다. 특히 **검색 조건이 수시로 바뀌는 동적 쿼리**나, **복잡한 조인/서브쿼리**가 필요한 경우 다양한 해결책이 존재합니다.

이 문서에서는 주요 전략들의 장단점과 예시를 비교 분석합니다.

## 1. JPA Native Query (`@Query`)

SQL을 직접 작성하여 실행하는 방식입니다.

### Native Query 특징

- **장점**:
  - SQL을 그대로 사용하므로 DB 고유 기능(Window Function, 특정 Dialect) 사용 가능.
  - 성능 최적화(인덱스 힌트 등)가 용이함.
  - 가장 직관적임 (SQL을 안다면).
- **단점**:
  - **타입 안정성(Type Safety) 부족**: 문자열로 쿼리를 작성하므로 오타가 런타임에 발견됨.
  - **동적 쿼리 작성이 매우 어려움**: `if` 문으로 문자열을 더하는 방식은 가독성이 떨어지고 SQL Injection 위험이 있음.
  - 리팩토링 시 컬럼명 변경 등이 자동으로 반영되지 않음.

### Native Query 예시 (정적 쿼리)

```java
public interface OrderRepository extends JpaRepository<Order, Long> {
    
    @Query(value = "SELECT * FROM orders o " +
                   "WHERE o.status = :status AND o.created_at > :date", 
           nativeQuery = true)
    List<Order> findOrdersByStatusAndDate(@Param("status") String status, 
                                          @Param("date") LocalDateTime date);
}
```

> **Note**: 멘토님이 언급한 "JPA Repository + Native Query"는 복잡한 조회(통계, 집계)에는 강력하지만, **조건이 계속 바뀌는 동적 쿼리**를 처리하기에는 적합하지 않습니다. 동적 쿼리가 필요하다면 아래의 QueryDSL이나 MyBatis/JdbcTemplate을 혼용하는 것이 일반적입니다.

---

## 2. QueryDSL (사실상 표준)

Java 코드로 쿼리를 작성할 수 있게 해주는 Type-Safe한 빌더 라이브러리입니다. 국내 Spring 생태계에서 가장 많이 사용됩니다.

### QueryDSL 특징

- **장점**:
  - **컴파일 시점 에러 체크**: 오타가 있으면 컴파일이 안 됨.
  - **동적 쿼리 작성 용이**: `BooleanBuilder`나 `BooleanExpression`을 사용하여 직관적인 동적 조건 처리가 가능.
  - **가독성**: 자바 코드처럼 읽힘. IDE 자동완성 지원.
- **단점**:
  - 초기 설정(Gradle/Maven)이 다소 번거로움.
  - 컴파일 시 `Q-Class`를 생성해야 함.

### QueryDSL 예시 (동적 쿼리)

```java
// Custom Repository 구현
public List<Order> searchOrders(String status, String memberName) {
    return queryFactory
            .selectFrom(order)
            .join(order.member, member)
            .where(
                eqStatus(status),      // 동적 조건 1
                containsMemberName(memberName) // 동적 조건 2
            )
            .fetch();
}

// BooleanExpression을 사용한 동적 조건 (null이면 무시됨)
private BooleanExpression eqStatus(String status) {
    if (StringUtils.isEmpty(status)) {
        return null; 
    }
    return order.status.eq(OrderStatus.valueOf(status));
}

private BooleanExpression containsMemberName(String name) {
    return StringUtils.hasText(name) ? member.name.contains(name) : null;
}
```

---

## 3. JPA Criteria API

JPA 표준 스펙에 포함된 프로그래밍 방식의 쿼리 빌더입니다.

### Criteria API 특징

- **장점**:
  - JPA 표준이므로 별도 의존성 추가 불필요.
- **단점**:
  - **가독성이 매우 나쁨**: 코드가 복잡하고 의도를 파악하기 힘듦.
  - 유지보수가 어려워 실무에서 기피하는 추세.

### Criteria API 예시

```java
// QueryDSL과 비교해보세요. 같은 로직입니다.
public List<Order> searchOrders(String status) {
    CriteriaBuilder cb = em.getCriteriaBuilder();
    CriteriaQuery<Order> cq = cb.createQuery(Order.class);
    Root<Order> order = cq.from(Order.class);

    List<Predicate> predicates = new ArrayList<>();
    
    if (status != null) {
        predicates.add(cb.equal(order.get("status"), OrderStatus.valueOf(status)));
    }
    
    cq.where(predicates.toArray(new Predicate[0]));
    return em.createQuery(cq).getResultList();
}
```

---

## 4. jOOQ (Java Object Oriented Querying)

DB 스키마를 기반으로 자바 코드를 생성하여 SQL을 작성하는 라이브러리입니다. QueryDSL보다 더 SQL 친화적입니다.

### jOOQ 특징

- **장점**:
  - SQL 문법과 거의 유사한 자바 API 제공.
  - 강력한 Type Safety.
  - Active Record 패턴 지원.
- **단점**:
  - **라이선스 이슈**: MySQL, PostgreSQL 등 오픈소스 DB는 무료지만, Oracle/SQL Server 등은 유료 라이선스 필요.
  - 초기 설정이 QueryDSL보다 복잡할 수 있음.

### jOOQ 예시

```java
// SQL과 거의 1:1 매핑됨
dslContext.select()
          .from(ORDERS)
          .join(MEMBER).on(ORDERS.MEMBER_ID.eq(MEMBER.ID))
          .where(ORDERS.STATUS.eq(status))
          .fetch();
```

---

## 5. MyBatis / JdbcTemplate (SQL Mapper)

JPA(ORM)가 아닌 SQL Mapper 방식입니다.

### SQL Mapper 특징

- **MyBatis**: XML 파일에 쿼리를 분리하여 관리. 동적 쿼리 태그(`if`, `choose`, `foreach`) 지원이 강력함. 하지만 컴파일 타임 체크가 불가능하고 코드가 분산됨.
- **JdbcTemplate**: Spring 기본 제공. 간단한 Native SQL 실행에 적합하지만 동적 쿼리 작성은 여전히 문자열 연산이 필요함.

---

## 6. 요약 및 추천 전략

| 기술 | 타입 안정성 | 동적 쿼리 | 가독성 | 추천 상황 |
| :--- | :---: | :---: | :---: | :--- |
| **Spring Data JPA** | O | X | 상 | 단순 CRUD, 정적 조회 |
| **QueryDSL** | **최상** | **최상** | **상** | **복잡한 조회, 동적 쿼리 (기본 추천)** |
| **Native Query** | 하 | 하 | 중 | 특정 DB 기능 사용, 성능 최적화, 통계 쿼리 |
| **JPA Criteria** | 상 | 중 | **하** | (비추천) 외부 의존성을 절대 추가할 수 없을 때 |
| **jOOQ** | 최상 | 상 | 상 | SQL 중심적인 개발, 복잡한 쿼리가 주를 이룰 때 |

### 실무 권장 조합

보통 **Spring Data JPA**를 기본으로 사용하고, 복잡한 조회나 동적 쿼리가 필요한 부분만 **QueryDSL**을 확장(Custom Repository)하여 사용하는 것이 가장 일반적이고 생산성이 높은 패턴입니다.
