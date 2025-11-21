# 트랜잭션 핵심 개념: 전파(Propagation)와 격리(Isolation)

## 1. 전파 속성 (Propagation)
동일 쓰레드에서 트랜잭션을 어떻게 재사용하거나 새로 만들지 결정한다.

| 전파 속성 | 설명 | 대표 사용 예 |
| --- | --- | --- |
| `REQUIRED` (기본값) | 기존 트랜잭션이 있으면 참여, 없으면 새로 시작. 대부분의 서비스 메서드에서 사용. | 주문 생성, 결제 등 일반 로직 |
| `REQUIRES_NEW` | 항상 새 트랜잭션 시작. 기존 트랜잭션은 잠시 보류. | 감사 로그, Outbox 저장 |
| `SUPPORTS` | 트랜잭션이 있으면 참여, 없으면 비트랜잭션으로 실행. | 단순 조회 로직 |
| `MANDATORY` | 반드시 기존 트랜잭션이 있어야 함. 없으면 예외. | 트랜잭션 외부에서 호출되면 안 되는 DAO |
| `NOT_SUPPORTED` | 트랜잭션을 일시 중단하고 비트랜잭션으로 실행. | 대용량 리포트 조회 |
| `NEVER` | 트랜잭션이 존재하면 예외 발생. | 캐시 조회처럼 트랜잭션이 불필요한 경우 |
| `NESTED` | 부모 트랜잭션 내에서 savepoint를 사용해 부분 롤백. JDBC + DataSourceTransactionManager에서만 사용 가능. | 부분 롤백이 필요한 배치 처리 |

### 주의점
1. `REQUIRES_NEW`는 새 커넥션을 열기 때문에 커넥션 풀 고갈 위험이 있다.
2. `NESTED`는 JPA 트랜잭션에서 동작하지 않으므로 savepoint가 필요한 경우 JDBC 템플릿을 사용한다.
3. 전파 속성이 다르면 예외 처리 흐름도 달라지므로, 호출 계층을 명확히 설계해야 한다.

## 2. 격리 수준 (Isolation Level)
동시성 문제를 얼마나 허용할지 결정한다. ANSI/ISO 표준 4단계.

| 격리 수준 | Dirty Read | Non-Repeatable Read | Phantom Read | 특징 |
| --- | --- | --- | --- | --- |
| READ UNCOMMITTED | 허용 | 허용 | 허용 | 거의 사용되지 않음. |
| READ COMMITTED | 차단 | 허용 | 허용 | Oracle, PostgreSQL 기본값. |
| REPEATABLE READ | 차단 | 차단 | 허용* | MySQL InnoDB 기본 값 (*Gap Lock으로 대부분 방지). |
| SERIALIZABLE | 차단 | 차단 | 차단 | 완전한 직렬화, 대신 처리량 급감. |

### 실무 팁
1. **기본 설정 확인**: DB마다 기본 격리 수준이 다르므로 명시적으로 설정하거나 문서화한다.
2. **락 전략과 결합**: `SELECT ... FOR UPDATE` 같은 행 락과 함께 사용하면 특정 행에 대한 팬텀/반복 불가 문제를 줄일 수 있다.
3. **비즈니스 요구사항 권장**:
   - 재고 차감: `REPEATABLE READ + FOR UPDATE` 혹은 비관적 쓰기 락.
   - 통계/배치 조회: 대부분 READ COMMITTED로 충분하며, 필요 시 스냅샷(Flashback) 기능 사용.
4. **테스트 케이스 작성**: Dirty/Non-repeatable/Phantom 시나리오를 재현하는 통합 테스트로 격리 수준이 예상대로 동작하는지 확인한다.

## 3. 트랜잭션 AOP 내부 동작 원리 (심화)

스프링의 `@Transactional`은 AOP 기반으로 동작합니다. 실제 런타임에 이 속성들이 어떻게 처리되는지 내부 메커니즘을 이해하면 디버깅에 큰 도움이 됩니다.

### 3-1. 핵심 컴포넌트

1. **TransactionInterceptor (Advice)**: 트랜잭션 경계를 설정하는 AOP 어드바이스입니다. `try-catch-finally` 블록으로 비즈니스 로직을 감싸고 있습니다.
2. **PlatformTransactionManager**: 실제 트랜잭션을 시작(`getTransaction`), 커밋(`commit`), 롤백(`rollback`)하는 매니저입니다.
3. **TransactionSynchronizationManager**: 쓰레드 로컬(ThreadLocal)을 이용해 현재 쓰레드의 커넥션과 트랜잭션 상태를 관리합니다.

### 3-2. 동작 흐름 (Pseudo Code)

```java
// TransactionInterceptor의 invoke 메서드와 유사한 로직
public Object invoke(MethodInvocation invocation) throws Throwable {
    // 1. 트랜잭션 속성(Propagation, Isolation 등) 조회
    TransactionAttribute txAttr = getTransactionAttributeSource().getTransactionAttribute(method, targetClass);
    
    // 2. 트랜잭션 시작 결정 (Propagation 속성에 따라)
    TransactionStatus status = transactionManager.getTransaction(txAttr);
    
    Object retVal;
    try {
        // 3. 비즈니스 로직 실행
        retVal = invocation.proceed();
    } catch (Throwable ex) {
        // 4. 예외 발생 시 롤백 여부 결정 (rollbackFor 속성 확인)
        completeTransactionAfterThrowing(txInfo, ex);
        throw ex;
    } finally {
        // 5. 이전 트랜잭션 상태 복구 (REQUIRES_NEW 등으로 중단된 경우)
        cleanupTransactionInfo(txInfo);
    }
    
    // 6. 정상 종료 시 커밋
    commitTransactionAfterReturning(txInfo);
    return retVal;
}
```

