# Pinpoint APM

## 개요
Pinpoint는 대규모 분산 시스템의 트랜잭션을 추적하고 시각화하는 오픈소스 APM(Application Performance Management) 도구입니다. Java, PHP, Python 등 다양한 언어를 지원하며, 서비스 간 호출 관계, 지연, 오류 등을 한눈에 파악할 수 있습니다.

## 주요 기능
- 분산 트랜잭션 추적
- 실시간 모니터링 및 대시보드
- 서비스 맵(Topology Map) 시각화
- 상세 트랜잭션 분석
- 알람 및 통계

## 시각화 예시
![Pinpoint Topology Map](https://naver.github.io/pinpoint/img/topology.png)

- **Topology Map**: 서비스 간 호출 관계와 트래픽, 지연, 오류를 시각적으로 표현합니다.
- **Call Stack View**: 개별 트랜잭션의 호출 스택, 각 단계별 소요 시간, SQL 쿼리, 외부 API 호출 등을 상세히 보여줍니다.

## 적용 예시
1. Pinpoint Collector, Web, Agent 구성 후 애플리케이션에 Agent 적용
2. 대시보드에서 서비스 호출 흐름, 병목 구간, 오류 트랜잭션 탐지
3. 문제 발생 시 Call Stack을 통해 원인 분석

## 참고 자료
- [Pinpoint 공식 문서](https://naver.github.io/pinpoint/)
- [GitHub: naver/pinpoint](https://github.com/naver/pinpoint)
