package io.joopang.database

import io.joopang.point.UserPoint
import org.springframework.stereotype.Component

/**
 * 사용자 포인트 저장소 (Repository 계층)
 *
 * 주의사항:
 * - 이 클래스는 변경하지 않고 공개된 API만을 사용해 데이터를 제어합니다.
 * - 실제 데이터베이스 대신 메모리(HashMap)를 사용하는 테스트용 구현입니다.
 *
 * @Component 어노테이션:
 * - Spring Bean으로 등록되어 의존성 주입(DI) 가능
 * - 싱글톤으로 관리되어 애플리케이션 전체에서 하나의 인스턴스만 사용됨
 *
 * 싱글톤 패턴의 주의사항:
 * - 여러 스레드가 동시에 접근할 수 있으므로 동시성 제어 필요
 * - 이 클래스는 Thread-safe하지 않으므로 외부에서 동기화 처리 필요
 */
@Component
class UserPointTable {
    /**
     * 사용자 포인트 데이터를 저장하는 인메모리 테이블
     *
     * HashMap:
     * - Key: 사용자 ID (Long)
     * - Value: 사용자 포인트 정보 (UserPoint)
     * - Thread-safe하지 않음: 동시성 문제 발생 가능
     */
    private val table = HashMap<Long, UserPoint>()

    /**
     * 사용자 ID로 포인트 정보 조회
     *
     * Thread.sleep():
     * - 데이터베이스 I/O 지연을 시뮬레이션하기 위한 랜덤 대기 시간 (0~200ms)
     * - 실제 프로덕션 환경에서는 네트워크/디스크 I/O가 발생합니다.
     *
     * Elvis 연산자 (?:):
     * - table[id]가 null이면 기본값(포인트 0)을 가진 새 객체 반환
     * - 신규 사용자는 자동으로 0 포인트로 초기화됨
     *
     * @param id 조회할 사용자 ID
     * @return 사용자 포인트 정보 (존재하지 않으면 0 포인트 기본값)
     */
    fun selectById(id: Long): UserPoint {
        Thread.sleep(Math.random().toLong() * 200L)  // DB 조회 지연 시뮬레이션
        return table[id] ?: UserPoint(id = id, point = 0, updateMillis = System.currentTimeMillis())
    }

    /**
     * 사용자 포인트 정보 저장 또는 업데이트
     *
     * Upsert 패턴:
     * - 존재하지 않으면 Insert (새로 추가)
     * - 이미 존재하면 Update (덮어쓰기)
     *
     * Thread.sleep():
     * - 데이터베이스 쓰기 지연을 시뮬레이션 (0~300ms)
     *
     * @param id 사용자 ID
     * @param amount 새로운 포인트 잔액 (기존 잔액을 완전히 대체)
     * @return 저장된 사용자 포인트 정보
     */
    fun insertOrUpdate(id: Long, amount: Long): UserPoint {
        Thread.sleep(Math.random().toLong() * 300L)  // DB 쓰기 지연 시뮬레이션
        val userPoint = UserPoint(id = id, point = amount, updateMillis = System.currentTimeMillis())
        table[id] = userPoint
        return userPoint
    }
}