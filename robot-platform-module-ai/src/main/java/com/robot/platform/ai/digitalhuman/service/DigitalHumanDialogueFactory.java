package com.robot.platform.ai.digitalhuman.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.robot.platform.ai.agent.service.AiAgentService;
import com.robot.platform.ai.digitalhuman.dal.dataobject.AiDigitalHumanDO;
import com.robot.platform.ai.model.client.*;
import com.robot.platform.ai.realtime.runtime.*;
import com.robot.platform.framework.common.util.json.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import static com.robot.platform.framework.common.exception.util.ServiceExceptionUtil.invalidParamException;

/** Admin previews reuse voice providers without impersonating a robot or writing household memory. */
@Component
@RequiredArgsConstructor
public class DigitalHumanDialogueFactory {
    private final AiDigitalHumanService humans;
    private final AiAgentService agents;
    private final ResolvedModelResolver models;
    private final ModelClientRegistry clients;
    private final RealtimeModelRouter router;

    public RealtimeProviderSession open(long tenant, long humanId, RealtimeTurnListener listener) {
        AiDigitalHumanDO human = humans.get(tenant, humanId);
        if (!"ENABLED".equals(human.getStatus())) throw invalidParamException("数字人已停用");
        if (!"ENABLED".equals(agents.get(tenant, human.getAgentId()).getStatus()))
            throw invalidParamException("数字人绑定的智能体已停用");
        var agent = agents.getResolvedConfig(tenant, human.getAgentId());
        var route = router.route(agent, RealtimeModelRouter.ModelCapabilities.configured(agent));
        String prompt = agent.systemPrompt() + "\n你正在进行数字人语音预览，请自然简洁地回答。此会话不操作真实机器人。";
        if (route.mode() == RealtimeRoute.Mode.NATIVE) {
            var model = resolve(tenant, route.realtimeModelId(), "REALTIME_S2S")
                    .withRealtimeSession(prompt, voiceConfig(agent.voiceConfigJson(), human.getVoiceId()));
            return clients.requireRealtimeVoice(model.providerType()).openTurnAware(model, listener);
        }
        var asr = resolve(tenant, route.asrModelId(), "ASR");
        var chat = resolve(tenant, route.conversationModelId(), "CHAT");
        var tts = resolve(tenant, human.getVoiceModelId() == null ? route.ttsModelId() : human.getVoiceModelId(), "TTS");
        return new CascadeRealtimePipeline(asr, chat,
                tts.withModelConfig(voiceConfig(tts.modelConfigJson(), human.getVoiceId())), prompt, clients, listener);
    }

    private ResolvedModel resolve(long tenant, Long id, String type) {
        if (id == null) throw invalidParamException("请先配置智能体的语音模型");
        var model = models.resolve(tenant, id);
        if (!type.equals(model.modelType())) throw invalidParamException("智能体语音模型类型不匹配");
        return model;
    }

    static String voiceConfig(String json, String voice) {
        if (voice == null || voice.isBlank()) return json;
        ObjectNode config = json == null || json.isBlank() ? JsonUtils.getObjectMapper().createObjectNode()
                : (ObjectNode) JsonUtils.parseTree(json).deepCopy();
        config.put("voice", voice);
        return JsonUtils.toJsonString(config);
    }
}
