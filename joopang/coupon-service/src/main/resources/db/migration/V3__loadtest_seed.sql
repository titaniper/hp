-- -- Seed data to exercise large-scale coupon issuance & ordering
-- INSERT INTO products (id, name, code, description, content, status, seller_id, category_id, price_amount, discount_rate, version, view_count, sales_count, created_at)
-- VALUES
--     (1000, 'Load Test Bundle', 'LOADTEST-BUNDLE', 'High stock product for load tests', 'Synthetic catalog entry for performance scenarios', 'ON_SALE', 200, 300, 19900, NULL, 1, 0, 0, NOW());

-- INSERT INTO product_items (id, product_id, name, unit_price, description, stock, status, code)
-- VALUES
--     (1500, 1000, 'Load Test Bundle - Default', 19900, 'Primary SKU with massive stock for tests', 500000, 'ACTIVE', 'LOADTEST-BUNDLE-DEFAULT');

-- INSERT INTO coupon_templates (id, title, type, value, status, min_amount, max_discount_amount, total_quantity, issued_quantity, limit_quantity, start_at, end_at)
-- VALUES
--     (900, '러시 전용 15% 할인', 'PERCENTAGE', 0.15, 'ACTIVE', 10000, 50000, 10000, 0, 1, '2024-01-01 00:00:00', '2025-12-31 23:59:59');

-- Generate 1,000 users (id: 1000-1999)
 -- postgres
-- WITH RECURSIVE seq AS (
--     SELECT 0 AS idx
--     UNION ALL
--     SELECT idx + 1 FROM seq WHERE idx < 999
-- )
-- INSERT INTO users (id, email, password_hash, first_name, last_name, balance_amount)
-- SELECT 1000 + idx,
--        CONCAT('rush+', 1000 + idx, '@joopang.com'),
--        'loadtest-hash',
--        'Rush',
--        CONCAT('User', 1000 + idx),
--        1000000
-- FROM seq;

INSERT INTO users (id, email, password_hash, first_name, last_name, balance_amount)
WITH RECURSIVE seq (idx) AS (
    SELECT 0
    UNION ALL
    SELECT idx + 1 FROM seq WHERE idx < 999
)
SELECT 1000 + idx,
       CONCAT('rush+', 1000 + idx, '@joopang.com'),
       'loadtest-hash',
       'Rush',
       CONCAT('User', 1000 + idx),
       1000000
FROM seq;

-- Generate 1,000 users (id: 2000-2999)
-- WITH RECURSIVE seq2 AS (
--     SELECT 0 AS idx
--     UNION ALL
--     SELECT idx + 1 FROM seq2 WHERE idx < 999
-- )
-- INSERT INTO users (id, email, password_hash, first_name, last_name, balance_amount)
-- SELECT 2000 + idx,
--        CONCAT('rush+', 2000 + idx, '@joopang.com'),
--        'loadtest-hash',
--        'Rush',
--        CONCAT('User', 2000 + idx),
--        1000000
-- FROM seq2;
INSERT INTO users (id, email, password_hash, first_name, last_name, balance_amount)
WITH RECURSIVE seq2 (idx) AS (
    SELECT 0
    UNION ALL
    SELECT idx + 1 FROM seq2 WHERE idx < 999
)
SELECT 2000 + idx,
       CONCAT('rush+', 2000 + idx, '@joopang.com'),
       'loadtest-hash',
       'Rush',
       CONCAT('User', 2000 + idx),
       1000000
FROM seq2;
