INSERT INTO user (id, username, nickname, full_name, status, created_at, balance, role) VALUES
    (1, 'caojinshuo', '曹津硕', '曹津硕', 1, CURRENT_TIMESTAMP, 12000, 'USER'),
    (2, 'admin', '管理员', '管理员', 1, CURRENT_TIMESTAMP, 0, 'ADMIN'),
    (3, 'zhangxiang', '张翔', '张翔', 1, CURRENT_TIMESTAMP, 8000, 'USER');

INSERT INTO user_auth (id, user_id, identity_type, identifier, credential, last_login_at, created_at, updated_at) VALUES
    (1, 1, 'username', 'caojinshuo', '$2a$10$sqFCNyKfS1jE7.Wi43rObOluwQgyCJUA9UTMkb7jeIXTM9M5fsuL6', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, 2, 'username', 'admin', '$2a$10$BDcB1dak9S1Nh3nwQNfM/uD/tTH3AKSQy9eWd0.6Lyx8XhPzQhipi', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, 3, 'username', 'zhangxiang', '$2a$10$sqFCNyKfS1jE7.Wi43rObOluwQgyCJUA9UTMkb7jeIXTM9M5fsuL6', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO venue (id, name, type, status, created_at) VALUES
    (1, '主体育馆', '羽毛球/篮球', 1, CURRENT_TIMESTAMP),
    (2, '中央篮球馆', '篮球', 1, CURRENT_TIMESTAMP),
    (3, '东区网球中心', '网球', 1, CURRENT_TIMESTAMP),
    (4, '南区羽毛球馆', '羽毛球', 1, CURRENT_TIMESTAMP),
    (5, '西区综合馆', '羽毛球/乒乓球', 1, CURRENT_TIMESTAMP),
    (6, '北区游泳馆', '游泳', 1, CURRENT_TIMESTAMP);

INSERT INTO venue_resource (id, venue_id, name, resource_type, capacity, price, unit_minutes, status) VALUES
    (1, 1, '主体育馆 A3', 1, 4, 48, 10, 1),
    (2, 1, '主体育馆 A5', 1, 4, 42, 10, 1),
    (3, 2, '中央篮球馆 B2', 2, 10, 68, 20, 1),
    (4, 3, '东区网球 02', 3, 2, 58, 10, 1),
    (5, 4, '南区羽毛球 C1', 1, 4, 36, 10, 1),
    (6, 4, '南区羽毛球 C3', 1, 4, 39, 10, 1),
    (7, 5, '西区综合馆 P2', 5, 2, 24, 30, 1),
    (8, 5, '西区综合馆 A1', 1, 4, 34, 10, 1),
    (9, 6, '北区泳道 03', 6, 6, 72, 30, 1);

INSERT INTO reservation (id, user_id, venue_id, resource_id, order_id, slot_date, size, start_unit, end_unit, status, created_at, updated_at) VALUES
    (1, 1, 1, 1, 1, DATEADD('DAY', 1, CURRENT_TIMESTAMP), 4, 114, 125, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, 1, 2, 3, NULL, DATEADD('DAY', -2, CURRENT_TIMESTAMP), 8, 96, 104, 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, 1, 3, 4, NULL, DATEADD('DAY', -5, CURRENT_TIMESTAMP), 2, 120, 125, 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (4, 1, 4, 5, 2, DATEADD('DAY', 2, CURRENT_TIMESTAMP), 4, 108, 113, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (5, 1, 5, 8, NULL, DATEADD('DAY', -1, CURRENT_TIMESTAMP), 4, 102, 107, 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (6, 1, 4, 6, NULL, DATEADD('DAY', -7, CURRENT_TIMESTAMP), 4, 114, 119, 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO "order" (id, order_no, user_id, total_amount, status, expired_at, created_at, updated_at) VALUES
    (1, 'CFDEMO0001', 1, 576, 1, DATEADD('MINUTE', 30, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, 'CFDEMO0002', 1, 216, 1, DATEADD('MINUTE', 30, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO payment (id, order_id, payment_no, biz_type, pay_channel, channel_trade_no, pay_amount, pay_status, status_note, paid_at, processed_at, created_at) VALUES
    (1, 1, 'PAYDEMO0001', 1, 1, NULL, 576, 1, '钱包扣款成功，已完成内部记账。', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, 2, 'PAYDEMO0002', 1, 1, NULL, 216, 1, '钱包扣款成功，已完成内部记账。', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
