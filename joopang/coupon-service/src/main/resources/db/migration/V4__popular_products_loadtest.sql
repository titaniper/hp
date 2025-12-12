SET SESSION cte_max_recursion_depth = 50000;
SET FOREIGN_KEY_CHECKS = 0;

-- Delete existing load test data to avoid conflicts
DELETE FROM product_daily_sales WHERE product_id >= 1000;
DELETE FROM order_items WHERE product_id >= 1000;
DELETE FROM cart_items WHERE product_id >= 1000;
DELETE FROM product_items WHERE product_id >= 1000;
DELETE FROM products WHERE id >= 1000;
TRUNCATE joopang.orders;
TRUNCATE joopang.order_items;


SET FOREIGN_KEY_CHECKS = 1;

-- Synthetic catalog for popular-products load tests (7000 products with 2 items each)
INSERT INTO products (id, name, code, description, content, status, seller_id, category_id, price_amount, discount_rate, version, view_count, sales_count, created_at)
WITH RECURSIVE prod_seq (idx) AS (
    SELECT 0
    UNION ALL
    SELECT idx + 1 FROM prod_seq WHERE idx < 6999
)
SELECT
    1000 + idx AS id,
    CONCAT('Load Test Product ', LPAD(idx, 4, '0')) AS name,
    CONCAT('LOAD-PROD-', LPAD(idx, 4, '0')) AS code,
    CONCAT('Synthetic load-test product #', idx) AS description,
    CONCAT('Bulk generated catalog entry #', idx) AS content,
    'ON_SALE' AS status,
    CASE WHEN idx % 2 = 0 THEN 200 ELSE 201 END AS seller_id,
    300 + (idx % 3) AS category_id,
    25000 + (idx * 500) AS price_amount,
    CASE WHEN idx % 4 = 0 THEN 5.00 ELSE NULL END AS discount_rate,
    1 AS version,
    1000 + (idx * 25) AS view_count,
    500 + (idx * 20) AS sales_count,
    DATE_SUB(CURDATE(), INTERVAL (idx % 14) DAY) AS created_at
FROM prod_seq;

INSERT INTO product_items (id, product_id, name, unit_price, description, stock, status, code)
WITH RECURSIVE prod_seq (idx) AS (
    SELECT 0
    UNION ALL
    SELECT idx + 1 FROM prod_seq WHERE idx < 6999
)
SELECT
    3000 + (idx * 2) + variant.variant AS id,
    1000 + idx AS product_id,
    CONCAT('Load Test Product ', LPAD(idx, 4, '0'), ' Option ', variant.variant + 1) AS name,
    25000 + (idx * 500) AS unit_price,
    CONCAT('Variant ', variant.variant + 1, ' for load product ', idx) AS description,
    100000 - (idx * 500) - (variant.variant * 100) AS stock,
    'ACTIVE' AS status,
    CONCAT('LOAD-ITEM-', LPAD(idx, 4, '0'), '-', variant.variant + 1) AS code
FROM prod_seq
CROSS JOIN (SELECT 0 AS variant UNION ALL SELECT 1 AS variant) AS variant;

-- 600 paid orders distributed to amplify the /api/products/top query
INSERT INTO orders (id, user_id, image_url, status, recipient_name, order_month, total_amount, discount_amount, ordered_at, paid_at, memo)
WITH RECURSIVE order_seq (idx) AS (
    SELECT 0
    UNION ALL
    SELECT idx + 1 FROM order_seq WHERE idx < 3000
)
SELECT
    id,
    user_id,
    image_url,
    'PAID' AS status,
    recipient_name,
    order_month,
    total_amount,
    0 AS discount_amount,
    ordered_at,
    paid_at,
    'load test order' AS memo
FROM (
    SELECT
        20000 + idx AS id,
        1000 + (idx % 2000) AS user_id,
        CONCAT('https://cdn.joopang.com/loadtest/orders/', 20000 + idx, '.png') AS image_url,
        CONCAT('Load Tester ', LPAD(idx % 2000, 4, '0')) AS recipient_name,
        DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL (idx % 7) DAY), '%Y-%m') AS order_month,
        (25000 + product_slot * 500) * quantity AS total_amount,
        DATE_SUB(NOW(), INTERVAL ((idx % 7) * 24 + 2) HOUR) AS ordered_at,
        DATE_SUB(NOW(), INTERVAL ((idx % 7) * 24) HOUR) AS paid_at
    FROM (
        SELECT
            idx,
            CASE
                WHEN idx < 300 THEN 0
                WHEN idx < 520 THEN 1
                ELSE ((idx - 520) % 18) + 2
            END AS product_slot,
            1 + (idx % 3) AS quantity
        FROM order_seq
    ) slot
) prepared;

INSERT INTO order_items (id, order_id, product_id, product_item_id, product_name, quantity, unit_price, subtotal, refunded_amount, refunded_quantity)
WITH RECURSIVE order_seq (idx) AS (
    SELECT 0
    UNION ALL
    SELECT idx + 1 FROM order_seq WHERE idx < 3000
)
SELECT
    30000 + idx AS id,
    20000 + idx AS order_id,
    1000 + product_slot AS product_id,
    3000 + (product_slot * 2) + (idx % 2) AS product_item_id,
    CONCAT('Load Test Product ', LPAD(product_slot, 4, '0')) AS product_name,
    quantity,
    25000 + (product_slot * 500) AS unit_price,
    (25000 + (product_slot * 500)) * quantity AS subtotal,
    0 AS refunded_amount,
    0 AS refunded_quantity
FROM (
    SELECT
        idx,
        CASE
            WHEN idx < 300 THEN 0
            WHEN idx < 520 THEN 1
            ELSE ((idx - 520) % 18) + 2
        END AS product_slot,
        1 + (idx % 3) AS quantity
    FROM order_seq
) slot;
