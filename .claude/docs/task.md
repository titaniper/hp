# 테스크 가이드

- 주요 작업 전에는 `Goal / Context / Constraints / Acceptance / Validation` 순서로 프롬프트를 작성합니다.
- 테스트 관련 변경 시 `./gradlew test` 또는 개별 테스트 명령을 명시합니다.
- 동시성 영향이 있는 경우 "ReentrantLock 기반 사용자별 잠금 유지"를 반드시 언급합니다.
- 문서나 설정을 수정하면 관련 파일(예: CLAUDE.md, claude_prompt_config.yaml)을 함께 업데이트하도록 요청하세요.
