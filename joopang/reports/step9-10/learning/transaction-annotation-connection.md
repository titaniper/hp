# @Transactional 동작: 커넥션 기반 이해

> 참고: https://mangkyu.tistory.com/154 내용을 바탕으로 정리

## 1. 트랜잭션 경계는 커넥션 단위
- 스프링의 `@Transactional`은 **프록시가 메서드 호출을 가로채서** 트랜잭션을 시작한다.
- 시작 시 `PlatformTransactionManager`가 DataSource에서 커넥션을 가져오고, 커넥션의 `autoCommit=false`를 설정해 트랜잭션을 시작한다.
- 동일 트랜잭션 범위에 있는 모든 DB 작업은 **같은 커넥션**을 사용해야 동일한 트랜잭션으로 묶인다.

## 2. ThreadLocal을 이용한 커넥션 전파
1. 트랜잭션 시작 시 커넥션을 `TransactionSynchronizationManager`의 ThreadLocal 저장소에 보관한다.
2. 같은 쓰레드에서 실행되는 DAO/JPA 호출은 ThreadLocal에서 커넥션을 찾아 재사용한다.
3. 메서드가 정상 종료되면 커밋, 예외 발생 시 롤백 → 이후 커넥션을 반환하고 ThreadLocal을 정리한다.

## 3. 주요 동작 시나리오
| 상황 | 동작 |
| --- | --- |
| `@Transactional` 진입 | 새 커넥션 획득, auto-commit 끄고 트랜잭션 시작 |
| 내부에서 또 다른 `@Transactional` 호출 (Propagation REQUIRED) | 기존 ThreadLocal 커넥션을 재사용 → 하나의 트랜잭션 |
| 내부에서 `REQUIRES_NEW` 호출 | 기존 커넥션을 잠시 보관하고, **새 커넥션**으로 독립 트랜잭션 시작 |
| 예외 발생 | 예외가 외부로 전달되면 롤백 → 커넥션 반환 |

## 4. 왜 커넥션 기반인지?
- RDB는 트랜잭션을 **커넥션(Connection)** 단위로 관리한다. 커밋/롤백 명령도 커넥션 객체가 가진 상태에 의존한다.
- 동일 쓰레드라도 커넥션을 새로 열면 별도 트랜잭션으로 취급되므로, 스프링은 ThreadLocal로 **커넥션 공유**를 강제한다.

## 5. 실무 시 주의점
1. **트랜잭션 경계 내부에서만 커넥션 사용**: 트랜잭션 밖에서 커넥션을 직접 열어 사용하면 일관성 깨짐.
2. **비동기 작업**: 다른 쓰레드에서 실행하면 ThreadLocal이 공유되지 않아 트랜잭션이 분리된다.
3. **커넥션 누수 방지**: 예외로 인해 `TransactionSynchronizationManager.clear()`가 실행되지 않으면 누수가 발생하므로, 마지막에 반드시 정리되도록 해야 한다.
4. **테스트 환경**: `@Transactional` 테스트는 기본적으로 롤백하므로 커넥션이 재사용되는 방식까지 검증 가능하다.
