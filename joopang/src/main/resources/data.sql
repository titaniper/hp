INSERT INTO products (id, name, code, description, content, status, seller_id, category_id, price_amount, price_currency, discount_rate, version)
VALUES
    ('11111111-1111-1111-1111-111111111111', '프리미엄 드립 커피 세트', 'PROD-DRIP-SET-001', '핸드드립을 위한 원두와 드리퍼 풀 패키지', '싱글 오리진 원두 3종과 세라믹 드리퍼, 필터 40매 포함', 'ON_SALE', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 39800, 'KRW', 0.10, 3),
    ('44444444-4444-4444-4444-444444444444', '에센셜 핸드크림 3종 세트', 'PROD-HAND-SET-002', '보습과 향을 동시에 챙길 수 있는 핸드크림 세트', '라벤더, 시더우드, 화이트머스크 30ml 튜브 구성', 'ON_SALE', 'cccccccc-cccc-cccc-cccc-cccccccccccc', 'dddddddd-dddd-dddd-dddd-dddddddddddd', 25900, 'KRW', 0.00, 1),
    ('66666666-6666-6666-6666-666666666666', '시그니처 허브 티 컬렉션', 'PROD-TEA-SET-003', '카페인 없는 허브 티 블렌드 5종 구성', '라벤더 캬모마일 등 인기 허브 티 5종과 안내 리플렛', 'ON_SALE', 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', 'ffffffff-ffff-ffff-ffff-ffffffffffff', 24800, 'KRW', 0.05, 2)
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO product_items (id, product_id, name, code, description, status, stock, unit_price, price_amount, price_currency)
VALUES
    ('22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', '싱글 오리진 원두 200g', 'SKU-DRIP-001', '중배전 싱글 오리진 원두', 'ACTIVE', 150, 14800, 14800, 'KRW'),
    ('33333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111111', '세라믹 드리퍼 세트', 'SKU-DRIP-002', '드립서버, 드리퍼, 필터 40매 포함', 'ACTIVE', 80, 25000, 25000, 'KRW'),
    ('55555555-5555-5555-5555-555555555555', '44444444-4444-4444-4444-444444444444', '라벤더 핸드크림 30ml', 'SKU-HAND-001', '은은한 라벤더 향의 보습 핸드크림', 'ACTIVE', 300, 8900, 8900, 'KRW'),
    ('77777777-7777-7777-7777-777777777777', '66666666-6666-6666-6666-666666666666', '허브 티 샘플러 10팩', 'SKU-TEA-001', '라벤더, 루이보스, 캐모마일 등 허브 티 블렌드', 'ACTIVE', 120, 24800, 24800, 'KRW')
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO product_sales_rankings (id, period, rank_position, product_id, name, sales_count, revenue)
VALUES
    (1, '3days', 1, '11111111-1111-1111-1111-111111111111', '프리미엄 드립 커피 세트', 124, 4935200),
    (2, '3days', 2, '44444444-4444-4444-4444-444444444444', '에센셜 핸드크림 3종 세트', 98, 2538200),
    (3, '3days', 3, '66666666-6666-6666-6666-666666666666', '시그니처 허브 티 컬렉션', 72, 1785600)
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO coupon_templates (id, title, type, value, total_quantity, issued_quantity, expires_at)
VALUES
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '10% 할인 쿠폰', 'PERCENTAGE', 0.10, 1000, 508, '2024-12-31 23:59:59'),
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '배송비 무료 쿠폰', 'GIFT', 0, 500, 500, '2024-06-30 23:59:59'),
    ('bbbbcccc-dddd-eeee-ffff-000011112222', '신규 가입 5% 할인', 'PERCENTAGE', 0.05, 2000, 960, '2024-09-30 23:59:59')
ON DUPLICATE KEY UPDATE title = VALUES(title);

INSERT INTO coupons (id, coupon_template_id, user_id, status, title, type, value, issued_at, expired_at, used_at, order_id)
VALUES
    ('11111111-1111-1111-1111-111111111111', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'aaaaaaaa-1111-2222-3333-444444444444', 'AVAILABLE', '10% 할인 쿠폰', 'PERCENTAGE', 0.10, '2024-03-01 08:30:00', '2024-12-31 23:59:59', NULL, NULL),
    ('22222222-2222-2222-2222-222222222222', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'aaaaaaaa-1111-2222-3333-444444444444', 'USED', '배송비 무료 쿠폰', 'GIFT', 0, '2024-02-10 10:15:00', '2024-06-30 23:59:59', '2024-02-18 12:04:00', '99999999-9999-9999-9999-999999999999'),
    ('33333333-3333-3333-3333-333333333333', 'bbbbcccc-dddd-eeee-ffff-000011112222', 'aaaaaaaa-1111-2222-3333-444444444444', 'EXPIRED', '신규 가입 5% 할인', 'PERCENTAGE', 0.05, '2023-01-01 00:00:00', '2023-12-31 23:59:59', NULL, NULL)
ON DUPLICATE KEY UPDATE status = VALUES(status);
