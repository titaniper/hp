# 트랜잭션 구현 방식 정리

## 1. 핵심 구성요소
| 구성요소 | 역할 |
| --- | --- |
| `PlatformTransactionManager` | 트랜잭션 시작/커밋/롤백을 담당하는 SPI. JDBC, JPA, JMS 등 리소스별 구현체가 존재. |
| `TransactionDefinition` | 전파 속성, 격리 수준, 타임아웃, 읽기 전용 여부 등 트랜잭션 메타데이터. |
| `TransactionStatus` | 현재 트랜잭션의 진행 상태(신규/기존 참여 여부, 롤백 전용 여부 등)를 포함. |
| `TransactionSynchronizationManager` | ThreadLocal 기반으로 커넥션과 동기화 콜백을 관리. |

## 2. 동작 흐름
1. `@Transactional`이 붙은 메서드는 AOP 프록시로 감싸진다.
2. 프록시는 메서드 실행 전에 `PlatformTransactionManager`에 `getTransaction()`을 요청한다.
3. `DataSourceTransactionManager` 예시:
   - DataSource에서 커넥션을 획득.
   - auto-commit 해제 및 격리 수준, 읽기 전용 같은 옵션을 설정.
   - `TransactionStatus`에 커넥션과 신규 여부를 저장.
4. 비즈니스 로직 실행.
5. 실행이 정상 종료되면 `commit(status)` 호출 → 커밋 또는 플러시/클리어 수행.
6. 예외가 발생하면 `rollback(status)` 호출 → 롤백 후 자원 정리.

## 3. 구현 시 고려사항
### 3.1 중첩 트랜잭션
- `REQUIRED`는 이미 진행 중인 트랜잭션에 참여하고, `REQUIRES_NEW`는 독립 트랜잭션을 시작한다.
- 중첩 구조에서는 `TransactionStatus`가 스택처럼 관리되어, 바깥 트랜잭션이 끝나야 실제 커밋이 일어난다.

### 3.2 롤백 전용 마킹
- 내부 트랜잭션에서 예외를 잡고 처리하더라도 롤백 전용으로 마킹되면 최종적으로 커밋할 수 없다.
- `TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()`로 명시적으로 표기할 수도 있다.

### 3.3 자원 동기화
- JPA의 경우 `EntityManager`도 ThreadLocal로 묶여 동일 트랜잭션 안에서 동일 영속성 컨텍스트를 사용한다.
- 여러 리소스를 묶을 때는 `JtaTransactionManager`처럼 글로벌 트랜잭션 매니저가 필요하다.

## 4. 구현 전략 선택
| 상황 | 권장 TransactionManager |
| --- | --- |
| 단일 RDB, JDBC Template | `DataSourceTransactionManager` |
| Spring Data JPA / Hibernate | `JpaTransactionManager` (EntityManager 연동) |
| 다중 리소스 (DB + MQ) | JTA 기반 매니저 (Bitronix, Atomikos 등) |
| R2DBC | `R2dbcTransactionManager` |

## 5. 테스트 및 모니터링
1. **트랜잭션 이벤트 로그**: `AbstractPlatformTransactionManager`에서 DEBUG 로그를 활성화하면 시작/커밋/롤백 과정을 추적 가능.
2. **프로파일링**: AOP 프록시 구간을 추적하여 트랜잭션 경계를 시각화한다.
3. **Failover 테스트**: 예외 시 커넥션이 반환되는지, 롤백-only 상태가 전파되는지 통합 테스트로 검증.
