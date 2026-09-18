-- RuoYi Robot schema additions, executed after framework and Quartz initialization.
-- Append-only integration surface: append idempotent DDL here in domain task order.
-- Do not reset upstream tables or insert sample business records.
-- The selected database is supplied by Compose/Testcontainers; do not hardcode USE.
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Task 2 intentionally defines no business tables. Tasks 3 onward append their schema.

-- Task 3: Product scope and tenant robot quota. The product table deliberately does
-- not use the generic tenant interceptor because public rows use tenant_id = NULL.
CREATE TABLE IF NOT EXISTS `device_product` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NULL COMMENT '租户编号；NULL 表示平台公共产品型号',
  `scope_tenant_id` bigint GENERATED ALWAYS AS (IFNULL(`tenant_id`, 0)) STORED COMMENT '公共/租户范围唯一键',
  `product_key` varchar(64) NOT NULL COMMENT '产品型号标识',
  `name` varchar(128) NOT NULL COMMENT '产品型号名称',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态（0启用 1停用）',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_device_product_scope_key` (`scope_tenant_id`, `product_key`),
  KEY `idx_device_product_visibility` (`tenant_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='产品型号';

CREATE TABLE IF NOT EXISTS `tenant_quota` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户编号',
  `robot_limit` int NOT NULL DEFAULT 0 COMMENT '机器人数量上限',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_quota_tenant` (`tenant_id`),
  CONSTRAINT `chk_tenant_quota_robot_limit` CHECK (`robot_limit` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户机器人配额';

CREATE TABLE IF NOT EXISTS `tenant_usage` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户编号',
  `robot_used` int NOT NULL DEFAULT 0 COMMENT '已激活机器人数量',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_usage_tenant` (`tenant_id`),
  CONSTRAINT `chk_tenant_usage_robot_used` CHECK (`robot_used` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户机器人用量';

-- Task 4: inventory rows are the only rows allowed to have tenant_id NULL.
CREATE TABLE IF NOT EXISTS `device` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NULL COMMENT '激活后的租户编号；库存设备为 NULL',
  `product_id` bigint NOT NULL COMMENT '库存指定产品型号',
  `robot_id` bigint NULL COMMENT '绑定机器人',
  `device_sn` varchar(128) NOT NULL COMMENT '设备全局序列号',
  `name` varchar(128) NOT NULL COMMENT '设备名称',
  `lifecycle_status` varchar(32) NOT NULL DEFAULT 'UNACTIVATED' COMMENT 'UNACTIVATED/ACTIVATED/DISABLED/MAINTENANCE/SCRAPPED',
  `credential_version` int NOT NULL DEFAULT 0 COMMENT '凭据版本',
  `mqtt_username` varchar(255) DEFAULT NULL COMMENT 'MQTT用户名',
  `mqtt_secret_hash` varchar(255) DEFAULT NULL COMMENT 'MQTT密钥哈希',
  `http_secret_ciphertext` varchar(1024) DEFAULT NULL COMMENT 'HTTP HMAC 密钥 AES-GCM 密文',
  `activate_time` datetime(3) DEFAULT NULL, `last_bind_time` datetime(3) DEFAULT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` varchar(64) DEFAULT '', `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`), UNIQUE KEY `uk_device_sn` (`device_sn`), UNIQUE KEY `uk_device_mqtt_username` (`mqtt_username`),
  KEY `idx_device_tenant_lifecycle` (`tenant_id`, `lifecycle_status`), KEY `idx_device_product` (`product_id`),
  CONSTRAINT `chk_device_inventory_scope` CHECK ((`lifecycle_status` = 'UNACTIVATED' AND `tenant_id` IS NULL) OR (`lifecycle_status` <> 'UNACTIVATED' AND `tenant_id` IS NOT NULL)),
  CONSTRAINT `chk_device_lifecycle_status` CHECK (`lifecycle_status` IN ('UNACTIVATED','ACTIVATED','DISABLED','MAINTENANCE','SCRAPPED')),
  CONSTRAINT `chk_device_credential_version` CHECK (`credential_version` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备库存和激活绑定';

CREATE TABLE IF NOT EXISTS `device_group` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL, `name` varchar(128) NOT NULL,
  `remark` varchar(500) DEFAULT NULL, `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', PRIMARY KEY (`id`),
  UNIQUE KEY `uk_device_group_tenant_name` (`tenant_id`, `name`), KEY `idx_device_group_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备组';

CREATE TABLE IF NOT EXISTS `device_group_relation` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL, `group_id` bigint NOT NULL, `device_id` bigint NOT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', PRIMARY KEY (`id`),
  UNIQUE KEY `uk_device_group_member` (`tenant_id`, `group_id`, `device_id`),
  KEY `idx_device_group_relation_device` (`tenant_id`, `device_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备组成员';

-- Task 5: robots are tenant-owned from birth. Device provisioning is the only creator
-- of a device-backed row, and physical deprovisioning releases uk_robot_device for reuse.
CREATE TABLE IF NOT EXISTS `robot` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '所属租户',
  `device_id` bigint NOT NULL COMMENT '绑定设备',
  `product_id` bigint NOT NULL COMMENT '产品型号',
  `robot_code` varchar(64) NOT NULL COMMENT '租户内机器人编号',
  `name` varchar(128) NOT NULL COMMENT '机器人名称',
  `online_status` varchar(32) NOT NULL DEFAULT 'OFFLINE' COMMENT 'ONLINE/OFFLINE',
  `work_status` varchar(32) NOT NULL DEFAULT 'IDLE' COMMENT 'robot work status',
  `battery_level` tinyint DEFAULT NULL COMMENT '最近遥测电量，0-100',
  `ip_address` varchar(45) DEFAULT NULL COMMENT '最近遥测 IP',
  `software_version` varchar(64) DEFAULT NULL COMMENT '最近遥测软件版本',
  `current_mission_id` varchar(128) DEFAULT NULL COMMENT '最近遥测任务',
  `last_heartbeat_time` datetime(3) DEFAULT NULL COMMENT '最后心跳时间',
  `creator` varchar(64) DEFAULT '', `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` varchar(64) DEFAULT '', `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_robot_tenant_code` (`tenant_id`, `robot_code`),
  UNIQUE KEY `uk_robot_device` (`device_id`),
  KEY `idx_robot_tenant_status` (`tenant_id`, `online_status`, `work_status`),
  CONSTRAINT `chk_robot_battery_level` CHECK (`battery_level` IS NULL OR (`battery_level` >= 0 AND `battery_level` <= 100)),
  CONSTRAINT `chk_robot_online_status` CHECK (`online_status` IN ('OFFLINE','ONLINE')),
  CONSTRAINT `chk_robot_work_status` CHECK (`work_status` IN ('IDLE','WORKING','CHARGING','FAULT','NAVIGATING','INSPECTING','ERROR','UPGRADING'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户机器人';

-- Durable inbox precedes Redis so a process restart cannot re-run a published device message.
CREATE TABLE IF NOT EXISTS `robot_message_inbox` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL, `device_id` bigint NOT NULL,
  `message_id` varchar(64) NOT NULL, `request_id` varchar(128) NOT NULL, `topic` varchar(512) NOT NULL,
  `message_type` varchar(64) NOT NULL, `payload_hash` char(64) NOT NULL, `result` varchar(32) NOT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` varchar(64) DEFAULT '', `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` bit(1) NOT NULL DEFAULT b'0', PRIMARY KEY (`id`),
  UNIQUE KEY `uk_robot_message_inbox_tenant_device_message` (`tenant_id`,`device_id`,`message_id`),
  KEY `idx_robot_message_inbox_tenant_device_received` (`tenant_id`,`device_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='机器人上行消息幂等收件箱';

-- Realtime state events are committed atomically with robot state and retried until delivered.
-- This is intentionally separate from Task 11's command outbox: direction and lifecycle differ.
CREATE TABLE IF NOT EXISTS `robot_realtime_event_outbox` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL, `robot_id` bigint NOT NULL,
  `event_key` varchar(255) COLLATE utf8mb4_bin NOT NULL, `event_type` varchar(64) NOT NULL, `payload` text NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'PENDING', `attempt_count` int NOT NULL DEFAULT 0,
  `next_attempt_time` datetime(3) NOT NULL, `claimed_at` datetime(3) DEFAULT NULL, `last_error` varchar(512) DEFAULT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` varchar(64) DEFAULT '', `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` bit(1) NOT NULL DEFAULT b'0', PRIMARY KEY (`id`),
  UNIQUE KEY `uk_robot_realtime_event_outbox_key` (`tenant_id`,`event_key`),
  KEY `idx_robot_realtime_event_outbox_due` (`status`,`next_attempt_time`,`claimed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='机器人实时状态事件发件箱';

-- Task 11: command delivery is separate from realtime notifications because a broker acknowledgement
-- is a transport fact, not proof that the robot executed the command.
CREATE TABLE IF NOT EXISTS `robot_command_outbox` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL, `device_id` bigint NOT NULL,
  `robot_id` bigint NOT NULL, `mission_id` bigint NOT NULL, `message_id` varchar(64) NOT NULL,
  `request_id` varchar(128) NOT NULL, `topic` varchar(512) NOT NULL, `envelope` json NOT NULL,
  `message_type` varchar(64) NOT NULL, `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `attempt_count` int NOT NULL DEFAULT 0, `next_attempt_time` datetime(3) NOT NULL,
  `claimed_at` datetime(3) DEFAULT NULL, `last_error` varchar(512) DEFAULT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` varchar(64) DEFAULT '', `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` bit(1) NOT NULL DEFAULT b'0', PRIMARY KEY (`id`),
  UNIQUE KEY `uk_robot_command_outbox_message` (`message_id`),
  KEY `idx_robot_command_outbox_due` (`status`,`next_attempt_time`,`claimed_at`),
  KEY `idx_robot_command_outbox_mission` (`tenant_id`,`mission_id`,`status`),
  CONSTRAINT `chk_robot_command_outbox_status` CHECK (`status` IN ('PENDING','SENDING','PUBLISHING','CANCEL_COMPENSATING','RETRY','SENT','FAILED')),
  CONSTRAINT `chk_robot_command_outbox_attempt` CHECK (`attempt_count` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='机器人命令可靠发件箱';

CREATE TABLE IF NOT EXISTS `robot_capability` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '所属租户',
  `robot_id` bigint NOT NULL COMMENT '机器人编号',
  `capability_code` varchar(64) NOT NULL COMMENT '能力标识',
  `configuration` varchar(4000) NOT NULL COMMENT '能力配置 JSON',
  `creator` varchar(64) DEFAULT '', `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` varchar(64) DEFAULT '', `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_robot_capability_tenant_robot_code` (`tenant_id`, `robot_id`, `capability_code`),
  KEY `idx_robot_capability_tenant_robot` (`tenant_id`, `robot_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='机器人能力';

-- Task 10: Mission facts remain tenant-scoped and soft-deletable. active_robot_id materializes
-- MySQL's partial-unique-index equivalent: PENDING rows form a queue, while only one dispatched/
-- running/paused slot may exist for a robot. Conditional version writes are the second race fence.
CREATE TABLE IF NOT EXISTS `robot_mission` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL, `mission_no` varchar(64) NOT NULL,
  `robot_id` bigint NOT NULL, `mission_type` varchar(64) NOT NULL, `source` varchar(32) NOT NULL,
  `status` varchar(32) NOT NULL, `priority` int NOT NULL DEFAULT 0, `request_id` varchar(128) NOT NULL,
  `creator_id` bigint DEFAULT NULL, `scheduled_time` datetime(3) DEFAULT NULL, `started_time` datetime(3) DEFAULT NULL,
  `finished_time` datetime(3) DEFAULT NULL, `cancel_requested_time` datetime(3) DEFAULT NULL,
  `payload` json NOT NULL, `error_code` varchar(128) DEFAULT NULL, `error_message` varchar(512) DEFAULT NULL,
  `version` int NOT NULL DEFAULT 0,
  `active_robot_id` bigint GENERATED ALWAYS AS (CASE WHEN `deleted` = b'0' AND `status` IN ('DISPATCHED','RUNNING','PAUSED') THEN `robot_id` ELSE NULL END) STORED,
  `creator` varchar(64) DEFAULT '', `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` varchar(64) DEFAULT '', `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` bit(1) NOT NULL DEFAULT b'0', PRIMARY KEY (`id`),
  UNIQUE KEY `uk_robot_mission_tenant_no` (`tenant_id`,`mission_no`),
  UNIQUE KEY `uk_robot_mission_tenant_request` (`tenant_id`,`request_id`),
  UNIQUE KEY `uk_robot_mission_active_slot` (`tenant_id`,`active_robot_id`),
  KEY `idx_robot_mission_dispatch` (`tenant_id`,`robot_id`,`status`,`priority`,`create_time`),
  KEY `idx_robot_mission_pending_timeout` (`status`,`create_time`),
  CONSTRAINT `chk_robot_mission_status` CHECK (`status` IN ('CREATED','PENDING','DISPATCHED','RUNNING','PAUSED','SUCCESS','FAILED','CANCELLED')),
  CONSTRAINT `chk_robot_mission_source` CHECK (`source` IN ('ADMIN','APP','VOICE','AGENT','SCHEDULE','INSPECTION','SYSTEM')),
  CONSTRAINT `chk_robot_mission_priority` CHECK (`priority` >= 0 AND `priority` <= 100),
  CONSTRAINT `chk_robot_mission_version` CHECK (`version` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='机器人任务';

CREATE TABLE IF NOT EXISTS `robot_mission_action` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL, `mission_id` bigint NOT NULL,
  `sequence_no` int NOT NULL, `action_type` varchar(32) NOT NULL, `parameters` json NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'PENDING', `started_time` datetime(3) DEFAULT NULL, `finished_time` datetime(3) DEFAULT NULL,
  `error_code` varchar(128) DEFAULT NULL, `error_message` varchar(512) DEFAULT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` varchar(64) DEFAULT '', `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` bit(1) NOT NULL DEFAULT b'0', PRIMARY KEY (`id`),
  UNIQUE KEY `uk_robot_mission_action_sequence` (`tenant_id`,`mission_id`,`sequence_no`),
  KEY `idx_robot_mission_action_mission` (`tenant_id`,`mission_id`,`status`),
  CONSTRAINT `chk_robot_mission_action_type` CHECK (`action_type` IN ('NAVIGATE','SPEAK','PLAY_MEDIA','CAPTURE_IMAGE','INSPECT','FIND_PERSON','FIND_OBJECT','RETURN_HOME','WAIT','CUSTOM')),
  CONSTRAINT `chk_robot_mission_action_status` CHECK (`status` IN ('PENDING','RUNNING','SUCCESS','FAILED','SKIPPED')),
  CONSTRAINT `chk_robot_mission_action_sequence_no` CHECK (`sequence_no` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='机器人任务动作';

CREATE TABLE IF NOT EXISTS `robot_mission_execution` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL, `mission_id` bigint NOT NULL,
  `attempt_no` int NOT NULL, `command_message_id` varchar(64) DEFAULT NULL, `command_type` varchar(64) DEFAULT NULL, `acknowledged_time` datetime(3) DEFAULT NULL,
  `started_time` datetime(3) DEFAULT NULL, `finished_time` datetime(3) DEFAULT NULL, `result` json DEFAULT NULL,
  `error_code` varchar(128) DEFAULT NULL, `error_message` varchar(512) DEFAULT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` varchar(64) DEFAULT '', `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` bit(1) NOT NULL DEFAULT b'0', PRIMARY KEY (`id`),
  UNIQUE KEY `uk_robot_mission_execution_command` (`tenant_id`,`command_message_id`),
  KEY `idx_robot_mission_execution_mission` (`tenant_id`,`mission_id`,`create_time`),
  CONSTRAINT `chk_robot_mission_execution_attempt` CHECK (`attempt_no` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='机器人任务执行尝试';

CREATE TABLE IF NOT EXISTS `robot_mission_event` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL, `mission_id` bigint NOT NULL,
  `action_id` bigint DEFAULT NULL, `event_type` varchar(64) NOT NULL, `from_status` varchar(32) DEFAULT NULL,
  `to_status` varchar(32) DEFAULT NULL, `message_id` varchar(64) DEFAULT NULL, `request_id` varchar(128) NOT NULL,
  `payload` json DEFAULT NULL, `occurred_time` datetime(3) NOT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` varchar(64) DEFAULT '', `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` bit(1) NOT NULL DEFAULT b'0', PRIMARY KEY (`id`),
  KEY `idx_robot_mission_event_timeline` (`tenant_id`,`mission_id`,`occurred_time`,`id`),
  KEY `idx_robot_mission_event_message` (`tenant_id`,`message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='机器人任务事件时间线';

-- Task 6: member credentials are strong one-way hashes; all APP access is granted explicitly.
CREATE TABLE IF NOT EXISTS `member` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL, `mobile` varchar(32) NOT NULL,
  `password` varchar(255) NOT NULL COMMENT 'BCrypt password hash only', `nickname` varchar(128) DEFAULT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'ENABLED',
  `creator` varchar(64) DEFAULT '', `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` varchar(64) DEFAULT '', `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` bit(1) NOT NULL DEFAULT b'0', PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member_tenant_mobile` (`tenant_id`, `mobile`), KEY `idx_member_tenant_status` (`tenant_id`, `status`),
  CONSTRAINT `chk_member_status` CHECK (`status` IN ('ENABLED', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户会员账户';

CREATE TABLE IF NOT EXISTS `member_robot_binding` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL, `member_id` bigint NOT NULL, `robot_id` bigint NOT NULL,
  `role` varchar(16) NOT NULL DEFAULT 'READ', `status` varchar(16) NOT NULL DEFAULT 'ENABLED',
  `creator` varchar(64) DEFAULT '', `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` varchar(64) DEFAULT '', `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` bit(1) NOT NULL DEFAULT b'0', PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member_robot_binding` (`tenant_id`, `member_id`, `robot_id`),
  KEY `idx_member_robot_binding_lookup` (`tenant_id`, `member_id`, `robot_id`, `status`),
  CONSTRAINT `chk_member_robot_role` CHECK (`role` IN ('READ', 'OWNER', 'OPERATOR')),
  CONSTRAINT `chk_member_robot_status` CHECK (`status` IN ('ENABLED', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员机器人授权';

-- Task 13: Robot-platform administration menus. Fixed identifiers make this block
-- safely re-runnable after the framework's system_menu table has been initialized.
-- Buttons are separate menu records because permissions are enforced by both Spring
-- Security and v-hasPermi; a hidden button can never be a substitute for the API check.
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 910000, '机器人平台', '', 1, 90, 0, '/robot-platform', 'ep:cpu', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 910000);
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 910010, '设备管理', '', 1, 1, 910000, 'device', 'ep:box', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 910010);
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 910011, '产品型号', 'device:product:query', 2, 1, 910010, 'product', 'ep:goods', 'device/product/index', 'RobotDeviceProduct', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 910011);
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 910012, '设备', 'device:device:query', 2, 2, 910010, 'device', 'ep:connection', 'device/device/index', 'RobotDevice', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 910012);
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 910013, '设备组', 'device:group:query', 2, 3, 910010, 'group', 'ep:collection', 'device/group/index', 'RobotDeviceGroup', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 910013);
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 910020, '机器人', 'robot:robot:query', 2, 2, 910000, 'robot', 'ep:cpu', 'robot/robot/index', 'RobotList', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 910020);
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 910030, '任务中心', '', 1, 3, 910000, 'mission', 'ep:list', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 910030);
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 910031, '实时任务', 'robot:mission:query', 2, 1, 910030, 'realtime', 'ep:video-play', 'robot/mission/index', 'RobotMission', 0, b'1', b'0', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 910031);
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 910032, '历史任务', 'robot:mission:query', 2, 2, 910030, 'history', 'ep:clock', 'robot/mission/index', 'RobotMissionHistory', 0, b'1', b'0', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 910032);
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 910033, '任务详情', 'robot:mission:query', 2, 3, 910030, 'detail', 'ep:document', 'robot/mission/index', 'RobotMissionDetailRoute', 0, b'0', b'0', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 910033);
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 910040, '租户配额', 'tenant:quota:query', 2, 4, 910000, 'quota', 'ep:histogram', 'tenant/quota/index', 'RobotTenantQuota', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 910040);

