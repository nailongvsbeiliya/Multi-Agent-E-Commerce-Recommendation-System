-- Idempotent schema + demo data initializer for PostgreSQL
\encoding UTF8
SET client_min_messages TO warning;

CREATE TABLE IF NOT EXISTS product_catalog (
    product_id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    category VARCHAR(100) NOT NULL,
    price DOUBLE PRECISION NOT NULL,
    description VARCHAR(1000),
    brand VARCHAR(120),
    seller_id VARCHAR(64),
    stock INTEGER NOT NULL,
    brand_preference_weight DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    sales_heat DOUBLE PRECISION NOT NULL DEFAULT 0.5,
    tags VARCHAR(1000)
);

ALTER TABLE product_catalog
    ADD COLUMN IF NOT EXISTS brand_preference_weight DOUBLE PRECISION NOT NULL DEFAULT 1.0;

ALTER TABLE product_catalog
    ADD COLUMN IF NOT EXISTS sales_heat DOUBLE PRECISION NOT NULL DEFAULT 0.5;

CREATE TABLE IF NOT EXISTS inventory (
    product_id VARCHAR(64) PRIMARY KEY,
    available_stock INTEGER NOT NULL,
    reserved_stock INTEGER NOT NULL,
    last_updated TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS recommendation_event (
    id BIGSERIAL PRIMARY KEY,
    request_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    scene VARCHAR(32) NOT NULL,
    num_items INTEGER NOT NULL,
    experiment_group VARCHAR(32) NOT NULL,
    strategy VARCHAR(64) NOT NULL,
    total_latency_ms DOUBLE PRECISION NOT NULL,
    converted BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_recommendation_event_user_id
    ON recommendation_event (user_id);

CREATE INDEX IF NOT EXISTS idx_recommendation_event_created_at
    ON recommendation_event (created_at);

CREATE OR REPLACE FUNCTION sync_inventory_from_catalog()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    INSERT INTO inventory (product_id, available_stock, reserved_stock, last_updated)
    VALUES (NEW.product_id, NEW.stock, GREATEST(0, NEW.stock / 20), NOW())
    ON CONFLICT (product_id) DO UPDATE
    SET available_stock = EXCLUDED.available_stock,
        reserved_stock = EXCLUDED.reserved_stock,
        last_updated = NOW();
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_sync_inventory_from_catalog ON product_catalog;
CREATE TRIGGER trg_sync_inventory_from_catalog
AFTER INSERT OR UPDATE OF stock ON product_catalog
FOR EACH ROW
EXECUTE FUNCTION sync_inventory_from_catalog();

INSERT INTO product_catalog (
    product_id,
    name,
    category,
    price,
    description,
    brand,
    seller_id,
    stock,
    tags
)
VALUES
    ('P001', 'iPhone 16 Pro', '手机', 7999, '高端旗舰手机', 'Apple', 'S01', 500, '旗舰,新品'),
    ('P002', '华为 Mate 70', '手机', 5999, '商务旗舰手机', '华为', 'S02', 300, '旗舰,国产'),
    ('P003', 'AirPods Pro 3', '耳机', 1899, '无线降噪耳机', 'Apple', 'S01', 1000, '降噪,无线'),
    ('P004', 'Sony WH-1000XM6', '耳机', 2499, '高端头戴耳机', 'Sony', 'S03', 200, '头戴,降噪'),
    ('P005', 'iPad Air M3', '平板', 4799, '轻薄生产力平板', 'Apple', 'S01', 400, '学习,办公'),
    ('P006', '小米平板 7 Pro', '平板', 2499, '高性价比平板', '小米', 'S04', 600, '性价比,娱乐'),
    ('P007', 'Anker 140W 充电器', '配件', 399, '高功率充电器', 'Anker', 'S05', 2000, '快充,便携'),
    ('P008', '机械革命极光 X', '笔记本', 6999, '高性能游戏本', '机械革命', 'S06', 150, '游戏,高性能'),
    ('P009', 'Dell U2724D 显示器', '显示器', 3299, '高分辨率显示器', 'Dell', 'S07', 80, '4K,办公'),
    ('P010', '罗技 MX Master 3S', '配件', 749, '人体工学鼠标', 'Logitech', 'S08', 500, '鼠标,办公'),
    ('P011', 'Samsung Galaxy S25', '手机', 6999, '安卓旗舰影像手机', 'Samsung', 'S09', 280, '旗舰,安卓'),
    ('P012', 'vivo X200 Pro', '手机', 5299, '高端影像旗舰手机', 'vivo', 'S10', 260, '影像,新品'),
    ('P013', 'OPPO Find X8', '手机', 4999, '轻薄手感旗舰手机', 'OPPO', 'S11', 240, '轻薄,新品'),
    ('P014', 'Redmi K80 Pro', '手机', 3699, '高性能性价比手机', 'Redmi', 'S04', 500, '性价比,游戏'),
    ('P015', 'Bose QC Ultra', '耳机', 2299, '旗舰级主动降噪耳机', 'Bose', 'S12', 180, '降噪,头戴'),
    ('P016', '华为 FreeBuds Pro 4', '耳机', 1499, '通勤友好的无线耳机', '华为', 'S02', 420, '无线,降噪'),
    ('P017', 'Beats Studio Pro', '耳机', 1999, '潮流风格头戴式耳机', 'Beats', 'S13', 170, '头戴,潮流'),
    ('P018', '小米 Buds 5 Pro', '耳机', 999, '入门高性价比降噪耳机', '小米', 'S04', 620, '性价比,降噪'),
    ('P019', '华为 MatePad Pro 13.2', '平板', 5699, '轻办公旗舰平板', '华为', 'S02', 210, '办公,旗舰'),
    ('P020', 'Samsung Galaxy Tab S10', '平板', 5199, '高刷影音旗舰平板', 'Samsung', 'S09', 200, '影音,旗舰'),
    ('P021', '联想小新 Pro 16 2026', '笔记本', 6299, '轻薄全能办公本', 'Lenovo', 'S14', 230, '办公,性价比'),
    ('P022', 'MacBook Air M4', '笔记本', 8999, '新品高续航轻薄笔记本', 'Apple', 'S01', 190, '轻薄,新品'),
    ('P023', 'ThinkPad X1 Carbon Gen13', '笔记本', 11999, '高可靠商务笔记本', 'Lenovo', 'S14', 120, '商务,高端'),
    ('P024', '华硕天选 Air', '笔记本', 7599, '轻薄游戏本', 'ASUS', 'S15', 160, '游戏,高性能'),
    ('P025', 'AOC Q27G4 显示器', '显示器', 1799, '2K高刷电竞显示器', 'AOC', 'S16', 260, '高刷,电竞'),
    ('P026', 'LG 27UP850N 显示器', '显示器', 2999, '4K设计师显示器', 'LG', 'S17', 140, '4K,设计'),
    ('P027', 'Apple Watch Series 10', '穿戴', 2999, '健康管理智能手表', 'Apple', 'S01', 360, '健康,新品'),
    ('P028', '华为 WATCH GT 5 Pro', '穿戴', 2488, '长续航运动手表', '华为', 'S02', 330, '运动,续航'),
    ('P029', '佳能 EOS R8', '相机', 9999, '轻便全画幅微单', 'Canon', 'S18', 90, '全画幅,Vlog'),
    ('P030', '大疆 Osmo Pocket 4', '相机', 3499, '口袋云台相机', 'DJI', 'S19', 260, 'Vlog,便携')
ON CONFLICT (product_id) DO UPDATE
SET name = EXCLUDED.name,
    category = EXCLUDED.category,
    price = EXCLUDED.price,
    description = EXCLUDED.description,
    brand = EXCLUDED.brand,
    seller_id = EXCLUDED.seller_id,
    stock = EXCLUDED.stock,
    tags = EXCLUDED.tags;

INSERT INTO product_catalog (
    product_id,
    name,
    category,
    price,
    description,
    brand,
    seller_id,
    stock,
    tags
)
VALUES
    ('P031', 'Redmi 13C', '手机', 899, '入门高性价比智能手机', 'Redmi', 'S20', 850, '入门,性价比'),
    ('P032', '荣耀 X60', '手机', 1299, '长续航大众机型', 'Honor', 'S21', 720, '入门,续航'),
    ('P033', 'iQOO Neo10', '手机', 2699, '高性能游戏手机', 'iQOO', 'S22', 510, '高性能,性价比'),
    ('P034', 'OnePlus 13', '手机', 4499, '均衡旗舰体验手机', 'OnePlus', 'S23', 360, '旗舰,影像'),
    ('P035', 'iPhone 16 Pro Max', '手机', 9999, '高端旗舰影像手机', 'Apple', 'S01', 180, '旗舰,高端'),
    ('P036', '华为 Pura 80 Ultra', '手机', 9999, '高端影像旗舰手机', '华为', 'S02', 160, '旗舰,影像'),
    ('P037', '漫步者 W820NB Plus', '耳机', 299, '入门主动降噪头戴耳机', 'Edifier', 'S24', 900, '入门,降噪'),
    ('P038', 'QCY AilyPods Pro', '耳机', 229, '通勤真无线耳机', 'QCY', 'S25', 1200, '入门,性价比'),
    ('P039', 'Soundcore Liberty 4 NC', '耳机', 599, '中端降噪真无线耳机', 'Soundcore', 'S05', 680, '降噪,性价比'),
    ('P040', 'Sony WF-1000XM5', '耳机', 1599, '旗舰降噪真无线耳机', 'Sony', 'S03', 260, '旗舰,降噪'),
    ('P041', 'Sennheiser Momentum 4', '耳机', 2499, '高端头戴耳机', 'Sennheiser', 'S26', 170, '高端,头戴'),
    ('P042', 'Redmi Pad SE', '平板', 999, '入门娱乐平板', 'Redmi', 'S20', 760, '入门,性价比'),
    ('P043', '荣耀平板 9', '平板', 1599, '学习办公平板', 'Honor', 'S21', 540, '学习,性价比'),
    ('P044', 'iPad 11', '平板', 2999, '均衡体验平板', 'Apple', 'S01', 430, '办公,均衡'),
    ('P045', '华为 MatePad Air 12', '平板', 3299, '轻办公高刷平板', '华为', 'S02', 300, '办公,高刷'),
    ('P046', 'iPad Pro 13 M4', '平板', 8999, '高端生产力平板', 'Apple', 'S01', 130, '高端,旗舰'),
    ('P047', '惠普战66 六代', '笔记本', 4599, '商务入门办公本', 'HP', 'S27', 350, '办公,性价比'),
    ('P048', '华硕无畏 Pro 15', '笔记本', 5499, '全能轻薄本', 'ASUS', 'S15', 310, '轻薄,性价比'),
    ('P049', '联想拯救者 Y9000P', '笔记本', 8999, '高性能游戏本', 'Lenovo', 'S14', 190, '游戏,高性能'),
    ('P050', 'ROG 幻16 Air', '笔记本', 12999, '高端轻薄游戏本', 'ROG', 'S28', 110, '高端,游戏'),
    ('P051', 'MacBook Pro 14 M4 Pro', '笔记本', 14999, '专业创作旗舰本', 'Apple', 'S01', 95, '高端,旗舰'),
    ('P052', 'HKC MG24Q', '显示器', 899, '入门电竞显示器', 'HKC', 'S29', 520, '入门,高刷'),
    ('P053', '小米 2K 27寸显示器', '显示器', 1299, '高性价比办公显示器', '小米', 'S04', 480, '性价比,办公'),
    ('P054', 'Dell S2722QC', '显示器', 2299, '4K Type-C 显示器', 'Dell', 'S07', 260, '4K,办公'),
    ('P055', 'LG 32UN880', '显示器', 3999, '专业设计显示器', 'LG', 'S17', 140, '4K,设计'),
    ('P056', 'Apple Studio Display', '显示器', 11499, '高端创作显示器', 'Apple', 'S01', 70, '高端,旗舰'),
    ('P057', 'Redmi Watch 5', '穿戴', 399, '入门智能手表', 'Redmi', 'S20', 620, '入门,性价比'),
    ('P058', '小米手环 9 Pro', '穿戴', 449, '轻量健康穿戴设备', '小米', 'S04', 780, '健康,性价比'),
    ('P059', 'Garmin Forerunner 265', '穿戴', 2780, '专业跑步运动手表', 'Garmin', 'S30', 160, '运动,高端'),
    ('P060', 'Apple Watch Ultra 3', '穿戴', 6499, '高端户外智能手表', 'Apple', 'S01', 120, '高端,运动'),
    ('P061', '华为 WATCH Ultimate', '穿戴', 5999, '高端商务运动手表', '华为', 'S02', 135, '高端,旗舰'),
    ('P062', 'Canon EOS R50', '相机', 4599, '入门微单相机', 'Canon', 'S18', 180, '入门,Vlog'),
    ('P063', 'Sony ZV-E10 II', '相机', 5999, 'Vlog 创作微单', 'Sony', 'S03', 165, 'Vlog,影像'),
    ('P064', 'Nikon Zf', '相机', 12999, '高端复古全画幅相机', 'Nikon', 'S31', 80, '高端,全画幅'),
    ('P065', 'Fujifilm X100VI', '相机', 11999, '便携高端街拍相机', 'Fujifilm', 'S32', 65, '高端,便携'),
    ('P066', 'DJI Air 4S', '无人机', 6999, '航拍无人机套装', 'DJI', 'S19', 120, '影像,高性能'),
    ('P067', 'Insta360 X5', '运动相机', 3299, '全景运动相机', 'Insta360', 'S33', 220, 'Vlog,便携'),
    ('P068', '雷柏 V500 Pro', '键盘', 199, '入门机械键盘', 'Rapoo', 'S34', 900, '入门,性价比'),
    ('P069', 'Logitech G Pro X Superlight 2', '鼠标', 999, '轻量化电竞鼠标', 'Logitech', 'S08', 300, '高性能,电竞'),
    ('P070', '金士顿 NV3 1TB', '存储', 449, '入门 PCIe 固态硬盘', 'Kingston', 'S35', 700, '入门,性价比'),
    ('P071', '西部数据 SN850X 2TB', '存储', 1199, '高性能 NVMe 固态硬盘', 'WD', 'S36', 360, '高性能,高端'),
    ('P072', '华硕 AX86U Pro', '路由器', 1499, '高性能家庭路由器', 'ASUS', 'S15', 260, '高性能,旗舰'),
    ('P073', 'TP-Link AX3000', '路由器', 299, '高性价比 Wi-Fi 6 路由器', 'TP-Link', 'S37', 980, '性价比,入门'),
    ('P074', '公牛 67W 氮化镓充电器', '充电器', 129, '便携快充充电器', 'BULL', 'S38', 1300, '入门,快充'),
    ('P075', 'Anker Prime 200W 充电器', '充电器', 699, '桌面多口高功率充电器', 'Anker', 'S05', 420, '高性能,快充'),
    ('P076', '罗技 C930e 摄像头', '配件', 599, '高清会议摄像头', 'Logitech', 'S08', 340, '办公,均衡'),
    ('P077', '极米 Play 5 投影仪', '投影', 2699, '家庭便携投影', 'XGIMI', 'S39', 190, '便携,性价比'),
    ('P078', '小米电视 S65 MiniLED', '电视', 4999, '高刷 MiniLED 电视', '小米', 'S04', 140, '影音,高性价比'),
    ('P079', 'TCL T7K 55寸', '电视', 2699, '入门高性价比 4K 电视', 'TCL', 'S40', 260, '入门,性价比'),
    ('P080', '石头 G20S Ultra', '清洁电器', 4999, '旗舰扫拖机器人', 'Roborock', 'S41', 120, '旗舰,智能'),
    ('P081', '追觅 H30 Ultra', '清洁电器', 2999, '高端洗地机', 'Dreame', 'S42', 180, '高端,智能'),
    ('P082', '小米空气净化器 4 Lite', '家电', 899, '入门空气净化器', '小米', 'S04', 420, '入门,健康'),
    ('P083', '美的 1.5匹空调 酷省电', '家电', 2999, '高性价比节能空调', 'Midea', 'S43', 110, '性价比,节能'),
    ('P084', '海尔 470L 冰箱', '家电', 3599, '大容量风冷冰箱', 'Haier', 'S44', 90, '家用,均衡'),
    ('P085', '米家智能门锁 Pro', '智能家居', 1299, '全自动智能门锁', 'Mijia', 'S45', 230, '智能,性价比'),
    ('P086', '天猫精灵 IN糖', '智能音箱', 399, '语音助手智能音箱', 'TmallGenie', 'S46', 540, '入门,智能'),
    ('P087', '惠普 136w 黑白激光打印机', '办公设备', 1199, '家用办公打印机', 'HP', 'S27', 210, '办公,性价比'),
    ('P088', '佳能 G3810 喷墨一体机', '办公设备', 999, '彩色打印复印一体机', 'Canon', 'S18', 200, '办公,入门'),
    ('P089', 'Nintendo Switch OLED', '游戏设备', 2299, '掌机主机一体游戏机', 'Nintendo', 'S47', 260, '娱乐,均衡'),
    ('P090', '索尼 PS5 Slim', '游戏设备', 3599, '次世代家用主机', 'Sony', 'S03', 170, '高性能,娱乐')
ON CONFLICT (product_id) DO UPDATE
SET name = EXCLUDED.name,
    category = EXCLUDED.category,
    price = EXCLUDED.price,
    description = EXCLUDED.description,
    brand = EXCLUDED.brand,
    seller_id = EXCLUDED.seller_id,
    stock = EXCLUDED.stock,
    tags = EXCLUDED.tags;

UPDATE product_catalog
SET
    brand_preference_weight = CASE brand
        WHEN 'Apple' THEN 1.25
        WHEN '华为' THEN 1.18
        WHEN 'Sony' THEN 1.15
        WHEN 'Samsung' THEN 1.12
        WHEN 'DJI' THEN 1.13
        WHEN 'Lenovo' THEN 1.08
        WHEN 'ASUS' THEN 1.08
        WHEN 'Logitech' THEN 1.10
        WHEN 'Canon' THEN 1.09
        WHEN 'Nikon' THEN 1.08
        WHEN 'Fujifilm' THEN 1.08
        WHEN 'Xiaomi' THEN 1.00
        WHEN '小米' THEN 1.00
        WHEN 'Redmi' THEN 0.96
        WHEN 'Honor' THEN 0.98
        WHEN 'QCY' THEN 0.88
        ELSE 1.00
    END,
    sales_heat = LEAST(0.98, GREATEST(0.10,
        0.18
        + CASE
            WHEN stock >= 1000 THEN 0.42
            WHEN stock >= 600 THEN 0.34
            WHEN stock >= 300 THEN 0.26
            WHEN stock >= 150 THEN 0.18
            ELSE 0.10
          END
        + CASE
            WHEN tags LIKE '%新品%' THEN 0.08
            ELSE 0.00
          END
        + CASE
            WHEN tags LIKE '%旗舰%' THEN 0.08
            WHEN tags LIKE '%性价比%' THEN 0.06
            ELSE 0.00
          END
        + CASE
            WHEN price BETWEEN 1000 AND 7000 THEN 0.06
            WHEN price < 1000 THEN 0.04
            ELSE 0.02
          END
    ));

INSERT INTO recommendation_event (
    request_id,
    user_id,
    scene,
    num_items,
    experiment_group,
    strategy,
    total_latency_ms,
    converted,
    created_at
)
SELECT
    SUBSTRING(MD5(RANDOM()::TEXT || CLOCK_TIMESTAMP()::TEXT || sampled.i::TEXT), 1, 32) AS request_id,
    'u' || LPAD((1 + FLOOR(RANDOM() * 120))::INT::TEXT, 3, '0') AS user_id,
    sampled.scene,
    (3 + FLOOR(RANDOM() * 4))::INT AS num_items,
    sampled.experiment_group,
    CASE sampled.experiment_group
        WHEN 'treatment_llm' THEN 'llm_rerank'
        WHEN 'explore' THEN 'explore_diversity'
        ELSE 'rule_based'
    END AS strategy,
    ROUND(
        GREATEST(
            120.0,
            calc.base_latency + (RANDOM() - 0.5) * 180.0
        )::NUMERIC,
        1
    )::DOUBLE PRECISION AS total_latency_ms,
    (RANDOM() < calc.convert_probability) AS converted,
    NOW() - ((FLOOR(RANDOM() * 64800))::INT || ' minutes')::INTERVAL AS created_at
FROM (
    SELECT
        g.i,
        (ARRAY['homepage', 'detail', 'campaign', 'search', 'cart'])[1 + FLOOR(RANDOM() * 5)::INT] AS scene,
        (ARRAY['control', 'treatment_llm', 'explore'])[1 + FLOOR(RANDOM() * 3)::INT] AS experiment_group
    FROM GENERATE_SERIES(1, 300) AS g(i)
) AS sampled
CROSS JOIN LATERAL (
    SELECT
        CASE sampled.experiment_group
            WHEN 'treatment_llm' THEN 580.0
            WHEN 'explore' THEN 470.0
            ELSE 360.0
        END AS base_latency,
        LEAST(
            0.45,
            CASE sampled.experiment_group
                WHEN 'treatment_llm' THEN 0.08
                WHEN 'explore' THEN 0.05
                ELSE 0.03
            END
            +
            CASE sampled.scene
                WHEN 'detail' THEN 0.06
                WHEN 'campaign' THEN 0.05
                WHEN 'cart' THEN 0.07
                WHEN 'search' THEN 0.04
                ELSE 0.03
            END
        ) AS convert_probability
) AS calc
WHERE NOT EXISTS (SELECT 1 FROM recommendation_event);

SELECT COUNT(*) AS product_catalog_rows FROM product_catalog;
SELECT COUNT(*) AS inventory_rows FROM inventory;
SELECT COUNT(*) AS recommendation_event_rows FROM recommendation_event;
