SET NAMES utf8mb4;

INSERT INTO user (id, username, status, created_at, balance, role)
VALUES
    (1, 'demo', 1, NOW(), 12000, 'USER'),
    (2, 'admin', 1, NOW(), 0, 'ADMIN')
ON DUPLICATE KEY UPDATE
    username = VALUES(username),
    status = VALUES(status),
    balance = VALUES(balance),
    role = VALUES(role);

INSERT INTO user_auth (id, user_id, identity_type, identifier, credential, last_login_at, created_at, updated_at)
VALUES
    (1, 1, 'username', 'demo', '$2a$10$BDcB1dak9S1Nh3nwQNfM/uD/tTH3AKSQy9eWd0.6Lyx8XhPzQhipi', NULL, NOW(), NOW()),
    (2, 2, 'username', 'admin', '$2a$10$BDcB1dak9S1Nh3nwQNfM/uD/tTH3AKSQy9eWd0.6Lyx8XhPzQhipi', NULL, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    user_id = VALUES(user_id),
    identity_type = VALUES(identity_type),
    identifier = VALUES(identifier),
    credential = VALUES(credential),
    updated_at = NOW();

INSERT INTO venue (id, name, type, status, created_at)
VALUES
    (1, '主体育馆', '羽毛球/篮球', 1, NOW()),
    (2, '中央篮球馆', '篮球', 1, NOW()),
    (3, '东区网球中心', '网球', 1, NOW())
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    type = VALUES(type),
    status = VALUES(status);

INSERT INTO venue_resource (id, venue_id, name, resource_type, capacity, price, unit_minutes, status)
VALUES
    (1, 1, '主体育馆 A3', 1, 4, 48, 10, 1),
    (2, 1, '主体育馆 A5', 1, 4, 42, 10, 1),
    (3, 2, '中央篮球馆 B2', 2, 10, 68, 20, 1),
    (4, 3, '东区网球 02', 3, 2, 58, 10, 1)
ON DUPLICATE KEY UPDATE
    venue_id = VALUES(venue_id),
    name = VALUES(name),
    resource_type = VALUES(resource_type),
    capacity = VALUES(capacity),
    price = VALUES(price),
    unit_minutes = VALUES(unit_minutes),
    status = VALUES(status);

INSERT INTO reservation (id, user_id, venue_id, resource_id, order_id, slot_date, size, start_unit, end_unit, status, created_at, updated_at)
VALUES
    (1, 1, 1, 1, 1, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '00:00:00'), 4, 114, 125, 1, NOW(), NOW()),
    (2, 1, 2, 3, NULL, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '00:00:00'), 8, 96, 104, 4, NOW(), NOW()),
    (3, 1, 3, 4, NULL, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 5 DAY), '00:00:00'), 2, 120, 125, 2, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    user_id = VALUES(user_id),
    venue_id = VALUES(venue_id),
    resource_id = VALUES(resource_id),
    slot_date = VALUES(slot_date),
    size = VALUES(size),
    start_unit = VALUES(start_unit),
    end_unit = VALUES(end_unit),
    status = VALUES(status),
    updated_at = NOW();

INSERT INTO `order` (id, order_no, user_id, total_amount, status, expired_at, created_at, updated_at)
VALUES
    (1, 'CFDEMO0001', 1, 576, 1, DATE_ADD(NOW(), INTERVAL 30 MINUTE), NOW(), NOW())
ON DUPLICATE KEY UPDATE
    order_no = VALUES(order_no),
    user_id = VALUES(user_id),
    total_amount = VALUES(total_amount),
    status = VALUES(status),
    expired_at = VALUES(expired_at),
    updated_at = NOW();

INSERT INTO payment (id, order_id, payment_no, biz_type, pay_channel, channel_trade_no, pay_amount, pay_status, status_note, paid_at, processed_at, created_at)
VALUES
    (1, 1, 'PAYDEMO0001', 1, 1, NULL, 576, 1, '钱包扣款成功，已完成内部记账。', NOW(), NOW(), NOW())
ON DUPLICATE KEY UPDATE
    order_id = VALUES(order_id),
    payment_no = VALUES(payment_no),
    biz_type = VALUES(biz_type),
    pay_channel = VALUES(pay_channel),
    channel_trade_no = VALUES(channel_trade_no),
    pay_amount = VALUES(pay_amount),
    pay_status = VALUES(pay_status),
    status_note = VALUES(status_note),
    paid_at = VALUES(paid_at),
    processed_at = VALUES(processed_at);

INSERT INTO time_slot (resource_id, slot_date, slot_unit, status, booked_count, created_at, updated_at)
VALUES
    (1, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '00:00:00'), 114, 0, 4, NOW(), NOW()),
    (1, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '00:00:00'), 115, 0, 4, NOW(), NOW()),
    (1, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '00:00:00'), 116, 0, 4, NOW(), NOW()),
    (1, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '00:00:00'), 117, 0, 4, NOW(), NOW()),
    (1, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '00:00:00'), 118, 0, 4, NOW(), NOW()),
    (1, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '00:00:00'), 119, 0, 4, NOW(), NOW()),
    (1, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '00:00:00'), 120, 0, 4, NOW(), NOW()),
    (1, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '00:00:00'), 121, 0, 4, NOW(), NOW()),
    (1, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '00:00:00'), 122, 0, 4, NOW(), NOW()),
    (1, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '00:00:00'), 123, 0, 4, NOW(), NOW()),
    (1, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '00:00:00'), 124, 0, 4, NOW(), NOW()),
    (1, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '00:00:00'), 125, 0, 4, NOW(), NOW()),
    (2, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '00:00:00'), 108, 0, 0, NOW(), NOW()),
    (2, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '00:00:00'), 109, 0, 0, NOW(), NOW()),
    (2, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '00:00:00'), 110, 0, 0, NOW(), NOW()),
    (2, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '00:00:00'), 111, 0, 0, NOW(), NOW()),
    (2, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '00:00:00'), 112, 0, 0, NOW(), NOW()),
    (2, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '00:00:00'), 113, 0, 0, NOW(), NOW()),
    (3, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '00:00:00'), 96, 0, 0, NOW(), NOW()),
    (3, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '00:00:00'), 97, 0, 0, NOW(), NOW()),
    (3, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '00:00:00'), 98, 0, 0, NOW(), NOW()),
    (3, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '00:00:00'), 99, 0, 0, NOW(), NOW()),
    (3, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '00:00:00'), 100, 0, 0, NOW(), NOW()),
    (3, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '00:00:00'), 101, 0, 0, NOW(), NOW()),
    (3, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '00:00:00'), 102, 0, 0, NOW(), NOW()),
    (3, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '00:00:00'), 103, 0, 0, NOW(), NOW()),
    (3, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '00:00:00'), 104, 0, 0, NOW(), NOW()),
    (4, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '00:00:00'), 120, 0, 0, NOW(), NOW()),
    (4, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '00:00:00'), 121, 0, 0, NOW(), NOW()),
    (4, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '00:00:00'), 122, 0, 0, NOW(), NOW()),
    (4, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '00:00:00'), 123, 0, 0, NOW(), NOW()),
    (4, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '00:00:00'), 124, 0, 0, NOW(), NOW()),
    (4, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '00:00:00'), 125, 0, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    status = VALUES(status),
    booked_count = VALUES(booked_count),
    updated_at = NOW();

INSERT INTO venue_admin (id, user_id, venue_id)
VALUES
    (1, 2, 1),
    (2, 2, 2),
    (3, 2, 3)
ON DUPLICATE KEY UPDATE
    user_id = VALUES(user_id),
    venue_id = VALUES(venue_id);
