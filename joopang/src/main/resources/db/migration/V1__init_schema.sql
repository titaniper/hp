CREATE TABLE sellers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(32) NOT NULL,
    owner_id BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE categories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    level INT NOT NULL,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    parent_id BIGINT NULL,
    INDEX idx_categories_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(191) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(255) NULL,
    last_name VARCHAR(255) NULL,
    balance_amount DECIMAL(19,2) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    code VARCHAR(191) NOT NULL UNIQUE,
    description TEXT NULL,
    content TEXT NULL,
    status VARCHAR(32) NOT NULL,
    seller_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    price_amount DECIMAL(19,2) NOT NULL,
    discount_rate DECIMAL(5,2) NULL,
    version INT NOT NULL,
    view_count INT NOT NULL,
    sales_count INT NOT NULL,
    created_at DATE NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE product_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    unit_price DECIMAL(19,2) NOT NULL,
    description TEXT NULL,
    stock DECIMAL(19,2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    code VARCHAR(191) NOT NULL UNIQUE,
    INDEX idx_product_items_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE product_daily_sales (
    product_id BIGINT NOT NULL,
    sale_date DATE NOT NULL,
    quantity INT NOT NULL,
    PRIMARY KEY (product_id, sale_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cart_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_item_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    UNIQUE KEY uk_cart_items_user_product_item (user_id, product_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE coupon_templates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    type VARCHAR(32) NOT NULL,
    value DECIMAL(19,4) NOT NULL,
    status VARCHAR(32) NOT NULL,
    min_amount DECIMAL(19,2) NULL,
    max_discount_amount DECIMAL(19,2) NULL,
    total_quantity INT NOT NULL,
    issued_quantity INT NOT NULL,
    limit_quantity INT NOT NULL,
    start_at DATETIME(6) NULL,
    end_at DATETIME(6) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE coupons (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    coupon_template_id BIGINT NULL,
    type VARCHAR(32) NOT NULL,
    value DECIMAL(19,4) NOT NULL,
    status VARCHAR(32) NOT NULL,
    issued_at DATETIME(6) NOT NULL,
    used_at DATETIME(6) NULL,
    expired_at DATETIME(6) NULL,
    order_id BIGINT NULL,
    INDEX idx_coupons_user_template (user_id, coupon_template_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    image_url VARCHAR(512) NULL,
    status VARCHAR(32) NOT NULL,
    recipient_name VARCHAR(255) NOT NULL,
    order_month CHAR(7) NOT NULL,
    total_amount DECIMAL(19,2) NOT NULL,
    discount_amount DECIMAL(19,2) NOT NULL,
    ordered_at DATETIME(6) NOT NULL,
    paid_at DATETIME(6) NULL,
    memo TEXT NULL,
    INDEX idx_orders_status_paid_at_desc (status, paid_at DESC),
    INDEX idx_orders_ordered_at_desc (ordered_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE order_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NULL,
    product_item_id BIGINT NULL,
    product_name VARCHAR(255) NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(19,2) NOT NULL,
    subtotal DECIMAL(19,2) NOT NULL,
    refunded_amount DECIMAL(19,2) NOT NULL,
    refunded_quantity INT NOT NULL,
    INDEX idx_order_items_order_id (order_id),
    INDEX idx_order_items_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE order_discounts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    type VARCHAR(32) NOT NULL,
    reference_id BIGINT NULL,
    price DECIMAL(19,2) NOT NULL,
    coupon_id BIGINT NULL,
    INDEX idx_order_discounts_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE deliveries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_item_id BIGINT NOT NULL,
    type VARCHAR(32) NOT NULL,
    zip_code VARCHAR(10) NOT NULL,
    base_address VARCHAR(255) NOT NULL,
    detail_address VARCHAR(255) NULL,
    receiver_tel VARCHAR(32) NOT NULL,
    estimated_delivery_date DATE NULL,
    status VARCHAR(32) NOT NULL,
    tracking_number VARCHAR(255) NULL,
    delivery_fee DECIMAL(19,2) NOT NULL,
    INDEX idx_deliveries_order_item_id (order_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE payments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    payment_gateway VARCHAR(255) NOT NULL,
    payment_method VARCHAR(32) NOT NULL,
    payment_amount DECIMAL(19,2) NOT NULL,
    remaining_balance DECIMAL(19,2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    payment_key VARCHAR(255) NULL,
    transaction_id VARCHAR(255) NULL,
    requested_at DATETIME(6) NOT NULL,
    approved_at DATETIME(6) NULL,
    cancelled_at DATETIME(6) NULL,
    INDEX idx_payments_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