-- Product permissions
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 910101, '产品新增', 'device:product:create', 3, 1, 910011, '', '', '', NULL, 0, b'1', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0' WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 910101);
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 910102, '产品修改', 'device:product:update', 3, 2, 910011, '', '', '', NULL, 0, b'1', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0' WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 910102);
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 910103, '产品删除', 'device:product:delete', 3, 3, 910011, '', '', '', NULL, 0, b'1', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0' WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 910103);
-- Device, group, robot, mission and quota actions (including every design §11.2 permission).
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT m.id, m.name, m.permission, 3, m.sort, m.parent_id, '', '', '', NULL, 0, b'1', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'
FROM (
  SELECT 910111 id, '设备入库' name, 'device:device:create' permission, 1 sort, 910012 parent_id UNION ALL
  SELECT 910112, '设备激活', 'device:device:activate', 2, 910012 UNION ALL
  SELECT 910113, '设备解绑', 'device:device:unbind', 3, 910012 UNION ALL
  SELECT 910114, '设备修改', 'device:device:update', 4, 910012 UNION ALL
  SELECT 910115, '设备轮换凭据', 'device:device:rotate', 5, 910012 UNION ALL
  SELECT 910121, '设备组查询', 'device:group:query', 1, 910013 UNION ALL
  SELECT 910122, '设备组新增', 'device:group:create', 2, 910013 UNION ALL
  SELECT 910123, '设备组修改', 'device:group:update', 3, 910013 UNION ALL
  SELECT 910124, '设备组删除', 'device:group:delete', 4, 910013 UNION ALL
  SELECT 910131, '机器人修改', 'robot:robot:update', 1, 910020 UNION ALL
  SELECT 910141, '任务创建', 'robot:mission:create', 1, 910031 UNION ALL
  SELECT 910142, '任务取消', 'robot:mission:cancel', 2, 910031 UNION ALL
  SELECT 910151, '配额修改', 'tenant:quota:update', 1, 910040
) m
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` existing WHERE existing.id = m.id);


-- Realtime Agent Core Task 2: tenant-scoped provider/model/prompt/agent persistence.
CREATE TABLE IF NOT EXISTS `ai_model_provider` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户编号',
  `name` varchar(128) NOT NULL COMMENT 'Provider 名称',
  `code` varchar(64) NOT NULL COMMENT 'Provider 编码',
  `provider_type` varchar(32) NOT NULL COMMENT 'QWEN/DEEPSEEK/DOUBAO',
  `base_url` varchar(512) NOT NULL COMMENT 'Provider 基础地址',
  `api_key_ciphertext` varchar(2048) DEFAULT NULL COMMENT 'Provider API Key 密文',
  `config_json` json DEFAULT NULL COMMENT 'Provider 高级配置',
  `status` varchar(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态',
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_provider_tenant_code` (`tenant_id`,`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 模型 Provider';

CREATE TABLE IF NOT EXISTS `ai_model` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户编号',
  `provider_id` bigint NOT NULL COMMENT 'Provider 编号',
  `name` varchar(128) NOT NULL COMMENT '模型名称',
  `model_code` varchar(128) NOT NULL COMMENT '供应商模型编码',
  `model_type` varchar(32) NOT NULL COMMENT 'CHAT/REALTIME_S2S/ASR/TTS/EMBEDDING',
  `capabilities_json` json DEFAULT NULL COMMENT '模型能力',
  `config_json` json DEFAULT NULL COMMENT '模型高级配置',
  `status` varchar(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态',
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_model_tenant_provider_code` (`tenant_id`,`provider_id`,`model_code`),
  KEY `idx_ai_model_tenant_type` (`tenant_id`,`model_type`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 模型';

CREATE TABLE IF NOT EXISTS `ai_prompt` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户编号',
  `name` varchar(128) NOT NULL COMMENT 'Prompt 名称',
  `code` varchar(64) NOT NULL COMMENT 'Prompt 编码',
  `type` varchar(32) NOT NULL COMMENT 'SYSTEM/MEMORY_EXTRACT/MEMORY_SUMMARY/TOOL_ROUTING',
  `content` longtext NOT NULL COMMENT 'Prompt 内容',
  `version` int NOT NULL COMMENT '版本号',
  `status` varchar(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态',
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_prompt_tenant_code_version` (`tenant_id`,`code`,`version`),
  KEY `idx_ai_prompt_tenant_code_status` (`tenant_id`,`code`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI Prompt';

CREATE TABLE IF NOT EXISTS `ai_agent` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户编号',
  `name` varchar(128) NOT NULL COMMENT '智能体名称',
  `code` varchar(64) NOT NULL COMMENT '智能体编码',
  `description` varchar(1000) DEFAULT NULL COMMENT '描述',
  `system_prompt_id` bigint NOT NULL COMMENT 'System Prompt 编号',
  `conversation_model_id` bigint DEFAULT NULL COMMENT '对话模型编号',
  `realtime_model_id` bigint DEFAULT NULL COMMENT 'Realtime S2S 模型编号',
  `asr_model_id` bigint DEFAULT NULL COMMENT 'ASR 模型编号',
  `tts_model_id` bigint DEFAULT NULL COMMENT 'TTS 模型编号',
  `realtime_mode` varchar(16) NOT NULL COMMENT 'NATIVE/CASCADE/AUTO',
  `memory_mode` varchar(16) NOT NULL DEFAULT 'NONE' COMMENT 'NONE/SESSION/LONG_TERM',
  `memory_read_enabled` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否读取长期记忆',
  `memory_write_enabled` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否写入长期记忆',
  `knowledge_enabled` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否启用知识能力',
  `voice_config_json` json DEFAULT NULL COMMENT '音色及语音配置',
  `status` varchar(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态',
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_agent_tenant_code` (`tenant_id`,`code`),
  KEY `idx_ai_agent_tenant_status` (`tenant_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 智能体';

CREATE TABLE IF NOT EXISTS `ai_agent_robot` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户编号',
  `agent_id` bigint NOT NULL COMMENT '智能体编号',
  `robot_id` bigint NOT NULL COMMENT '机器人编号',
  `is_default` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否默认智能体',
  `status` varchar(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态',
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_agent_robot` (`tenant_id`,`agent_id`,`robot_id`),
  KEY `idx_ai_agent_robot_default` (`tenant_id`,`robot_id`,`is_default`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 智能体机器人绑定';
