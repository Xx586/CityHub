CREATE TABLE tb_user (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    phone VARCHAR(20) NOT NULL,
    password VARCHAR(128) NULL,
    nick_name VARCHAR(64) NOT NULL,
    icon VARCHAR(255) NOT NULL DEFAULT '',
    role VARCHAR(16) NOT NULL DEFAULT 'USER',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tb_shop (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL,
    type_id BIGINT UNSIGNED NOT NULL,
    images VARCHAR(1024) NOT NULL DEFAULT '',
    area VARCHAR(128) NOT NULL DEFAULT '',
    address VARCHAR(255) NOT NULL DEFAULT '',
    x DOUBLE NULL,
    y DOUBLE NULL,
    avg_price BIGINT NULL,
    sold INT NOT NULL DEFAULT 0,
    comments INT NOT NULL DEFAULT 0,
    score INT NOT NULL DEFAULT 0,
    open_hours VARCHAR(64) NOT NULL DEFAULT '',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_shop_type (type_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tb_voucher (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    shop_id BIGINT UNSIGNED NOT NULL,
    title VARCHAR(128) NOT NULL,
    sub_title VARCHAR(255) NOT NULL DEFAULT '',
    rules VARCHAR(1024) NOT NULL DEFAULT '',
    pay_value BIGINT NOT NULL,
    actual_value BIGINT NOT NULL,
    type TINYINT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_voucher_shop (shop_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tb_seckill_voucher (
    voucher_id BIGINT UNSIGNED NOT NULL,
    stock INT NOT NULL,
    begin_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (voucher_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tb_voucher_order (
    id BIGINT NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    voucher_id BIGINT UNSIGNED NOT NULL,
    pay_type TINYINT NOT NULL DEFAULT 1,
    status VARCHAR(16) NOT NULL DEFAULT 'UNPAID',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    pay_time TIMESTAMP NULL,
    use_time TIMESTAMP NULL,
    refund_time TIMESTAMP NULL,
    cancel_time TIMESTAMP NULL,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_user_voucher (user_id, voucher_id),
    KEY idx_order_user_time (user_id, create_time),
    KEY idx_order_status_time (status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tb_outbox_event (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_outbox_status_retry (status, next_retry_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO tb_user(phone, nick_name, role) VALUES
('13800000000', 'CityHub管理员', 'ADMIN'),
('13900000000', '演示用户', 'USER');

INSERT INTO tb_shop(id, name, type_id, area, address, avg_price, sold, comments, score, open_hours) VALUES
(1, 'CityHub 茶餐厅', 1, '徐家汇', '示例路 100 号', 80, 1200, 368, 47, '10:00-22:00'),
(2, 'CityHub 烘焙坊', 1, '静安寺', '示例路 200 号', 45, 860, 215, 48, '08:00-21:00');

INSERT INTO tb_voucher(id, shop_id, title, sub_title, rules, pay_value, actual_value, type, status) VALUES
(1, 1, '50元秒杀代金券', '限时抢购', '不可叠加，每人限购一张', 3000, 5000, 1, 1),
(2, 1, '100元普通代金券', '全场通用', '不可兑现', 8000, 10000, 0, 1);

INSERT INTO tb_seckill_voucher(voucher_id, stock, begin_time, end_time) VALUES
(1, 100, '2025-01-01 00:00:00', '2030-12-31 23:59:59');
