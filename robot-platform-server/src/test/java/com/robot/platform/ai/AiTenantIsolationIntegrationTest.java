package com.robot.platform.ai;
import com.robot.platform.integration.AbstractRobotPlatformIntegrationTest;
import com.robot.platform.ai.agent.dal.dataobject.AiAgentDO;import com.robot.platform.ai.agent.dal.mysql.AiAgentMapper;
import com.robot.platform.ai.model.dal.dataobject.AiModelDO;import com.robot.platform.ai.model.dal.mysql.AiModelMapper;
import com.robot.platform.ai.memory.dal.dataobject.AiMemoryDO;import com.robot.platform.ai.memory.dal.mysql.AiMemoryMapper;
import com.robot.platform.ai.conversation.dal.dataobject.AiConversationDO;import com.robot.platform.ai.conversation.dal.mysql.AiConversationMapper;
import org.junit.jupiter.api.Test;import org.springframework.beans.factory.annotation.Autowired;import java.math.BigDecimal;import java.time.LocalDateTime;import static org.assertj.core.api.Assertions.assertThat;
class AiTenantIsolationIntegrationTest extends AbstractRobotPlatformIntegrationTest{
 @Autowired AiMemoryMapper memories;@Autowired AiModelMapper models;@Autowired AiAgentMapper agents;@Autowired AiConversationMapper conversations;
 @Test void tenantACannotReadTenantBIds(){
  long b=202L,a=101L;AiModelDO model=new AiModelDO();model.setTenantId(b);model.setProviderId(1L);model.setName("b");model.setModelCode("b-"+System.nanoTime());model.setModelType("CHAT");model.setStatus("ENABLED");models.insert(model);
  AiAgentDO agent=new AiAgentDO();agent.setTenantId(b);agent.setName("b");agent.setCode("b-"+System.nanoTime());agent.setSystemPromptId(1L);agent.setRealtimeMode("NATIVE");agent.setMemoryMode("NONE");agent.setMemoryReadEnabled(false);agent.setMemoryWriteEnabled(false);agent.setKnowledgeEnabled(false);agent.setStatus("ENABLED");agents.insert(agent);
  AiConversationDO conv=new AiConversationDO();conv.setTenantId(b);conv.setAgentId(agent.getId());conv.setRobotId(9L);conv.setChannel("ROBOT_VOICE");conv.setStatus("ACTIVE");conv.setStartedAt(LocalDateTime.now());conv.setCreatedAt(LocalDateTime.now());conv.setUpdatedAt(LocalDateTime.now());conversations.insert(conv);
  AiMemoryDO m=new AiMemoryDO();m.setTenantId(b);m.setScope("ROBOT");m.setRobotId(9L);m.setMemoryType("FACT");m.setContent("tenant-b");m.setImportance(new BigDecimal(".8"));m.setConfidence(new BigDecimal(".9"));m.setFirstObservedAt(LocalDateTime.now());m.setLastObservedAt(LocalDateTime.now());m.setStatus("ACTIVE");m.setCreatedAt(LocalDateTime.now());m.setUpdatedAt(LocalDateTime.now());memories.insert(m);
  assertThat(models.selectByIdAndTenantId(model.getId(),a)).isNull();assertThat(agents.selectByIdAndTenantId(agent.getId(),a)).isNull();assertThat(conversations.selectByIdAndTenantId(conv.getId(),a)).isNull();assertThat(memories.selectByIdAndTenantId(m.getId(),a)).isNull();
  assertThat(models.logicalDeleteByIdAndTenantId(model.getId(),a)).isZero();assertThat(agents.logicalDeleteByIdAndTenantId(agent.getId(),a)).isZero();
  memories.deleteById(m.getId());conversations.deleteById(conv.getId());agents.deleteById(agent.getId());models.deleteById(model.getId());
 }}
