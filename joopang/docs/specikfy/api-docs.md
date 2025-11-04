왜 API 문서가 중요한가요?
API 문서는 개발자 간의 소통 도구입니다. 좋은 문서는 개발 속도를 높이고, 버그를 줄이며, 새로운 팀원의 온보딩을 빠르게 합니다.
Swagger/OpenAPI란?
OpenAPI(Swagger)는 REST API를 기술하기 위한 표준 명세서입니다. JSON 또는 YAML 형식으로 작성하며, 다양한 도구들이 이를 활용할 수 있습니다.
OpenAPI 생태계:
OpenAPI Spec (YAML/JSON): API 명세의 핵심
엔드포인트, 파라미터, 응답 정의
버전 관리 가능
Swagger UI: 대화형 API 문서
실시간 API 테스트
시각적 문서화
Swagger Codegen: 자동 코드 생성
Client SDK 생성
Server Stub 생성
Swagger Editor: 명세 편집기
실시간 유효성 검사
미리보기 기능
Mock Server: 테스트용 가짜 서버
프론트엔드 개발 병렬 진행
API 동작 시뮬레이션
📚 참고: OpenAPI Specification
공식 사이트
GitHub - OpenAPI Specification
Wikipedia: OpenAPI Specification
Swagger의 주요 기능
API 문서 자동 생성
코드에서 주석으로 문서 작성
해당 주석이나 데코레이터로 자동 생성
대화형 API 테스트
Swagger UI에서 직접 API 호출 테스트 가능
매개변수 입력과 응답 확인 가능
코드 생성
다양한 언어로 클라이언트 SDK 자동 생성
메소드 스텁 생성
MockAPI의 활용
MockAPI는 실제 백엔드 구현 없이 API 응답을 시뮬레이션하는 도구입니다.
MockAPI의 장점
병렬 개발: 프론트엔드와 백엔드 동시 개발 가능
빠른 프로토타이핑: UI/UX 검증에 집중 가능
테스트 용이성: 예측 가능한 데이터로 테스트 작성
주요 MockAPI 도구들
JSON Server: 로컬 개발용, 간단한 설정
Mockoon: GUI 기반, 고급 기능 지원
mockapi.io: 온라인 서비스, 팀 공유 용이
Postman Mock Server: API 테스트와 연동
JSON Server로 MockAPI 쉽게 만들기
JSON Server를 사용하면 단 몇 분 만에 완전히 동작하는 REST API를 만들 수 있습니다!
1단계: 설치 및 설정
# 프로젝트 초기화
mkdir todo-mock-api
cd todo-mock-api
npm init -y

# JSON Server 설치
npm install -D json-server

​
2단계: package.json 스크립트 추가
{
  "name": "todo-mock-api",
  "scripts": {
    "dev": "json-server --watch db.json --port 3001",
    "start": "json-server db.json --port 3001"
  },
  "devDependencies": {
    "json-server": "^0.17.4"
  }
}

​
3단계: db.json 파일 생성
{
  "todos": [
    {
      "id": 1,
      "title": "프로젝트 문서 작성",
      "description": "API 설계 문서 완성하기",
      "status": "pending",
      "createdAt": "2024-01-15T09:00:00Z",
      "updatedAt": "2024-01-15T09:00:00Z"
    },
    {
      "id": 2,
      "title": "코드 리뷰",
      "description": "팀원 PR 검토하기",
      "status": "completed",
      "createdAt": "2024-01-14T14:30:00Z",
      "updatedAt": "2024-01-14T16:00:00Z"
    }
  ]
}

​
4단계: 서버 실행
npm run dev

# 터미널 출력:
# JSON Server is running on <http://localhost:3001>
# Resources
# <http://localhost:3001/todos>

​
5단계: 자동 생성되는 API들
// 조회 (GET)
GET <http://localhost:3001/todos>           # 모든 할일 목록
GET <http://localhost:3001/todos/1>         # ID 1인 할일 조회
GET <http://localhost:3001/todos?status=pending>  # 상태별 필터링

// 생성 (POST)
POST <http://localhost:3001/todos>
// Body: {"title": "새 할일", "status": "pending"}

// 수정 (PUT/PATCH)
PUT <http://localhost:3001/todos/1>
PATCH <http://localhost:3001/todos/1>

// 삭제 (DELETE)
DELETE <http://localhost:3001/todos/1>

​
6단계: 프론트엔드에서 사용 예시
const API_BASE = '<http://localhost:3001>';

// 할일 목록 조회
const fetchTodos = async () => {
  const response = await fetch(`${API_BASE}/todos`);
  return response.json();
};

// 새 할일 생성
const createTodo = async (todoData) => {
  const response = await fetch(`${API_BASE}/todos`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(todoData)
  });
  return response.json();
};

​
JSON Server의 장점
빠른 설정: 몇 분 만에 API 서버 완성
실시간 업데이트: 파일 변경 시 자동 재시작
RESTful 규칙: REST 원칙을 자동으로 따름
강력한 쿼리: 필터링, 정렬, 페이지네이션 지원
프론트엔드 중심: 백엔드 없이도 개발 진행 가능
문서화 모범 사례
좋은 API 문서의 요소
명확한 엔드포인트 설명
예시 요청/응답 데이터
에러 코드와 의미
인증 방법 안내
사용 예시와 튜토리얼
문서 유지보수 전략
코드와 문서의 동기화
자동화된 문서 갱신
버전 관리와 연동