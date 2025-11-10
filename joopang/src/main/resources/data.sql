INSERT INTO sellers (id, name, type, owner_id)
VALUES
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'Joopang Originals', 'BRAND', 'aaaaaaaa-1111-2222-3333-444444444444'),
    ('bbbbbbbb-aaaa-bbbb-cccc-dddddddddddd', 'Handcrafted Goods', 'PERSON', 'bbbbbbbb-1111-2222-3333-444444444444')
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO categories (id, level, name, status, parent_id)
VALUES
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 0, 'Electronics', 'ACTIVE', NULL),
    ('cccccccc-cccc-cccc-cccc-cccccccccccc', 0, 'Beauty', 'ACTIVE', NULL),
    ('dddddddd-dddd-dddd-dddd-dddddddddddd', 0, 'Lifestyle', 'ACTIVE', NULL)
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO users (id, email, password_hash, first_name, last_name, balance_amount)
VALUES
    ('aaaaaaaa-1111-2222-3333-444444444444', 'customer@joopang.com', 'hashedpassword', 'Joo', 'Pang', 500000),
    ('bbbbbbbb-1111-2222-3333-444444444444', 'vip@joopang.com', 'viphashed', 'Vip', 'Customer', 1000000)
ON DUPLICATE KEY UPDATE email = VALUES(email);

INSERT INTO products (id, name, code, description, content, status, seller_id, category_id, price_amount, discount_rate, version, view_count, sales_count, created_at)
VALUES
    ('11111111-1111-1111-1111-111111111111', 'Galaxy Fold', 'GALAXY-FOLD', 'Latest foldable smartphone', 'Premium foldable experience with cutting-edge display.', 'ON_SALE', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 239900, 5.00, 3, 4820, 350, '2024-05-20'),
    ('22222222-2222-2222-2222-222222222222', 'Velvet Matte Lipstick', 'VELVET-LIP', 'Luxurious matte lipstick', 'Smooth matte finish with long-lasting color payoff.', 'ON_SALE', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'cccccccc-cccc-cccc-cccc-cccccccccccc', 25900, 15.00, 5, 10950, 1240, '2024-05-17'),
    ('33333333-3333-3333-3333-333333333333', 'NeoBuds Pro', 'NEOBUDS-PRO', 'Noise-cancelling wireless earbuds', 'Adaptive ANC with studio-quality sound.', 'ON_SALE', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 129000, NULL, 2, 6540, 780, '2024-05-21')
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO product_items (id, product_id, name, unit_price, description, stock, status, code)
VALUES
    ('21111111-1111-1111-1111-111111111111', '11111111-1111-1111-1111-111111111111', 'Galaxy Fold Phantom Black', 239900, 'Phantom Black color option', 25, 'ACTIVE', 'GFOLD-BLK'),
    ('21111111-1111-1111-1111-222222222222', '11111111-1111-1111-1111-111111111111', 'Galaxy Fold Matte Silver', 239900, 'Matte Silver color option', 18, 'ACTIVE', 'GFOLD-SLV'),
    ('23333333-3333-3333-3333-333333333333', '22222222-2222-2222-2222-222222222222', 'Velvet Matte - Ruby Red', 25900, 'Bold ruby red shade', 120, 'ACTIVE', 'VELVET-RUBY'),
    ('24444444-4444-4444-4444-444444444444', '22222222-2222-2222-2222-222222222222', 'Velvet Matte - Vintage Rose', 25900, 'Romantic vintage rose shade', 87, 'ACTIVE', 'VELVET-ROSE'),
    ('25555555-5555-5555-5555-555555555555', '33333333-3333-3333-3333-333333333333', 'NeoBuds Pro - Graphite', 129000, 'Graphite color', 65, 'ACTIVE', 'NEOBUDS-GRP'),
    ('26666666-6666-6666-6666-666666666666', '33333333-3333-3333-3333-333333333333', 'NeoBuds Pro - Pearl', 129000, 'Pearl white color', 54, 'ACTIVE', 'NEOBUDS-PRL')
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO product_daily_sales (product_id, sale_date, quantity)
VALUES
    ('11111111-1111-1111-1111-111111111111', DATE_SUB(CURDATE(), INTERVAL 1 DAY), 24),
    ('11111111-1111-1111-1111-111111111111', DATE_SUB(CURDATE(), INTERVAL 2 DAY), 17),
    ('11111111-1111-1111-1111-111111111111', DATE_SUB(CURDATE(), INTERVAL 3 DAY), 11),
    ('22222222-2222-2222-2222-222222222222', DATE_SUB(CURDATE(), INTERVAL 1 DAY), 56),
    ('22222222-2222-2222-2222-222222222222', DATE_SUB(CURDATE(), INTERVAL 2 DAY), 42),
    ('22222222-2222-2222-2222-222222222222', DATE_SUB(CURDATE(), INTERVAL 3 DAY), 31),
    ('33333333-3333-3333-3333-333333333333', DATE_SUB(CURDATE(), INTERVAL 1 DAY), 28),
    ('33333333-3333-3333-3333-333333333333', DATE_SUB(CURDATE(), INTERVAL 2 DAY), 33),
    ('33333333-3333-3333-3333-333333333333', DATE_SUB(CURDATE(), INTERVAL 3 DAY), 19)
