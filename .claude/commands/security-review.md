Review this code for security vulnerabilities.

Checklist:
- 입력 검증 누락, 특히 포인트 증감 시 범위 확인 여부
- 동시성 제어가 잠금을 우회하지 않는지
- 민감한 정보(포인트, 사용자 ID)가 로그로 노출되지 않는지
- 외부 의존성 추가 시 라이선스/보안 검증 필요 여부

Deliverable: 심각도 순으로 정렬한 이슈 목록과 필요한 테스트 제안
