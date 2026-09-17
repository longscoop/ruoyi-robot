from pathlib import Path
import sys

SQL_MARKER = "-- Realtime Agent Core Task 2: provider/model/prompt/agent persistence."
SQL = r'''-- Realtime Agent Core Task 2: provider/model/prompt/agent persistence.
CREATE TABLE IF NOT EXISTS `ai_model_provider` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '所属租户',
  `name` varchar(128) NOT NULL COMMENT '供应商名称',
  `code` varchar(64) NOT NULL COMMENT '租户内供应商编码',
  `provider_type` varchar(32) NOT NULL COMMENT 'QWEN/DEEPSEEK/DOUBAO',
  `base_url` varchar(512) DEFAULT NULL COMMENT '供应商 API 地址',
  `api_key_ciphertext` varchar(2048) DEFAULT NULL COMMENT 'API Key 加密密文',
  `config_json` json DEFAULT NULL COMMENT '供应商扩展配置',
  `status` varchar(16) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
  `creator` varchar(64) DEFAULT '', `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` varchar(64) DEFAULT '', `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_provider_tenant_code` (`tenant_id`,`code`),
  KEY `idx_ai_provider_tenant_status` (`tenant_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 模型供应商';

CREATE TABLE IF NOT EXISTS `ai_model` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '所属租户',
  `provider_id` bigint NOT NULL COMMENT '模型供应商',
  `name` varchar(128) NOT NULL COMMENT '模型名称',
  `model_code` varchar(128) NOT NULL COMMENT '供应商模型编码',
  `model_type` varchar(32) NOT NULL COMMENT 'CHAT/REALTIME_S2S/ASR/TTS/EMBEDDING',
  `capabilities_json` json DEFAULT NULL COMMENT '模型能力声明',
  `config_json` json DEFAULT NULL COMMENT '模型扩展配置',
  `status` varchar(16) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
  `creator` varchar(64) DEFAULT '', `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` varchar(64) DEFAULT '', `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_model_tenant_provider_code` (`tenant_id`,`provider_id`,`model_code`),
  KEY `idx_ai_model_tenant_type_status` (`tenant_id`,`model_type`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 模型';

CREATE TABLE IF NOT EXISTS `ai_prompt` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '所属租户',
  `name` varchar(128) NOT NULL COMMENT 'Prompt 名称',
  `code` varchar(64) NOT NULL COMMENT 'Prompt 编码',
  `version` int NOT NULL COMMENT 'Prompt 版本',
  `content` text NOT NULL COMMENT 'Prompt 内容',
  `status` varchar(16) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
  `creator` varchar(64) DEFAULT '', `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` varchar(64) DEFAULT '', `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_prompt_tenant_code_version` (`tenant_id`,`code`,`version`),
  KEY `idx_ai_prompt_tenant_code_status` (`tenant_id`,`code`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI Prompt';

CREATE TABLE IF NOT EXISTS `ai_agent` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '所属租户',
  `name` varchar(128) NOT NULL COMMENT '智能体名称',
  `code` varchar(64) NOT NULL COMMENT '智能体编码',
  `description` varchar(1000) DEFAULT NULL COMMENT '智能体描述',
  `system_prompt_id` bigint NOT NULL COMMENT '系统 Prompt',
  `conversation_model_id` bigint DEFAULT NULL COMMENT '文本/级联聊天模型',
  `realtime_model_id` bigint DEFAULT NULL COMMENT '原生实时语音模型',
  `asr_model_id` bigint DEFAULT NULL COMMENT '级联 ASR 模型',
  `tts_model_id` bigint DEFAULT NULL COMMENT '级联 TTS 模型',
  `realtime_mode` varchar(16) NOT NULL DEFAULT 'AUTO' COMMENT 'NATIVE/CASCADE/AUTO',
  `memory_mode` varchar(16) NOT NULL DEFAULT 'NONE' COMMENT 'NONE/SESSION/LONG_TERM',
  `memory_read_enabled` tinyint NOT NULL DEFAULT 0 COMMENT '是否读取记忆',
  `memory_write_enabled` tinyint NOT NULL DEFAULT 0 COMMENT '是否写入记忆',
  `knowledge_enabled` tinyint NOT NULL DEFAULT 0 COMMENT '是否启用知识库',
  `voice_config_json` json DEFAULT NULL COMMENT '语音配置',
  `status` varchar(16) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
  `creator` varchar(64) DEFAULT '', `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` varchar(64) DEFAULT '', `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_agent_tenant_code` (`tenant_id`,`code`),
  KEY `idx_ai_agent_tenant_status` (`tenant_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 智能体';

CREATE TABLE IF NOT EXISTS `ai_agent_robot` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` bigint NOT NULL COMMENT '所属租户',
  `agent_id` bigint NOT NULL COMMENT '智能体',
  `robot_id` bigint NOT NULL COMMENT '机器人',
  `is_default` tinyint NOT NULL DEFAULT 0 COMMENT '是否默认智能体',
  `status` varchar(16) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
  `creator` varchar(64) DEFAULT '', `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updater` varchar(64) DEFAULT '', `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_agent_robot` (`tenant_id`,`agent_id`,`robot_id`),
  KEY `idx_ai_agent_robot_default` (`tenant_id`,`robot_id`,`is_default`,`status`),
  KEY `idx_ai_agent_robot_agent` (`tenant_id`,`agent_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 智能体与机器人绑定';
'''

