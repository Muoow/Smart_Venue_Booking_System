SET NAMES utf8mb4;

INSERT INTO user (id, username, nickname, full_name, status, created_at, balance, role)
VALUES
    (1, 'caojinshuo', '曹津硕', '曹津硕', 1, NOW(), 12000, 'USER'),
    (2, 'admin', '管理员', '管理员', 1, NOW(), 0, 'ADMIN'),
    (3, 'zhangxiang', '张翔', '张翔', 1, NOW(), 8000, 'USER')
ON DUPLICATE KEY UPDATE
    username = VALUES(username),
    nickname = VALUES(nickname),
    full_name = VALUES(full_name),
    status = VALUES(status),
    balance = VALUES(balance),
    role = VALUES(role);

INSERT INTO user_auth (id, user_id, identity_type, identifier, credential, last_login_at, created_at, updated_at)
VALUES
    (1, 1, 'username', 'caojinshuo', '$2a$10$sqFCNyKfS1jE7.Wi43rObOluwQgyCJUA9UTMkb7jeIXTM9M5fsuL6', NULL, NOW(), NOW()),
    (2, 2, 'username', 'admin', '$2a$10$sqFCNyKfS1jE7.Wi43rObOluwQgyCJUA9UTMkb7jeIXTM9M5fsuL6', NULL, NOW(), NOW()),
    (3, 3, 'username', 'zhangxiang', '$2a$10$sqFCNyKfS1jE7.Wi43rObOluwQgyCJUA9UTMkb7jeIXTM9M5fsuL6', NULL, NOW(), NOW())
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
    (3, '东区网球中心', '网球', 1, NOW()),
    (4, '南区羽毛球馆', '羽毛球', 1, NOW()),
    (5, '西区综合馆', '羽毛球/乒乓球', 1, NOW()),
    (6, '北区游泳馆', '游泳', 1, NOW())
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    type = VALUES(type),
    status = VALUES(status);

INSERT INTO venue_resource (id, venue_id, name, resource_type, capacity, price, unit_minutes, status)
VALUES
    (1, 1, '主体育馆 A3', 1, 4, 48, 10, 1),
    (2, 1, '主体育馆 A5', 1, 4, 42, 10, 1),
    (3, 2, '中央篮球馆 B2', 2, 10, 68, 20, 1),
    (4, 3, '东区网球 02', 3, 2, 58, 10, 1),
    (5, 4, '南区羽毛球 C1', 1, 4, 36, 10, 1),
    (6, 4, '南区羽毛球 C3', 1, 4, 39, 10, 1),
    (7, 5, '西区综合馆 P2', 5, 2, 24, 30, 1),
    (8, 5, '西区综合馆 A1', 1, 4, 34, 10, 1),
    (9, 6, '北区泳道 03', 6, 6, 72, 30, 1)
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
    (3, 1, 3, 4, NULL, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 5 DAY), '00:00:00'), 2, 120, 125, 2, NOW(), NOW()),
    (4, 1, 4, 5, 2, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '00:00:00'), 4, 108, 113, 1, NOW(), NOW()),
    (5, 1, 5, 8, NULL, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '00:00:00'), 4, 102, 107, 4, NOW(), NOW()),
    (6, 1, 4, 6, NULL, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 7 DAY), '00:00:00'), 4, 114, 119, 2, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    user_id = VALUES(user_id),
    venue_id = VALUES(venue_id),
    resource_id = VALUES(resource_id),
    order_id = VALUES(order_id),
    slot_date = VALUES(slot_date),
    size = VALUES(size),
    start_unit = VALUES(start_unit),
    end_unit = VALUES(end_unit),
    status = VALUES(status),
    updated_at = NOW();

INSERT INTO `order` (id, order_no, user_id, total_amount, status, expired_at, created_at, updated_at)
VALUES
    (1, 'CFDEMO0001', 1, 576, 1, DATE_ADD(NOW(), INTERVAL 30 MINUTE), NOW(), NOW()),
    (2, 'CFDEMO0002', 1, 216, 1, DATE_ADD(NOW(), INTERVAL 30 MINUTE), NOW(), NOW())
ON DUPLICATE KEY UPDATE
    order_no = VALUES(order_no),
    user_id = VALUES(user_id),
    total_amount = VALUES(total_amount),
    status = VALUES(status),
    expired_at = VALUES(expired_at),
    updated_at = NOW();

INSERT INTO payment (id, order_id, payment_no, biz_type, pay_channel, channel_trade_no, pay_amount, pay_status, status_note, paid_at, processed_at, created_at)
VALUES
    (1, 1, 'PAYDEMO0001', 1, 1, NULL, 576, 1, '钱包扣款成功，已完成内部记账。', NOW(), NOW(), NOW()),
    (2, 2, 'PAYDEMO0002', 1, 1, NULL, 216, 1, '钱包扣款成功，已完成内部记账。', NOW(), NOW(), NOW())
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
    (3, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '00:00:00'), 96, 0, 8, NOW(), NOW()),
    (3, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '00:00:00'), 97, 0, 8, NOW(), NOW()),
    (3, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '00:00:00'), 98, 0, 8, NOW(), NOW()),
    (3, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '00:00:00'), 99, 0, 8, NOW(), NOW()),
    (3, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '00:00:00'), 100, 0, 8, NOW(), NOW()),
    (3, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '00:00:00'), 101, 0, 8, NOW(), NOW()),
    (3, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '00:00:00'), 102, 0, 8, NOW(), NOW()),
    (3, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '00:00:00'), 103, 0, 8, NOW(), NOW()),
    (3, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '00:00:00'), 104, 0, 8, NOW(), NOW()),
    (5, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '00:00:00'), 108, 0, 4, NOW(), NOW()),
    (5, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '00:00:00'), 109, 0, 4, NOW(), NOW()),
    (5, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '00:00:00'), 110, 0, 4, NOW(), NOW()),
    (5, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '00:00:00'), 111, 0, 4, NOW(), NOW()),
    (5, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '00:00:00'), 112, 0, 4, NOW(), NOW()),
    (5, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '00:00:00'), 113, 0, 4, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    status = VALUES(status),
    booked_count = VALUES(booked_count),
    updated_at = NOW();

INSERT INTO venue_admin (id, user_id, venue_id)
VALUES
    (1, 2, 1),
    (2, 2, 2),
    (3, 2, 3),
    (4, 2, 4),
    (5, 2, 5),
    (6, 2, 6),
    (7, 4, 1),
    (8, 5, 2)
ON DUPLICATE KEY UPDATE
    user_id = VALUES(user_id),
    venue_id = VALUES(venue_id);
