DROP TABLE IF EXISTS coupons;
DROP TABLE IF EXISTS coupon_templates;

CREATE TABLE coupon_templates (
    id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    type ENUM('PERCENTAGE', 'AMOUNT', 'GIFT') NOT NULL,
    value DECIMAL(19, 4) NOT NULL,
    status ENUM('DRAFT', 'ACTIVE', 'PAUSED', 'ENDED') NOT NULL,
    min_amount DECIMAL(19, 2),
    max_discount_amount DECIMAL(19, 2),
    total_quantity INT NOT NULL,
    issued_quantity INT NOT NULL,
    limit_quantity INT NOT NULL,
    start_at DATETIME(6),
    end_at DATETIME(6),
    PRIMARY KEY (id)
);

CREATE TABLE coupons (
    id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    coupon_template_id BIGINT,
    type ENUM('PERCENTAGE', 'AMOUNT', 'GIFT') NOT NULL,
    value DECIMAL(19, 4) NOT NULL,
    status ENUM('AVAILABLE', 'USED', 'EXPIRED') NOT NULL,
    issued_at DATETIME(6) NOT NULL,
    used_at DATETIME(6),
    expired_at DATETIME(6),
    order_id BIGINT,
    PRIMARY KEY (id)
);