ROOT = Path('robot-platform-module-ai/src/main/java/com/robot/platform/ai')

DOS = {
    ROOT / 'model/dal/dataobject/AiModelProviderDO.java': ('com.robot.platform.ai.model.dal.dataobject', 'ai_model_provider', 'AiModelProviderDO', [('Long','id'),('Long','tenantId'),('String','name'),('String','code'),('String','providerType'),('String','baseUrl'),('String','apiKeyCiphertext'),('String','configJson'),('String','status')]),
    ROOT / 'model/dal/dataobject/AiModelDO.java': ('com.robot.platform.ai.model.dal.dataobject', 'ai_model', 'AiModelDO', [('Long','id'),('Long','tenantId'),('Long','providerId'),('String','name'),('String','modelCode'),('String','modelType'),('String','capabilitiesJson'),('String','configJson'),('String','status')]),
    ROOT / 'prompt/dal/dataobject/AiPromptDO.java': ('com.robot.platform.ai.prompt.dal.dataobject', 'ai_prompt', 'AiPromptDO', [('Long','id'),('Long','tenantId'),('String','name'),('String','code'),('Integer','version'),('String','content'),('String','status')]),
    ROOT / 'agent/dal/dataobject/AiAgentDO.java': ('com.robot.platform.ai.agent.dal.dataobject', 'ai_agent', 'AiAgentDO', [('Long','id'),('Long','tenantId'),('String','name'),('String','code'),('String','description'),('Long','systemPromptId'),('Long','conversationModelId'),('Long','realtimeModelId'),('Long','asrModelId'),('Long','ttsModelId'),('String','realtimeMode'),('String','memoryMode'),('Boolean','memoryReadEnabled'),('Boolean','memoryWriteEnabled'),('Boolean','knowledgeEnabled'),('String','voiceConfigJson'),('String','status')]),
    ROOT / 'agent/dal/dataobject/AiAgentRobotDO.java': ('com.robot.platform.ai.agent.dal.dataobject', 'ai_agent_robot', 'AiAgentRobotDO', [('Long','id'),('Long','tenantId'),('Long','agentId'),('Long','robotId'),('Boolean','isDefault'),('String','status')]),
}

