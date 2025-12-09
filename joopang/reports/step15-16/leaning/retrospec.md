# Step 15-16 회고 (리뷰 반영)

이번 스텝에서는 Redis 기반 랭킹/비동기 처리 실험 결과를 공유했는데, 아래 피드백을 기준으로 학습/수정 계획을 정리했습니다.

## 랭킹

- `ProductRankingRepository.incrementSalesAndRevenue`가 실제로 어디서 호출되는지 명확하지 않다는 피드백이 있었습니다. `OrderDataTransmissionServiceImpl.send`(src/main/kotlin/io/joopang/services/order/infrastructure/OrderDataTransmissionServiceImpl.kt)에서 결제 스트림을 읽어 랭킹을 업데이트하도록 이미 연결되어 있었지만, 문서에 흐름이 설명되지 않아 혼란을 드린 부분으로 보입니다. 레포트와 API 문서에 데이터 플로 다이어그램을 추가해 “주문 → OrderDataTransmissionService → ProductRankingRepository” 경로를 명시하겠습니다. 또한 랭킹 집계가 필요한 진입점(백오피스 수동 집계 등)이 있다면 해당 서비스에서도 명시적으로 재사용하도록 가이드할 예정입니다.
- ZUNIONSTORE 기반 인기 상품 병합 성능을 실측하자는 제안을 바로 실행할 계획입니다. 테스트 데이터 1만, 10만, 50만 건 수준에서 redis-benchmark + k6 시나리오를 만들어 보고, 파이프라이닝 유무/임시 키 전략(아래 항목) 별 수치를 README에 첨부하겠습니다.
- 인기 상품 집계를 위해 매번 임시 랜덤 키를 만들고 즉시 삭제했는데, 캐싱이 목적이라면 고정 키(`cache:popular-products:daily`)를 두고 주기적으로 overwrite 하는 편이 낫다는 피드백을 수용합니다. PopularProductsCacheWarmupJob을 “고정 키 업데이트 + TTL 연장” 패턴으로 바꿔, 랭킹 조회가 동일 키를 참조하도록 정리하겠습니다. 필요 시 집계 중 충돌을 피하기 위해 `:tmp` 키를 잠시 쓰되, 마지막에 `RENAME`으로 교체하는 방식으로 단순화합니다.

## 비동기 처리

- 쿠폰 유효성 검증이 매번 RDB를 조회해 병목이 될 수 있다는 의견을 반영해, 쿠폰 메타 정보를 Redis에 캐싱해 두고 TTL과 동기화 이벤트로만 갱신하는 구조를 설계하겠습니다. 발급 요청 시 DB 대신 캐시를 조회하고, 만료/수량 변경은 관리 백오피스에서 Message Queue 이벤트로 캐시를 갱신하는 방식으로 DB 의존성을 제거합니다. 캐시 일관성을 위해 1) 쿠폰 메타 싱크 Job, 2) 실패 시 회복 전략(백업 조회)을 문서화합니다.
- Stream enqueue 시 중복 검사를 `queueOps.addIfAbsent`로 선행하려는 제안 역시 채택합니다. 중복 요청 자체를 줄이면 소비자에서의 필터링 비용과 스트림 길이를 줄일 수 있으므로, API 레벨에서 사용자/쿠폰 조합을 key로 살려두고 Redis Set을 통해 딱 한 번만 enqueue 하도록 변경합니다. 이후 Consumer에서는 “중복 시 Early return” 로직을 단순화할 수 있습니다.
- Redis Stream 활용에 대해서는 긍정적 피드백을 받았지만, 모니터링을 위해 “pending length, lag, 재처리 횟수”를 대시보드화하라는 제안을 자체 추가했습니다.

## 문서/보고

- 리뷰에서 언급된 대로 보고서 가독성이 낮았던 이유는 텍스트 위주로 정리해 데이터 흐름을 한눈에 보기 어려웠기 때문입니다. 다음 리포트부터는 “랭킹 데이터 플로”, “쿠폰 발급 아키텍처”를 Mermaid 다이어그램 혹은 Excalidraw 이미지로 첨부하고, 핵심 지표 표를 도입해 가독성을 높이겠습니다.

## 다음 액션 체크리스트

1. [ ] 주문 → 랭킹 갱신 흐름 다이어그램을 보고서/README에 추가하고 관련 코드를 주석으로 연결한다.
2. [ ] 인기 상품 집계 키 전략을 고정 키 + RENAME 패턴으로 교체하고, 배치 Job을 수정한다.
3. [ ] ZUNIONSTORE 파이프라인 성능 측정 스크립트(k6 + redis-benchmark)를 작성해 결과를 보고서에 첨부한다.
4. [ ] 쿠폰 메타 캐시 계층을 구현하고, 발급 API가 Redis 캐시에만 의존하도록 수정한다.
5. [ ] 발급 요청 시 `addIfAbsent` 기반 중복 제한을 넣고, 대시보드를 통해 스트림 상태를 시각화한다.
6. [ ] 레포트 전체에 다이어그램/표를 추가해 가독성을 개선한다.

피드백 반영 계획을 위처럼 구체화했으니, 구현 순서에 따라 각 항목을 issue로 쪼개 진행하겠습니다.
