package com.robot.platform.ai.memory.extract;

import com.robot.platform.ai.agent.service.AiAgentConfig;
import com.robot.platform.ai.memory.identity.ConversationIdentity;
import com.robot.platform.ai.memory.policy.MemoryDirectiveParser;
import com.robot.platform.ai.model.client.ModelClientRegistry;
import com.robot.platform.ai.prompt.service.AiPromptService;
import com.robot.platform.ai.realtime.runtime.ResolvedModelResolver;
import com.robot.platform.framework.common.util.json.JsonUtils;
import org.springframework.stereotype.Component;

import java.util.List;

/** Resolves the extraction model per tenant and Agent, never from a global credential. */
@Component
public class AgentMemoryExtractor implements MemoryExtractor {
    private static final String DEFAULT_PROMPT = """
            从 USER 内容提取值得长期保留的明确事实、偏好或明确要求记住的内容。不要从 ASSISTANT 的猜测创建事实。
            不保留问候、测试闲聊、敏感凭据或短暂的无意义内容。不推断说话者身份；身份信息只记录用户明说的资料，并标注自述未核实，不得用于身份认证。
            USER 要求忘记时，仅返回明确指定的待删除内容，不扩展为其他记忆。
            仅输出 JSON，不用 Markdown：{"candidates":[{"scope":"ROBOT","memoryType":"FACT",
            "content":"独立完整的事实","importance":0.8,"confidence":0.9,"expiresAt":null}]}。
            无可保留内容返回 {"candidates":[]}。memoryType 按内容分类：PREFERENCE 用户偏好（口味、兴趣、沟通习惯）；IDENTITY 身份信息（用户自述的姓名、年龄等）；
            CONTEXT 情景（家庭环境、近期安排）；WORK 工作（职业、项目、工作习惯）；RELATION 关系（家庭、宠物）；FACT 其他事实。
            一条只保存一个独立事实，去掉闲聊和重复措辞。临时情景或事件必须给出 ISO 本地到期时间，长期事实可为 null。
            """;
    private final ResolvedModelResolver models;
    private final ModelClientRegistry clients;
    private final AiPromptService prompts;

    public AgentMemoryExtractor(ResolvedModelResolver models, ModelClientRegistry clients, AiPromptService prompts) {
        this.models = models;
        this.clients = clients;
        this.prompts = prompts;
    }

    @Override
    public List<MemoryCandidate> extract(CompletedTurn turn, ConversationIdentity identity,
                                        MemoryDirectiveParser.Directive directive) {
        throw new IllegalArgumentException("Memory extraction requires the resolved Agent configuration");
    }

    @Override
    public List<MemoryCandidate> extract(CompletedTurn turn, ConversationIdentity identity,
                                        MemoryDirectiveParser.Directive directive, AiAgentConfig agent) {
        if (agent.tenantId() != identity.tenantId()) throw new IllegalArgumentException("Memory tenant mismatch");
        if (agent.conversationModelId() == null) throw new IllegalStateException("Long-term memory requires an Agent CHAT model");
        var model = models.resolve(identity.tenantId(), agent.conversationModelId());
        if (!"CHAT".equals(model.modelType())) throw new IllegalStateException("Memory extraction model must be CHAT");
        String prompt = prompts.list(identity.tenantId()).stream()
                .filter(p -> "MEMORY_EXTRACT".equals(p.getType()) && "ENABLED".equals(p.getStatus()))
                .findFirst().map(p -> p.getContent()).orElse(DEFAULT_PROMPT);
        prompt += "\n当前本地时间：" + java.time.LocalDateTime.now()
                + (identity.memberMemoryAllowed() ? "\n已验证成员，可使用 MEMBER、MEMBER_ROBOT 或 ROBOT 范围。"
                : "\n说话者身份未验证，只允许 ROBOT 范围；不得建立或推断成员身份。");
        return new LlmMemoryExtractor(clients.requireChat(model.providerType()), model, prompt,
                JsonUtils.getObjectMapper(), error -> { throw new IllegalStateException("Memory extraction failed", error); })
                .extract(turn, identity, directive);
    }
}
