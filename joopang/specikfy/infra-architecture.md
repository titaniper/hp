
인프라 구조 설계
https://teamsparta.notion.site/2-4-ERD-2452dc3ef5148052852fc61127410edc 

기본 웹 애플리케이션 구조
기본 웹 애플리케이션 구조:
사용자 계층
브라우저/모바일 앱에서 요청 시작
로드 밸런서
트래픽을 여러 서버에 분산
헬스체크 및 장애 서버 격리
웹 서버 (nginx)
정적 파일 서빙
리버스 프록시 역할
애플리케이션 서버 (Node.js)
비즈니스 로직 처리
API 엔드포인트 제공
데이터 계층
Database (PostgreSQL): 영구 데이터 저장
Cache (Redis): 세션 및 임시 데이터 저장
클라우드 환경의 현대적 구조
클라우드 환경의 현대적 아키텍처 계층:
Edge Layer
CDN (CloudFlare, CloudFront): 정적 컨텐츠 캐싱
DDoS 방어, SSL 종료
API Layer
API Gateway: 요청 라우팅, 인증, 속도 제한
로드밸런싱, 모니터링
Container Orchestration
Kubernetes: 컨테이너 자동 배포/확장
Pod 관리, 서비스 디스커버리
Data Layer
Primary/Replica DB: 읽기 부하 분산
Redis Cache: 세션 저장, 캐싱
Message Queue (Kafka): 비동기 처리
Storage
Object Storage (S3): 파일 저장
CDN 연동, 백업 스토리지
📚 참고: AWS Well-Architected Framework
AWS 공식 문서
Martin Fowler - Microservices


# Prompt
중간 규모 블로그 서비스의 인프라 구조를 설계해주세요.
- 월 100만 PV 처리 가능
- AWS 기준으로 설계
- Load Balancer, Auto Scaling, RDS, ElastiCache 포함
- 보안과 모니터링 고려
- 비용 최적화도 고려한 구조

# Check
인프라 구조
단일 장애점이 있는가?
확장성이 고려되었는가?
보안이 적절히 설정되었는가?
모니터링 체계가 있는가?
백업과 복구 전략이 있는가?