### 3-3. 속성별 상세 동작

#### 1. Propagation (전파) 처리

`transactionManager.getTransaction(txAttr)` 내부에서 일어나는 일입니다.

- **REQUIRED (기본값)**
  1. `TransactionSynchronizationManager`를 확인해 현재 활성 트랜잭션이 있는지 봅니다.
  2. **있으면**: 새로운 물리 트랜잭션을 만들지 않고, 기존 `TransactionStatus`를 그대로 사용하거나 "참여" 표시만 합니다.
  3. **없으면**: `DataSource`에서 새 커넥션을 가져와 `setAutoCommit(false)`를 하고 트랜잭션을 시작합니다.

- **REQUIRES_NEW**
  1. 현재 활성 트랜잭션이 있다면, **Suspend(일시 중단)** 시킵니다.
     - 기존 커넥션을 `ThreadLocal`에서 제거하고 별도 객체에 보관합니다.
  2. 새로운 커넥션을 맺고 새 트랜잭션을 시작합니다.
  3. 새 트랜잭션이 끝나면(커밋/롤백), 보관해뒀던 기존 트랜잭션을 **Resume(재개)** 시켜 `ThreadLocal`에 다시 바인딩합니다.

- **SUPPORTS**

  ```java
  if (isExistingTransaction()) {
      return existingTransaction; // 기존 트랜잭션에 참여
  } else {
      return null; // 트랜잭션 없이(Non-Transactional) 실행
  }
  ```

- **MANDATORY**

  ```java
  if (isExistingTransaction()) {
      return existingTransaction; // 기존 트랜잭션에 참여
  } else {
      throw new IllegalTransactionStateException("No existing transaction found"); // 예외 발생
  }
  ```

- **NOT_SUPPORTED**

  ```java
  if (isExistingTransaction()) {
      Object suspendedResources = suspend(existingTransaction); // 기존 트랜잭션 잠시 중단(Suspend)
      // 트랜잭션 컨텍스트 없이 비즈니스 로직 실행
  }
  // 로직 종료 후
  if (suspendedResources != null) {
      resume(existingTransaction, suspendedResources); // 기존 트랜잭션 복구(Resume)
  }
  ```

- **NEVER**

  ```java
  if (isExistingTransaction()) {
      throw new IllegalTransactionStateException("Existing transaction found"); // 예외 발생
  } else {
      return null; // 트랜잭션 없이 실행
  }
  ```

- **NESTED**

  ```java
  if (isExistingTransaction()) {
      // JDBC Savepoint 생성 (부모 트랜잭션 안에서 중첩)
      Connection con = existingTransaction.getConnection();
      Savepoint savepoint = con.setSavepoint();
      return new TransactionStatus(savepoint);
  } else {
      return startNewTransaction(); // 기존 트랜잭션 없으면 REQUIRED처럼 동작
  }
  ```

#### 2. Isolation (격리) 처리

트랜잭션 시작(`doBegin`)과 종료(`doCleanup`) 시점에 커넥션의 격리 수준을 조정합니다.

```java
// 1. 트랜잭션 시작 (doBegin)
Connection con = dataSource.getConnection();

// 기존 격리 수준 백업 (나중에 복구하기 위해)
Integer previousIsolationLevel = DataSourceUtils.prepareConnectionForTransaction(con, definition);

// 사용자가 설정한 격리 수준 적용 (예: REPEATABLE_READ)
int currentIsolation = definition.getIsolationLevel();
if (currentIsolation != TransactionDefinition.ISOLATION_DEFAULT) {
    con.setTransactionIsolation(currentIsolation);
}

// 2. 트랜잭션 수행 (비즈니스 로직)
// ...

// 3. 트랜잭션 종료 및 정리 (doCleanupAfterCompletion)
if (previousIsolationLevel != null) {
    con.setTransactionIsolation(previousIsolationLevel); // 커넥션 풀 반납 전 원래 설정으로 복구
}
```

#### 3. Rollback Rules (롤백 규칙)

`completeTransactionAfterThrowing` 메서드에서 판단합니다.

- **기본 동작**:
  - `RuntimeException` 및 `Error`: **롤백**
  - `Checked Exception` (Exception 상속): **커밋**
- **rollbackFor / noRollbackFor 설정 시**:
  - 예외가 발생하면 설정된 규칙(RuleBasedTransactionAttribute)을 순회하며 "가장 가까운 패턴"을 찾습니다.
  - 매칭된 규칙에 따라 롤백 여부를 결정합니다.

> **Tip**: 개발자가 `try-catch`로 예외를 잡아버리면 AOP까지 예외가 전달되지 않아 롤백이 발생하지 않습니다. 롤백을 원한다면 `catch` 블록에서 다시 예외를 던지거나 `TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()`를 호출해야 합니다.
