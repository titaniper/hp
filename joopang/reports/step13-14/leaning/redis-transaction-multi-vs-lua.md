# Redis 트랜잭션: MULTI/EXEC vs Lua 스크립트

## 1. 개요
- Redis는 한 번에 여러 명령을 실행할 수 있도록 **트랜잭션** 기능을 제공하며, 대표적으로 `MULTI`/`EXEC` 조합과 **Lua 스크립트**(`EVAL`, `EVALSHA`) 두 가지 방식이 있다.
- 공식 문서: <https://redis.io/docs/latest/develop/using-commands/transactions/>

## 2. MULTI/EXEC 트랜잭션
### 특징
1. `MULTI`로 트랜잭션을 시작하면 이후 명령이 큐에 쌓이고 `EXEC` 시점에 순차 실행된다.
2. Redis는 단일 스레드이므로 한 트랜잭션의 명령이 실행되는 동안 다른 클라이언트 명령이 끼어들어 데이터가 섞이지 않는다.
3. 단, 실행 도중 한 명령이 실패해도 전체 롤백은 되지 않고 실패한 명령만 에러를 반환한다.
4. `WATCH`와 조합하면 Optimistic Lock처럼 동작해 키가 변경되면 `EXEC`가 실패한다.

### 장점/단점
| 장점 | 단점 |
| --- | --- |
| 여러 명령을 한 RTT로 묶을 수 있다 | 롤백이 없고, 실패 시 클라이언트 판단 필요 |
| 코드 가독성이 높고 디버깅 쉬움 | 조건 분기/로직이 늘어나면 클라이언트 쪽에서 처리해야 함 |
| `WATCH`를 통해 낙관적 잠금 구현 가능 | 긴 트랜잭션은 전체 서버 throughput에 영향 |

### 예시
```text
WATCH stock:coupon:100
val = GET stock:coupon:100
if val > 0 then
  MULTI
  DECR stock:coupon:100
  SADD coupon:issued:100 user:1
  EXEC
else
  UNWATCH
end
```

### Spring (RedisTemplate) 사용 예시
```kotlin
val redisTemplate: StringRedisTemplate = ...
fun issueWithMulti(templateId: Long, userId: Long) {
    val stockKey = "stock:coupon:$templateId"
    val issuedKey = "coupon:issued:$templateId"
    redisTemplate.execute { connection ->
        do {
            connection.watch(stockKey.toByteArray())
            val stock = connection.stringCommands().get(stockKey.toByteArray())?.toString(Charsets.UTF_8)?.toLong() ?: 0
            if (stock <= 0) {
                connection.unwatch()
                throw IllegalStateException("sold out")
            }
            connection.multi()
            connection.decr(stockKey.toByteArray())
            connection.setCommands().sAdd(issuedKey.toByteArray(), userId.toString().toByteArray())
        } while (connection.exec() == null) // null이면 WATCH key 변경 → 재시도
    }
}
```

## 3. Lua 스크립트 트랜잭션
### 특징
1. Redis 2.6+ 부터 내장 Lua 인터프리터로 서버 측에서 명령을 실행 가능.
2. Lua 스크립트는 실행 중 다른 명령이 끼어들 수 없어 **원자성**이 보장된다.
3. 조건문, 반복문 등 복잡한 로직을 서버 안에서 처리 가능하며, 다수의 명령을 한 RTT로 묶는다.
4. `SCRIPT LOAD` + `EVALSHA` 조합을 사용하면 스크립트를 캐시해 재사용 가능.

### 장점/단점
| 장점 | 단점 |
| --- | --- |
| 복잡한 로직을 서버에서 원자적으로 실행 | 스크립트 실행 시간이 길면 다른 클라이언트가 모두 기다려야 함 |
| RTT를 한 번으로 줄여 네트워크 부담 감소 | Lua 디버깅이 어렵고, 잘못 작성 시 서버 중단 가능 |
| MULTI/EXEC + WATCH로 구현하기 어려운 분기 로직 처리 | 최대 실행 시간 제한(기본 5초) 초과 시 `SCRIPT KILL` 필요 |

### 예시(Lua)
```lua
local stockKey = KEYS[1]
local issuedKey = KEYS[2]
local userId = ARGV[1]
local stock = tonumber(redis.call('GET', stockKey) or '0')
if stock <= 0 then
  return {err = 'SOLD_OUT'}
end
if redis.call('SISMEMBER', issuedKey, userId) == 1 then
  return {err = 'DUPLICATE'}
end
redis.call('DECR', stockKey)
redis.call('SADD', issuedKey, userId)
return {ok = 'ISSUED'}
```

### Spring (DefaultRedisScript) 사용 예시
```kotlin
@Component
class CouponLuaExecutor(
    private val stringRedisTemplate: StringRedisTemplate,
) {
    private val issueScript = DefaultRedisScript<String>().apply {
        scriptSource = ResourceScriptSource(ClassPathResource("lua/coupon_issue.lua"))
        resultType = String::class.java
    }

    fun enqueue(templateId: Long, userId: Long): String {
        val keys = listOf(
            "stock:coupon:$templateId",
            "coupon:issued:$templateId",
        )
        val args = listOf(userId.toString())
        return stringRedisTemplate.execute(issueScript, keys, *args.toTypedArray())
            ?: throw IllegalStateException("script returned null")
    }
}
```

## 4. 선택 기준
| 상황 | 권장 방식 |
| --- | --- |
| 단순히 여러 명령을 묶고, 실패 시 클라이언트에서 분기 처리 가능 | `MULTI`/`EXEC` + `WATCH` |
| 조건 분기/계산 로직이 복잡해 RTT를 최소화해야 함 | Lua 스크립트 |
| 길고 무거운 트랜잭션이 문제될 수 있는 환경 | Lua로 가능한 한 짧게 작성하거나 MULTI로 쪼개기 |
| 감사/디버깅이 중요한 환경 | MULTI/EXEC (명령 하나씩 기록) |

## 5. 운영 팁
- Lua는 스크립트가 길어지면 관리가 어려우므로 레포지토리에서 버전 관리하고, 재사용 가능한 함수로 분리한다.
- MULTI/EXEC 사용 시 `WATCH` 대상 키를 최소화하고, 실패 시 재시도 로직을 설계해 **낙관적 락** 특성을 살린다.
- Lua 실행 시간이 길어지지 않도록 외부 시스템 호출(I/O)은 금지하고, 필요하면 파이프라인/Batch로 나누어 실행한다.

## 6. 결론
- Redis 트랜잭션은 단순 명령 묶음이 필요한 경우 MULTI/EXEC, 복잡한 원자적 로직을 서버 측에서 처리해야 할 때 Lua 스크립트가 적합하다.
- 두 방식을 혼용하면서도, 스크립트 실행 시간·에러 처리·모니터링을 준비하는 것이 고가용성 환경에서는 필수이다.