ON DUPLICATE KEY UPDATE quantity = VALUES(quantity);

INSERT INTO cart_items (id, user_id, product_id, product_item_id, quantity)
VALUES
    ('aaaaaaaa-0000-0000-0000-aaaaaaaa0000', 'aaaaaaaa-1111-2222-3333-444444444444', '11111111-1111-1111-1111-111111111111', '21111111-1111-1111-1111-111111111111', 1)
ON DUPLICATE KEY UPDATE quantity = VALUES(quantity);

INSERT INTO coupon_templates (id, title, type, value, status, min_amount, max_discount_amount, total_quantity, issued_quantity, limit_quantity, start_at, end_at)
VALUES
    ('aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee', '신규 가입 10% 할인', 'PERCENTAGE', 0.10, 'ACTIVE', 50000, 30000, 1000, 150, 1, '2024-05-01 00:00:00', '2024-12-31 23:59:59'),
    ('bbbbbbbb-cccc-dddd-eeee-ffffffffffff', '5,000원 즉시 할인', 'AMOUNT', 5000, 'ACTIVE', 20000, NULL, 5000, 2345, 2, '2024-04-15 00:00:00', '2024-10-31 23:59:59')
ON DUPLICATE KEY UPDATE title = VALUES(title);

INSERT INTO coupons (id, coupon_template_id, user_id, type, value, status, issued_at, expired_at, used_at, order_id)
VALUES
    ('10101010-2020-3030-4040-505050505050', 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee', 'aaaaaaaa-1111-2222-3333-444444444444', 'PERCENTAGE', 0.10, 'AVAILABLE', '2024-05-10 09:00:00', '2024-12-31 23:59:59', NULL, NULL),
    ('60606060-7070-8080-9090-a0a0a0a0a0a0', 'bbbbbbbb-cccc-dddd-eeee-ffffffffffff', 'aaaaaaaa-1111-2222-3333-444444444444', 'AMOUNT', 5000, 'USED', '2024-05-08 09:00:00', '2024-08-31 23:59:59', '2024-05-12 10:15:00', '99999999-9999-9999-9999-999999999999')
ON DUPLICATE KEY UPDATE status = VALUES(status);

INSERT INTO orders (id, user_id, image_url, status, recipient_name, order_month, total_amount, discount_amount, ordered_at, paid_at, memo)
VALUES
    ('99999999-9999-9999-9999-999999999999', 'aaaaaaaa-1111-2222-3333-444444444444', 'https://cdn.joopang.com/orders/9999.png', 'PAID', 'Joo Pang', '2024-05', 264900, 5000, '2024-05-12 10:00:00', '2024-05-12 10:15:00', 'Leave at door')
ON DUPLICATE KEY UPDATE recipient_name = VALUES(recipient_name);

INSERT INTO order_items (id, order_id, product_id, product_item_id, product_name, quantity, unit_price, subtotal, refunded_amount, refunded_quantity)
VALUES
    ('aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee', '99999999-9999-9999-9999-999999999999', '11111111-1111-1111-1111-111111111111', '21111111-1111-1111-1111-111111111111', 'Galaxy Fold Phantom Black', 1, 239900, 239900, 0, 0),
    ('bbbbbbbb-cccc-dddd-eeee-ffffffffffff', '99999999-9999-9999-9999-999999999999', '22222222-2222-2222-2222-222222222222', '23333333-3333-3333-3333-333333333333', 'Velvet Matte - Ruby Red', 1, 25900, 25900, 0, 0)
ON DUPLICATE KEY UPDATE product_name = VALUES(product_name);

INSERT INTO order_discounts (id, order_id, type, reference_id, price, coupon_id)
VALUES
    ('88888888-9999-aaaa-bbbb-cccccccccccc', '99999999-9999-9999-9999-999999999999', 'COUPON', 'bbbbbbbb-cccc-dddd-eeee-ffffffffffff', 5000, '60606060-7070-8080-9090-a0a0a0a0a0a0')
ON DUPLICATE KEY UPDATE price = VALUES(price);

INSERT INTO deliveries (id, order_item_id, type, zip_code, base_address, detail_address, receiver_tel, estimated_delivery_date, status, tracking_number, delivery_fee)
VALUES
    ('66666666-7777-8888-9999-000000000000', 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee', 'DIRECT_DELIVERY', '06000', 'Seoul, Gangnam-gu, Teheran-ro 152', '12F', '010-1234-5678', DATE_ADD(CURDATE(), INTERVAL 3 DAY), 'IN_TRANSIT', 'JP123456789KR', 3000)
ON DUPLICATE KEY UPDATE tracking_number = VALUES(tracking_number);

INSERT INTO payments (id, order_id, payment_gateway, payment_method, payment_amount, remaining_balance, status, payment_key, transaction_id, requested_at, approved_at, cancelled_at)
VALUES
    ('77777777-8888-9999-aaaa-bbbbbbbbbbbb', '99999999-9999-9999-9999-999999999999', 'CoupayPay', 'CREDIT_CARD', 259900, 740100, 'COMPLETED', 'payment-key-7777', 'tx-7777', '2024-05-12 10:00:05', '2024-05-12 10:00:30', NULL)
ON DUPLICATE KEY UPDATE status = VALUES(status);