MAPPERS = {
    ROOT / 'model/dal/mysql/AiModelProviderMapper.java': '''package com.robot.platform.ai.model.dal.mysql;

import com.robot.platform.ai.model.dal.dataobject.AiModelProviderDO;
import com.robot.platform.framework.mybatis.core.mapper.BaseMapperX;
import com.robot.platform.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiModelProviderMapper extends BaseMapperX<AiModelProviderDO> {
    default AiModelProviderDO selectByIdAndTenantId(long id, long tenantId) {
        return selectOne(new LambdaQueryWrapperX<AiModelProviderDO>().eq(AiModelProviderDO::getId, id).eq(AiModelProviderDO::getTenantId, tenantId));
    }
    default AiModelProviderDO selectByCodeAndTenantId(String code, long tenantId) {
        return selectOne(new LambdaQueryWrapperX<AiModelProviderDO>().eq(AiModelProviderDO::getCode, code).eq(AiModelProviderDO::getTenantId, tenantId));
    }
}
''',
    ROOT / 'model/dal/mysql/AiModelMapper.java': '''package com.robot.platform.ai.model.dal.mysql;

import com.robot.platform.ai.model.dal.dataobject.AiModelDO;
import com.robot.platform.framework.mybatis.core.mapper.BaseMapperX;
import com.robot.platform.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiModelMapper extends BaseMapperX<AiModelDO> {
    default AiModelDO selectByIdAndTenantId(long id, long tenantId) {
        return selectOne(new LambdaQueryWrapperX<AiModelDO>().eq(AiModelDO::getId, id).eq(AiModelDO::getTenantId, tenantId));
    }
    default AiModelDO selectByProviderAndCodeAndTenantId(long tenantId, long providerId, String modelCode) {
        return selectOne(new LambdaQueryWrapperX<AiModelDO>().eq(AiModelDO::getTenantId, tenantId).eq(AiModelDO::getProviderId, providerId).eq(AiModelDO::getModelCode, modelCode));
    }
}
''',
    ROOT / 'prompt/dal/mysql/AiPromptMapper.java': '''package com.robot.platform.ai.prompt.dal.mysql;

import com.robot.platform.ai.prompt.dal.dataobject.AiPromptDO;
import com.robot.platform.framework.mybatis.core.mapper.BaseMapperX;
import com.robot.platform.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiPromptMapper extends BaseMapperX<AiPromptDO> {
    default AiPromptDO selectByIdAndTenantId(long id, long tenantId) {
        return selectOne(new LambdaQueryWrapperX<AiPromptDO>().eq(AiPromptDO::getId, id).eq(AiPromptDO::getTenantId, tenantId));
    }
    default AiPromptDO selectByCodeAndVersionAndTenantId(String code, Integer version, long tenantId) {
        return selectOne(new LambdaQueryWrapperX<AiPromptDO>().eq(AiPromptDO::getCode, code).eq(AiPromptDO::getVersion, version).eq(AiPromptDO::getTenantId, tenantId));
    }
}
''',
    ROOT / 'agent/dal/mysql/AiAgentMapper.java': '''package com.robot.platform.ai.agent.dal.mysql;

import com.robot.platform.ai.agent.dal.dataobject.AiAgentDO;
import com.robot.platform.framework.mybatis.core.mapper.BaseMapperX;
import com.robot.platform.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiAgentMapper extends BaseMapperX<AiAgentDO> {
    default AiAgentDO selectByIdAndTenantId(long id, long tenantId) {
        return selectOne(new LambdaQueryWrapperX<AiAgentDO>().eq(AiAgentDO::getId, id).eq(AiAgentDO::getTenantId, tenantId));
    }
    default AiAgentDO selectByCodeAndTenantId(String code, long tenantId) {
        return selectOne(new LambdaQueryWrapperX<AiAgentDO>().eq(AiAgentDO::getCode, code).eq(AiAgentDO::getTenantId, tenantId));
    }
}
''',
    ROOT / 'agent/dal/mysql/AiAgentRobotMapper.java': '''package com.robot.platform.ai.agent.dal.mysql;

import com.robot.platform.ai.agent.dal.dataobject.AiAgentRobotDO;
import com.robot.platform.framework.mybatis.core.mapper.BaseMapperX;
import com.robot.platform.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface AiAgentRobotMapper extends BaseMapperX<AiAgentRobotDO> {
    default AiAgentRobotDO selectByIdAndTenantId(long id, long tenantId) {
        return selectOne(new LambdaQueryWrapperX<AiAgentRobotDO>().eq(AiAgentRobotDO::getId, id).eq(AiAgentRobotDO::getTenantId, tenantId));
    }
    default List<AiAgentRobotDO> selectByRobot(long tenantId, long robotId) {
        return selectList(new LambdaQueryWrapperX<AiAgentRobotDO>().eq(AiAgentRobotDO::getTenantId, tenantId).eq(AiAgentRobotDO::getRobotId, robotId));
    }
    default List<AiAgentRobotDO> selectByAgent(long tenantId, long agentId) {
        return selectList(new LambdaQueryWrapperX<AiAgentRobotDO>().eq(AiAgentRobotDO::getTenantId, tenantId).eq(AiAgentRobotDO::getAgentId, agentId));
    }
}
''',
}

