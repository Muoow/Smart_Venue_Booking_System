CREATE TABLE IF NOT EXISTS user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    nickname VARCHAR(64),
    full_name VARCHAR(64),
    status INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    balance BIGINT DEFAULT 0,
    role VARCHAR(32) DEFAULT 'USER'
);

CREATE TABLE IF NOT EXISTS user_auth (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    identity_type VARCHAR(32) NOT NULL,
    identifier VARCHAR(64) NOT NULL,
    credential VARCHAR(255) NOT NULL,
    last_login_at BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS venue (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    type VARCHAR(64),
    status INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS venue_resource (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    venue_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    resource_type INT NOT NULL,
    capacity INT,
    price INT,
    unit_minutes INT,
    status INT NOT NULL
);

CREATE TABLE IF NOT EXISTS reservation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    venue_id BIGINT NOT NULL,
    resource_id BIGINT NOT NULL,
    order_id BIGINT,
    slot_date TIMESTAMP NOT NULL,
    size INT NOT NULL,
    start_unit INT NOT NULL,
    end_unit INT NOT NULL,
    status INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS time_slot (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    resource_id BIGINT NOT NULL,
    slot_date TIMESTAMP NOT NULL,
    slot_unit INT NOT NULL,
    status INT NOT NULL,
    booked_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (resource_id, slot_date, slot_unit)
);

CREATE TABLE IF NOT EXISTS `order` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(64),
    user_id BIGINT NOT NULL,
    total_amount BIGINT,
    status INT,
    expired_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (order_no)
);

CREATE TABLE IF NOT EXISTS payment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    payment_no VARCHAR(64),
    biz_type INT NOT NULL,
    pay_channel INT,
    channel_trade_no VARCHAR(64),
    pay_amount BIGINT,
    pay_status INT,
    status_note VARCHAR(255),
    paid_at TIMESTAMP,
    processed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (payment_no),
    UNIQUE (channel_trade_no)
);

CREATE TABLE IF NOT EXISTS venue_admin (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    venue_id BIGINT NOT NULL
);
