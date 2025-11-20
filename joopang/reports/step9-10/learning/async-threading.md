# Spring Async & 스레드 풀 정리

> 참고: https://mangkyu.tistory.com/425 요약

## 1. 기본 동작
- `@Async`는 별도의 쓰레드에서 메서드를 실행한다. 기본 구현은 `SimpleAsyncTaskExecutor`로, **호출마다 새 쓰레드를 생성**한다.
- 새 쓰레드 생성 비용: JVM은 기본적으로 스택 1MB를 할당하므로, 호출이 몰리면 메모리 낭비와 컨텍스트 스위칭 비용이 크다.
- 또한 데이터베이스 커넥션·HTTP 클라이언트 등 자원 풀도 쓰레드 수만큼 추가로 필요하므로 풀 고갈 위험이 있다.

## 2. 실무 권장: 쓰레드 풀 사용
1. `TaskExecutor` Bean을 설정해 `@Async`에서 재사용하도록 한다.
   ```kotlin
   @Configuration
   @EnableAsync
   class AsyncConfig {

       @Bean(name = ["orderAsyncExecutor"])
       fun orderAsyncExecutor(): Executor =
           ThreadPoolTaskExecutor().apply {
               corePoolSize = 10
               maxPoolSize = 30
               queueCapacity = 100
               setThreadNamePrefix("order-async-")
               initialize()
           }
   }
   ```
2. Async 메서드에서 `@Async("orderAsyncExecutor")`처럼 명시적으로 풀을 선택한다.

## 3. 구성 파라미터
- **Core Pool Size**: 항상 유지되는 스레드 수. CPU 코어 수와 작업 성격에 따라 조정.
- **Max Pool Size**: 큐가 가득 찼을 때 늘어나는 최대 스레드 수.
- **Queue Capacity**: 대기열 크기. 너무 작으면 `RejectedExecutionException`, 너무 크면 지연.
- **Keep Alive Seconds**: 코어를 초과한 스레드의 생존 시간.
- **Rejected 정책**: 딜레이 큐 등 대안 처리가 필요한 경우 구현체를 지정.

## 4. 커넥션 풀과의 관계
- Async 작업은 기존 쓰레드와 별도 커넥션을 사용하므로, DB 커넥션 풀 사이즈를 Async 풀보다 크게 잡아야 한다.
- 예: Async 풀 최대 30이라면, HikariCP `maximumPoolSize`는 최소 30 이상 + 동기 요청 대비 여유를 둔다.
- 외부 API 클라이언트(HTTP, Redis 등)도 쓰레드 수만큼 세션을 열 수 있으므로 풀 정책을 조정한다.

## 5. 모니터링 포인트
1. **큐 적재량**: `ThreadPoolTaskExecutor`는 `ThreadPoolExecutor`의 MBean으로 노출되므로 큐 길이를 모니터링.
2. **커넥션 풀 대기 시간**: Async 폭주로 커넥션을 기다리는 시간이 늘어나면 로그/메트릭으로 감지.
3. **스레드 덤프**: Async 스레드가 블로킹 작업으로 묶여 있는지 주기적으로 확인.

## 6. 베스트 프랙티스
- CPU 바운드 작업은 `corePoolSize ≈ CPU 코어 수`, I/O 바운드 작업은 더 큰 풀과 큐를 사용.
- 트랜잭션 컨텍스트는 Async 쓰레드로 전파되지 않으므로, 필요한 경우 이벤트/메시지 큐로 전달하거나 별도 DTO를 구성.
- Async와 같은 쓰레드에서만 사용할 자원(ThreadLocal 등)을 사용했다면 반드시 정리한다.
