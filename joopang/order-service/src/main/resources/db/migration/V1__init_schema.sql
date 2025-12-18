CREATE TABLE sellers (
    id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    type ENUM('BRAND', 'PERSON') NOT NULL,
    owner_id BIGINT NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE categories (
    id BIGINT NOT NULL,
    level INT NOT NULL,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    parent_id BIGINT NULL,
    PRIMARY KEY (id),
    INDEX idx_categories_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE users (
    id BIGINT NOT NULL,
    email VARCHAR(191) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    balance_amount DECIMAL(19, 2) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE products (
    id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    code VARCHAR(191) NOT NULL,
    description TEXT,
    content TEXT,
    status ENUM('ON_SALE') NOT NULL,
    seller_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    price_amount DECIMAL(19, 2) NOT NULL,
    discount_rate DECIMAL(5, 2),
    version INT NOT NULL,
    view_count INT NOT NULL,
    sales_count INT NOT NULL,
    created_at DATE NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_products_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE product_items (
    id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    unit_price DECIMAL(19, 2) NOT NULL,
    description TEXT,
    stock DECIMAL(19, 2) NOT NULL,
    status ENUM('ACTIVE') NOT NULL,
    code VARCHAR(191) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_items_code (code),
    INDEX idx_product_items_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE product_daily_sales (
    product_id BIGINT NOT NULL,
    sale_date DATE NOT NULL,
    quantity INT NOT NULL,
    PRIMARY KEY (product_id, sale_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cart_items (
    id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_item_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY idx_cart_items_user_product_item (user_id, product_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
    PRIMARY KEY (id),
    INDEX idx_coupons_user_template (user_id, coupon_template_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE orders (
    id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    image_url VARCHAR(512),
    status ENUM('PENDING', 'PAID', 'SHIPPING', 'DELIVERED', 'CANCELED', 'REFUNDED') NOT NULL,
    recipient_name VARCHAR(255) NOT NULL,
    order_month VARCHAR(7) NOT NULL,
    total_amount DECIMAL(19, 2) NOT NULL,
    discount_amount DECIMAL(19, 2) NOT NULL,
    ordered_at DATETIME(6) NOT NULL,
    paid_at DATETIME(6),
    memo TEXT,
    PRIMARY KEY (id),
    INDEX idx_orders_status_paid_at_desc (status, paid_at DESC),
    INDEX idx_orders_ordered_at_desc (ordered_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE order_items (
    id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    product_id BIGINT,
    product_item_id BIGINT,
    product_name VARCHAR(255) NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(19, 2) NOT NULL,
    subtotal DECIMAL(19, 2) NOT NULL,
    refunded_amount DECIMAL(19, 2) NOT NULL,
    refunded_quantity INT NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_order_items_order_id (order_id),
    INDEX idx_order_items_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE order_discounts (
    id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    type ENUM('POINT', 'COUPON') NOT NULL,
    reference_id BIGINT,
    price DECIMAL(19, 2) NOT NULL,
    coupon_id BIGINT,
    PRIMARY KEY (id),
    INDEX idx_order_discounts_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE deliveries (
    id BIGINT NOT NULL,
    order_item_id BIGINT NOT NULL,
    type ENUM('DIRECT_DELIVERY') NOT NULL,
    zip_code VARCHAR(10) NOT NULL,
    base_address VARCHAR(255) NOT NULL,
    detail_address VARCHAR(255),
    receiver_tel VARCHAR(32) NOT NULL,
    estimated_delivery_date DATE,
    status ENUM('PREPARING', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED', 'DELIVERY_FAILED') NOT NULL,
    tracking_number VARCHAR(255),
    delivery_fee DECIMAL(19, 2) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_deliveries_order_item_id (order_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE payments (
    id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    payment_gateway VARCHAR(255) NOT NULL,
    payment_method ENUM('CREDIT_CARD', 'BANK_TRANSFER', 'VIRTUAL_ACCOUNT', 'MOBILE_PAYMENT', 'COUPAY_MONEY', 'POINT') NOT NULL,
    payment_amount DECIMAL(19, 2) NOT NULL,
    remaining_balance DECIMAL(19, 2) NOT NULL,
    status ENUM('PENDING', 'COMPLETED', 'FAILED', 'CANCELLED', 'REFUNDED', 'PARTIAL_REFUNDED') NOT NULL,
    payment_key VARCHAR(255),
    transaction_id VARCHAR(255),
    requested_at DATETIME(6) NOT NULL,
    approved_at DATETIME(6),
    cancelled_at DATETIME(6),
    PRIMARY KEY (id),
    INDEX idx_payments_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE order_outbox_events (
    id BIGINT NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(191) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    payload LONGTEXT NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    published_at DATETIME(6),
    last_error TEXT,
    retry_count INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_order_outbox_events_status_created_at (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
