DELETE FROM coupons;
DELETE FROM coupon_templates;

INSERT INTO coupon_templates (id, title, type, value, status, min_amount, max_discount_amount, total_quantity, issued_quantity, limit_quantity, start_at, end_at) VALUES
    (700, '신규 가입 10% 할인', 'PERCENTAGE', 0.10, 'ACTIVE', 50000, 30000, 1000, 150, 1, '2024-05-01 00:00:00', '2024-12-31 23:59:59');

INSERT INTO coupons (id, coupon_template_id, user_id, type, value, status, issued_at, expired_at, used_at, order_id) VALUES
    (800, 700, 100, 'PERCENTAGE', 0.10, 'AVAILABLE', '2024-05-10 09:00:00', '2024-12-31 23:59:59', NULL, NULL);
