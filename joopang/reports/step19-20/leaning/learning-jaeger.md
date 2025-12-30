# Jaeger

## 개요

Jaeger는 CNCF에서 관리하는 오픈소스 분산 트레이싱 시스템입니다. 마이크로서비스 환경에서 트랜잭션 추적, 성능 분석, 병목 탐지에 활용됩니다.

## 주요 기능

- 분산 트랜잭션 추적
- 서비스 맵, 트레이스 시각화
- 다양한 스토리지 백엔드 지원
- OpenTracing, OpenTelemetry 호환

## 시각화 예시

![Jaeger Trace View](https://www.jaegertracing.io/img/trace-detail.png)

- **Trace Detail**: 서비스 호출 단계별 소요 시간, 오류, 메타데이터 등 시각화

## 적용 예시

1. Jaeger Agent/Collector 구성 후 애플리케이션에 트레이싱 라이브러리 적용
2. Jaeger UI에서 트랜잭션 흐름, 병목 구간 분석

## 참고 자료

- [Jaeger 공식 문서](https://www.jaegertracing.io/docs/)
