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
