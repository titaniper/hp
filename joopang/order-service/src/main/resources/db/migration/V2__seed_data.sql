DELETE FROM order_outbox_events;
DELETE FROM payments;
DELETE FROM deliveries;
DELETE FROM order_discounts;
DELETE FROM order_items;
DELETE FROM orders;
DELETE FROM coupons;
DELETE FROM coupon_templates;
DELETE FROM cart_items;
DELETE FROM product_daily_sales;
DELETE FROM product_items;
DELETE FROM products;
DELETE FROM categories;
DELETE FROM sellers;
DELETE FROM users;

INSERT INTO sellers (id, name, type, owner_id) VALUES
    (200, 'Joopang Originals', 'BRAND', 100),
    (201, 'Handcrafted Goods', 'PERSON', 101);

INSERT INTO categories (id, level, name, status, parent_id) VALUES
    (300, 0, 'Electronics', 'ACTIVE', NULL),
    (301, 0, 'Beauty', 'ACTIVE', NULL),
    (302, 0, 'Lifestyle', 'ACTIVE', NULL);

INSERT INTO users (id, email, password_hash, first_name, last_name, balance_amount) VALUES
    (100, 'customer@joopang.com', 'hashedpassword', 'Joo', 'Pang', 500000),
    (101, 'vip@joopang.com', 'viphashed', 'Vip', 'Customer', 1000000);

INSERT INTO products (id, name, code, description, content, status, seller_id, category_id, price_amount, discount_rate, version, view_count, sales_count, created_at) VALUES
    (400, 'Galaxy Fold', 'GALAXY-FOLD', 'Latest foldable smartphone', 'Premium foldable experience with cutting-edge display.', 'ON_SALE', 200, 300, 239900, 5.00, 3, 4820, 350, '2024-05-20'),
    (401, 'Velvet Matte Lipstick', 'VELVET-LIP', 'Luxurious matte lipstick', 'Smooth matte finish with long-lasting color payoff.', 'ON_SALE', 200, 301, 25900, 15.00, 5, 10950, 1240, '2024-05-17'),
    (402, 'NeoBuds Pro', 'NEOBUDS-PRO', 'Noise-cancelling wireless earbuds', 'Adaptive ANC with studio-quality sound.', 'ON_SALE', 200, 300, 129000, NULL, 2, 6540, 780, '2024-05-21');

INSERT INTO product_items (id, product_id, name, unit_price, description, stock, status, code) VALUES
    (500, 400, 'Galaxy Fold Phantom Black', 239900, 'Phantom Black color option', 25, 'ACTIVE', 'GFOLD-BLK'),
    (501, 400, 'Galaxy Fold Matte Silver', 239900, 'Matte Silver color option', 18, 'ACTIVE', 'GFOLD-SLV'),
    (502, 401, 'Velvet Matte - Ruby Red', 25900, 'Bold ruby red shade', 120, 'ACTIVE', 'VELVET-RUBY'),
    (503, 401, 'Velvet Matte - Vintage Rose', 25900, 'Romantic vintage rose shade', 87, 'ACTIVE', 'VELVET-ROSE'),
    (504, 402, 'NeoBuds Pro - Graphite', 129000, 'Graphite color', 65, 'ACTIVE', 'NEOBUDS-GRP'),
    (505, 402, 'NeoBuds Pro - Pearl', 129000, 'Pearl white color', 54, 'ACTIVE', 'NEOBUDS-PRL');

INSERT INTO product_daily_sales (product_id, sale_date, quantity) VALUES
    (400, '2024-05-23', 24),
    (400, '2024-05-22', 17),
    (400, '2024-05-21', 11),
    (401, '2024-05-23', 56),
    (401, '2024-05-22', 42),
    (401, '2024-05-21', 31),
    (402, '2024-05-23', 28),
    (402, '2024-05-22', 33),
    (402, '2024-05-21', 19);

INSERT INTO cart_items (id, user_id, product_id, product_item_id, quantity) VALUES
    (600, 100, 400, 500, 1);

INSERT INTO coupon_templates (id, title, type, value, status, min_amount, max_discount_amount, total_quantity, issued_quantity, limit_quantity, start_at, end_at) VALUES
    (700, '신규 가입 10% 할인', 'PERCENTAGE', 0.10, 'ACTIVE', 50000, 30000, 1000, 150, 1, '2024-05-01 00:00:00', '2024-12-31 23:59:59'),
    (701, '5,000원 즉시 할인', 'AMOUNT', 5000, 'ACTIVE', 20000, NULL, 5000, 2345, 2, '2024-04-15 00:00:00', '2024-10-31 23:59:59');

INSERT INTO coupons (id, coupon_template_id, user_id, type, value, status, issued_at, expired_at, used_at, order_id) VALUES
    (800, 700, 100, 'PERCENTAGE', 0.10, 'AVAILABLE', '2024-05-10 09:00:00', '2024-12-31 23:59:59', NULL, NULL),
    (801, 701, 100, 'AMOUNT', 5000, 'USED', '2024-05-08 09:00:00', '2024-08-31 23:59:59', '2024-05-12 10:15:00', 900);

INSERT INTO orders (id, user_id, image_url, status, recipient_name, order_month, total_amount, discount_amount, ordered_at, paid_at, memo) VALUES
    (900, 100, 'https://cdn.joopang.com/orders/9999.png', 'PAID', 'Joo Pang', '2024-05', 264900, 5000, '2024-05-12 10:00:00', '2024-05-12 10:15:00', 'Leave at door');

INSERT INTO order_items (id, order_id, product_id, product_item_id, product_name, quantity, unit_price, subtotal, refunded_amount, refunded_quantity) VALUES
    (901, 900, 400, 500, 'Galaxy Fold Phantom Black', 1, 239900, 239900, 0, 0),
    (902, 900, 401, 502, 'Velvet Matte - Ruby Red', 1, 25900, 25900, 0, 0);

INSERT INTO order_discounts (id, order_id, type, reference_id, price, coupon_id) VALUES
    (903, 900, 'COUPON', 701, 5000, 801);

INSERT INTO deliveries (id, order_item_id, type, zip_code, base_address, detail_address, receiver_tel, estimated_delivery_date, status, tracking_number, delivery_fee) VALUES
    (904, 901, 'DIRECT_DELIVERY', '06000', 'Seoul, Gangnam-gu, Teheran-ro 152', '12F', '010-1234-5678', '2024-05-15', 'IN_TRANSIT', 'JP123456789KR', 3000);

INSERT INTO payments (id, order_id, payment_gateway, payment_method, payment_amount, remaining_balance, status, payment_key, transaction_id, requested_at, approved_at, cancelled_at) VALUES
    (905, 900, 'CoupayPay', 'CREDIT_CARD', 259900, 740100, 'COMPLETED', 'payment-key-7777', 'tx-7777', '2024-05-12 10:00:05', '2024-05-12 10:00:30', NULL);
