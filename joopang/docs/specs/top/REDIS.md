## 집계 테이블 추천 여부

**일반적으로는 만들지 않는 것을 추천합니다.** 이유는:

### 만들지 않아도 되는 경우 (대부분)
- 일별 데이터에서 GROUP BY로 집계해도 충분히 빠름
- 파티셔닝 + 인덱스만으로도 성능 확보 가능
- 데이터 정합성 관리 부담 (동기화 이슈)
- 스토리지 낭비

```sql
-- 이 쿼리가 충분히 빠르면 집계 테이블 불필요
SELECT 
  DATE_FORMAT(metric_date, '%Y-%m') as month,
  SUM(views) as total_views
FROM product_metrics_daily
WHERE product_id = 123
  AND metric_date >= '2024-01-01'
GROUP BY month;
```

### 만드는 것이 유리한 경우 (특수 상황)
1. **대량 데이터 + 복잡한 집계**: 수억 건의 일별 데이터에서 다중 차원 분석
2. **느린 쿼리가 반복될 때**: 같은 기간 집계를 수천 명이 동시 조회
3. **BI/리포팅 전용**: Redshift, BigQuery 같은 분석 DB에서

**결론: 우선 일별 테이블만 만들고, 성능 문제가 실제로 발생하면 그때 집계 테이블 추가하세요.**

---

## 시퀀스 다이어그램다이어그램을 만들었습니다! 주요 포인트는:

## 핵심 플로우

1. **실시간 수집**: Redis에 HINCRBY로 빠르게 증가
2. **배치 처리**: 전날 데이터를 MySQL로 이관 후 Redis 정리
3. **대시보드 조회**: 최근 데이터는 Redis, 과거는 MySQL에서 가져와 병합
4. **장애 대응**: Redis 장애 시 DB로 fallback

## 추가 고려사항

```python
# 배치 처리 예시 코드
def flush_metrics_to_db(target_date):
    pattern = f"product:*:metrics:{target_date}"
    
    # Redis에서 스캔
    for key in redis.scan_iter(match=pattern, count=100):
        product_id = key.split(':')[1]
        metrics = redis.hgetall(key)
        
        # Bulk insert로 성능 최적화
        batch_data.append({
            'date': target_date,
            'product_id': product_id,
            'views': int(metrics.get('views', 0)),
            'sales': int(metrics.get('sales', 0))
        })
        
        if len(batch_data) >= 1000:
            db.bulk_insert(batch_data)
            batch_data.clear()
```

궁금한 부분 있으시면 말씀해주세요!