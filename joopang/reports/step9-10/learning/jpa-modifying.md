# JPA @Modifying 어노테이션과 벌크 연산 (Bulk Operations)

Spring Data JPA에서 `@Query`를 사용하여 `INSERT`, `UPDATE`, `DELETE` 쿼리를 직접 실행할 때 반드시 함께 사용해야 하는 어노테이션입니다.

## 1. 왜 필요한가?

JPA의 기본 메커니즘은 **조회(SELECT) -> 수정(Setter) -> 변경 감지(Dirty Checking)** 과정을 거쳐 업데이트를 수행합니다. 하지만 수천, 수만 건의 데이터를 한 번에 업데이트해야 할 때, 모든 엔티티를 메모리에 로딩하는 것은 성능상 매우 비효율적입니다.

이때 **벌크 연산(Bulk Operation)**을 통해 한 번의 SQL로 대량의 데이터를 수정/삭제할 수 있는데, 이를 위해 `@Modifying`이 필요합니다.

- **`@Query`만 사용 시**: `Query.getResultList()` (조회)를 호출하려 시도 -> 에러 발생.
- **`@Modifying` 추가 시**: `Query.executeUpdate()` (변경)를 호출하여 정상 실행.

## 2. 기본 사용법

```java
public interface MemberRepository extends JpaRepository<Member, Long> {

    @Modifying // 필수! 없으면 InvalidDataAccessApiUsageException 발생
    @Query("UPDATE Member m SET m.age = m.age + 1 WHERE m.age >= :age")
    int bulkAgePlus(@Param("age") int age);
}
```

---

## 3. 핵심 주의사항: 영속성 컨텍스트 불일치

벌크 연산은 **영속성 컨텍스트(1차 캐시)를 무시하고 DB에 직접 쿼리를 날립니다.** 이로 인해 **DB와 애플리케이션 간의 데이터 불일치**가 발생할 수 있습니다.

### 문제 상황 시나리오

1. `member1` (나이 10)을 조회 (1차 캐시에 저장됨).
2. 벌크 연산으로 모든 회원의 나이를 +1 함 (DB: member1 나이 11).
3. 다시 `member1`을 조회하면?
   - DB에는 11로 되어있지만, **1차 캐시에는 여전히 10으로 남아있음.**
   - JPA는 1차 캐시를 우선하므로 **10을 반환함.** -> **버그 발생!**

### 해결 방법: `clearAutomatically = true`

벌크 연산 직후에 **영속성 컨텍스트를 깨끗이 비워주는(Clear)** 옵션입니다.

```java
@Modifying(clearAutomatically = true) // 쿼리 실행 후 1차 캐시 초기화
@Query("UPDATE Member m SET m.age = m.age + 1 WHERE m.age >= :age")
int bulkAgePlus(@Param("age") int age);
```

이렇게 하면 다음 조회 시 1차 캐시가 비어있으므로 DB에서 다시 조회해오게 되어 데이터 정합성이 맞춰집니다.

---

## 4. 주요 옵션 상세

### 4.1. `clearAutomatically` (Default: `false`)

- `true`: 쿼리 실행 후 `EntityManager.clear()`를 호출합니다.
- **권장**: 벌크 연산 후 해당 엔티티를 다시 조회해야 한다면 **무조건 `true`로 설정**하세요.

### 4.2. `flushAutomatically` (Default: `false`)

- `true`: 쿼리 실행 전 `EntityManager.flush()`를 호출하여 쓰기 지연 저장소의 변경사항을 DB에 반영합니다.
- Hibernate의 `FlushModeType.AUTO` (기본값)를 사용하면 쿼리 실행 전 자동으로 플러시가 되므로, 보통은 따로 설정하지 않아도 됩니다.

---

## 5. 요약

1. `UPDATE`, `DELETE` 쿼리를 `@Query`로 작성할 땐 **`@Modifying`이 필수**다.
2. 벌크 연산은 1차 캐시를 무시하고 DB에 바로 쏜다.
3. 따라서 연산 후 데이터 불일치를 막기 위해 **`@Modifying(clearAutomatically = true)`** 옵션을 사용하는 것이 안전하다.
