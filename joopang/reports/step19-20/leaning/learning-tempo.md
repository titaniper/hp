# Tempo

## 개요

Tempo는 Grafana Labs에서 개발한 분산 트레이싱 백엔드로, 오픈소스이며 대규모 환경에서 저렴하게 트레이스 데이터를 저장하고 조회할 수 있습니다. Jaeger, Zipkin, OpenTelemetry 등 다양한 트레이싱 프로토콜을 지원합니다.

## 주요 기능

- 대용량 트레이스 저장 및 검색
- Grafana와의 통합 시각화
- 다양한 수집기(Collector) 지원
- TraceID 기반 빠른 검색

## 시각화 예시

![Tempo Trace View](https://grafana.com/static/img/docs/tempo/trace-detail.png)

- **Trace View**: 서비스 호출 흐름, 각 단계별 지연, 오류 등을 시각적으로 분석

## 적용 예시

1. OpenTelemetry Collector를 통해 애플리케이션 트레이스 수집
2. Tempo에 저장 후 Grafana에서 트레이스 시각화

## 참고 자료

- [Grafana Tempo 공식 문서](https://grafana.com/docs/tempo/)
