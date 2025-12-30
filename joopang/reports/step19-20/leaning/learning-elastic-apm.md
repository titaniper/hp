# Elastic APM

## 개요

Elastic APM은 Elastic Stack(ELK)의 일부로, 애플리케이션의 성능 모니터링과 분산 트레이싱을 제공합니다. 다양한 언어와 프레임워크를 지원합니다.

## 주요 기능

- 분산 트랜잭션 추적 및 시각화
- 실시간 성능 모니터링
- 에러 추적, 서비스 맵
- Kibana와 통합 대시보드

## 시각화 예시

![Elastic APM Trace View](https://www.elastic.co/guide/en/apm/get-started/current/images/apm-distributed-tracing.png)

- **Distributed Tracing**: 서비스 간 호출, 지연, 오류를 Kibana에서 시각화

## 적용 예시

1. Elastic APM Server, Agent 구성 후 애플리케이션에 Agent 적용
2. Kibana에서 트랜잭션, 에러, 성능 지표 시각화

## 참고 자료

- [Elastic APM 공식 문서](https://www.elastic.co/guide/en/apm/get-started/current/index.html)
