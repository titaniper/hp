시퀀스 다이어그램 설계
시퀀스 다이어그램이란?
시퀀스 다이어그램은 시간의 흐름에 따라 객체 간의 상호작용을 보여주는 다이어그램입니다. API 요청이 들어왔을 때 시스템 내부에서 어떤 순서로 처리되는지를 명확히 보여줍니다.
언제 시퀀스 다이어그램을 그려야 할까요?
복잡한 비즈니스 로직이 있을 때
여러 서비스나 컴포넌트가 협력해야 할 때
에러 처리 흐름을 정의할 때
팀원들과 로직을 공유해야 할 때
시퀀스 다이어그램 구성요소
1. 액터 (Actor)
시스템과 상호작용하는 외부 주체
예: 사용자, 관리자, 외부 시스템
2. 객체 (Object)
시스템 내부의 컴포넌트나 서비스
예: API Server, Database, Cache, External Service
3. 생명선 (Lifeline)
각 객체의 생존 기간을 나타내는 세로 점선
4. 메시지 (Message)
객체 간의 통신이나 호출
화살표로 표현, 시간 순서대로 위에서 아래로
게시글 생성 API의 시퀀스 다이어그램 예시
게시글 생성 API 흐름:
👤 사용자 → API Server: POST /posts 요청
API Server → Auth Service: 토큰 검증
Auth Service → API Server: user_id 반환
API Server: 입력값 검증
API Server → Database: 게시글 저장 (INSERT)
Database → API Server: post_id 반환
API Server → Redis Cache: 캐시 업데이트
API Server → 사용자: 201 Created 응답
복잡한 시나리오: 댓글 작성 with 알림 발송
댓글 작성 with 알림 발송 흐름:
👤 사용자 → API Server: POST /comments 요청
API Server → Database: 사용자 인증 확인
API Server → Database: 댓글 저장
API Server → Database: 게시글 작성자 정보 조회
API Server → Message Queue: 알림 이벤트 발행
Message Queue → Push/Email Service: 비동기 알림 처리
API Server → 사용자: 201 Created 즉시 응답
※ 푸시/이메일 알림은 비동기로 처리되어 사용자 응답에 영향 없음
📚 참고: UML Sequence Diagrams
Wikipedia - Sequence diagram
PlantUML 공식 문서


# Prompt
게시글 생성 API의 시퀀스 다이어그램을 그려주세요.
- JWT 토큰 인증 과정 포함
- 입력 데이터 검증
- 데이터베이스 저장
- 캐시 업데이트
- 에러 상황 처리
PlantUML 형식으로 작성해주세요.

# Check
모든 중요한 상호작용이 포함되었는가?
에러 처리 흐름이 명확한가?
비동기 처리가 올바르게 표현되었는가?
성능상 병목지점이 파악되었는가?
보안 관련 검증 과정이 포함되었는가?