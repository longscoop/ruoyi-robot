-- RuoYi Robot AI schema, executed after robot-platform.sql on fresh environments.
-- AI business data is tenant-scoped; provider secrets are stored only as ciphertext.
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `ai_model_provider` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户编号',
  `name` varchar(128) NOT NULL COMMENT '供应商名称',
  `code` varchar(64) NOT NULL COMMENT '租户内供应商编码',
  `provider_type` varchar(32) NOT NULL COMMENT 'QWEN/DEEPSEEK/DOUBAO',
  `base_url` varchar(512) NOT NULL COMMENT '供应商 API 地址',
  `api_key_ciphertext` varchar(2048) DEFAULT NULL COMMENT 'API Key AES-GCM 密文',
  `config_json` json DEFAULT NULL COMMENT '非敏感供应商配置',
  `status` varchar(16) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_provider_tenant_code` (`tenant_id`,`code`),
  KEY `idx_ai_provider_tenant_type` (`tenant_id`,`provider_type`,`status`),
  CONSTRAINT `chk_ai_provider_type` CHECK (`provider_type` IN ('QWEN','DEEPSEEK','DOUBAO')),
  CONSTRAINT `chk_ai_provider_status` CHECK (`status` IN ('ENABLED','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 模型供应商';

CREATE TABLE IF NOT EXISTS `ai_model` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户编号',
  `provider_id` bigint NOT NULL COMMENT '供应商编号',
  `name` varchar(128) NOT NULL COMMENT '模型名称',
  `model_code` varchar(128) NOT NULL COMMENT '供应商模型编码',
  `model_type` varchar(32) NOT NULL COMMENT 'CHAT/REALTIME_S2S/ASR/TTS/EMBEDDING',
  `capabilities_json` json DEFAULT NULL COMMENT '模型能力声明',
  `config_json` json DEFAULT NULL COMMENT '模型非敏感配置',
  `status` varchar(16) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_model_tenant_provider_code` (`tenant_id`,`provider_id`,`model_code`),
  KEY `idx_ai_model_tenant_type` (`tenant_id`,`model_type`,`status`),
  CONSTRAINT `chk_ai_model_type` CHECK (`model_type` IN ('CHAT','REALTIME_S2S','ASR','TTS','EMBEDDING')),
  CONSTRAINT `chk_ai_model_status` CHECK (`status` IN ('ENABLED','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 模型';

CREATE TABLE IF NOT EXISTS `ai_prompt` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户编号',
  `name` varchar(128) NOT NULL COMMENT 'Prompt 名称',
  `code` varchar(64) NOT NULL COMMENT 'Prompt 编码',
  `type` varchar(32) NOT NULL COMMENT 'SYSTEM/MEMORY_EXTRACT/MEMORY_SUMMARY/TOOL_ROUTING',
  `content` text NOT NULL COMMENT 'Prompt 内容',
  `version` int NOT NULL COMMENT '版本号，从 1 开始',
  `status` varchar(16) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_prompt_tenant_code_version` (`tenant_id`,`code`,`version`),
  KEY `idx_ai_prompt_tenant_type` (`tenant_id`,`type`,`status`),
  CONSTRAINT `chk_ai_prompt_type` CHECK (`type` IN ('SYSTEM','MEMORY_EXTRACT','MEMORY_SUMMARY','TOOL_ROUTING')),
  CONSTRAINT `chk_ai_prompt_version` CHECK (`version` > 0),
  CONSTRAINT `chk_ai_prompt_status` CHECK (`status` IN ('ENABLED','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI Prompt 版本';

CREATE TABLE IF NOT EXISTS `ai_agent` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户编号',
  `name` varchar(128) NOT NULL COMMENT '智能体名称',
  `code` varchar(64) NOT NULL COMMENT '租户内智能体编码',
  `description` varchar(1000) DEFAULT NULL COMMENT '智能体说明',
  `system_prompt_id` bigint NOT NULL COMMENT '系统 Prompt 版本编号',
  `conversation_model_id` bigint DEFAULT NULL COMMENT 'CHAT 模型',
  `realtime_model_id` bigint DEFAULT NULL COMMENT 'REALTIME_S2S 模型',
  `asr_model_id` bigint DEFAULT NULL COMMENT 'ASR 模型',
  `tts_model_id` bigint DEFAULT NULL COMMENT 'TTS 模型',
  `realtime_mode` varchar(16) NOT NULL COMMENT 'NATIVE/CASCADE/AUTO',
  `memory_mode` varchar(16) NOT NULL DEFAULT 'SESSION' COMMENT 'NONE/SESSION/LONG_TERM',
  `memory_read_enabled` bit(1) NOT NULL DEFAULT b'1',
  `memory_write_enabled` bit(1) NOT NULL DEFAULT b'1',
  `knowledge_enabled` bit(1) NOT NULL DEFAULT b'0',
  `voice_config_json` json DEFAULT NULL COMMENT '语音参数',
  `status` varchar(16) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_agent_tenant_code` (`tenant_id`,`code`),
  KEY `idx_ai_agent_tenant_status` (`tenant_id`,`status`),
  CONSTRAINT `chk_ai_agent_realtime_mode` CHECK (`realtime_mode` IN ('NATIVE','CASCADE','AUTO')),
  CONSTRAINT `chk_ai_agent_memory_mode` CHECK (`memory_mode` IN ('NONE','SESSION','LONG_TERM')),
  CONSTRAINT `chk_ai_agent_status` CHECK (`status` IN ('ENABLED','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 智能体';

CREATE TABLE IF NOT EXISTS `ai_agent_robot` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '租户编号',
  `agent_id` bigint NOT NULL COMMENT '智能体编号',
  `robot_id` bigint NOT NULL COMMENT '机器人编号',
  `is_default` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否默认智能体',
  `status` varchar(16) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_agent_robot` (`tenant_id`,`agent_id`,`robot_id`),
  KEY `idx_ai_agent_robot_default` (`tenant_id`,`robot_id`,`is_default`,`status`),
  CONSTRAINT `chk_ai_agent_robot_status` CHECK (`status` IN ('ENABLED','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='智能体机器人绑定';