def render_do(package, table, clazz, fields):
    lines = [f'package {package};', '', 'import com.baomidou.mybatisplus.annotation.TableId;', 'import com.baomidou.mybatisplus.annotation.TableName;', 'import com.robot.platform.framework.mybatis.core.dataobject.BaseDO;', 'import lombok.AllArgsConstructor;', 'import lombok.Builder;', 'import lombok.Data;', 'import lombok.EqualsAndHashCode;', 'import lombok.NoArgsConstructor;', '', f'@TableName("{table}")', '@Data', '@EqualsAndHashCode(callSuper = true)', '@Builder', '@NoArgsConstructor', '@AllArgsConstructor', f'public class {clazz} extends BaseDO {{']
    for index, (java_type, name) in enumerate(fields):
        if index == 0:
            lines.append('    @TableId')
        lines.append(f'    private {java_type} {name};')
    lines.extend(['}', ''])
    return '\n'.join(lines)

def apply():
    sql_path = Path('sql/mysql/robot-platform.sql')
    current = sql_path.read_text(encoding='utf-8')
    if SQL_MARKER in current:
        raise SystemExit('Task 2 schema marker already exists')
    sql_path.write_text(current.rstrip() + '\n\n' + SQL, encoding='utf-8')
    for path, spec in DOS.items():
        if path.exists():
            raise SystemExit(f'{path} already exists')
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(render_do(*spec), encoding='utf-8')
    for path, content in MAPPERS.items():
        if path.exists():
            raise SystemExit(f'{path} already exists')
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding='utf-8')

def check():
    sql = Path('sql/mysql/robot-platform.sql').read_text(encoding='utf-8')
    for table in ('ai_model_provider', 'ai_model', 'ai_prompt', 'ai_agent', 'ai_agent_robot'):
        assert f'CREATE TABLE IF NOT EXISTS `{table}`' in sql, table
    for key in ('uk_ai_provider_tenant_code', 'uk_ai_model_tenant_provider_code', 'uk_ai_prompt_tenant_code_version', 'uk_ai_agent_tenant_code', 'uk_ai_agent_robot', 'idx_ai_agent_robot_default'):
        assert f'`{key}`' in sql, key
    assert '`api_key_ciphertext`' in sql
    assert '`api_key` varchar' not in sql
    for path in (*DOS.keys(), *MAPPERS.keys()):
        assert path.exists(), path
        text = path.read_text(encoding='utf-8')
        assert 'tenantId' in text or 'TenantId' in text, path
    provider = next(path for path in DOS if path.name == 'AiModelProviderDO.java').read_text(encoding='utf-8')
    assert 'apiKeyCiphertext' in provider
    assert 'apiKey;' not in provider

if __name__ == '__main__':
    check() if '--check' in sys.argv else apply()
