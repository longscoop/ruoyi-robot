package com.robot.platform.ai.admin.controller.admin;

import com.robot.platform.ai.admin.AiDisplayOptionMapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.robot.platform.ai.model.dal.mysql.AiModelMapper;
import com.robot.platform.ai.model.dal.mysql.AiModelProviderMapper;
import com.robot.platform.framework.common.pojo.CommonResult;
import com.robot.platform.framework.common.util.json.JsonUtils;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static com.robot.platform.framework.common.pojo.CommonResult.success;

@RestController @RequestMapping("/ai/display-options") @RequiredArgsConstructor
public class AiDisplayOptionsController {
    private final AiDisplayOptionMapper names;
    private final AiModelMapper models;
    private final AiModelProviderMapper providers;
    @GetMapping
    @PreAuthorize("@ss.hasAnyPermissions('ai:conversation:query','ai:realtime:query','ai:memory:query','ai:digital-human:query','ai:agent:query','ai:model:query')")
    public CommonResult<Options> get() {
        long tenant = TenantContextHolder.getRequiredTenantId();
        var providerRows = providers.selectByTenantId(tenant);
        var modelRows = models.selectByTenantId(tenant).stream().map(model -> {
            var provider = providerRows.stream().filter(p -> p.getId().equals(model.getProviderId())).findFirst().orElse(null);
            String type = provider == null ? "" : provider.getProviderType();
            return new ModelOption(model.getId(), model.getName(), model.getModelType(), model.getStatus(),
                    voices(type, model.getModelCode(), model.getConfigJson()));
        }).toList();
        return success(new Options(names.agents(tenant), names.robots(tenant), names.members(tenant),
                names.providers(tenant), modelRows));
    }
    static List<Voice> voices(String provider, String model, String configJson) {
        Map<String, Voice> result = new LinkedHashMap<>();
        if ("QWEN".equals(provider) && model != null &&
                (model.startsWith("qwen3-tts-flash") || model.startsWith("qwen-tts") || model.startsWith("qwen3-omni-flash-realtime"))) {
            for (Voice voice : List.of(new Voice("Cherry", "芊悦 · 亲切女声"), new Voice("Serena", "苏瑶 · 温柔女声"),
                    new Voice("Ethan", "晨煦 · 普通话男声"), new Voice("Chelsie", "千雪 · 活泼女声"))) result.put(voice.id(), voice);
        }
        if (configJson != null && !configJson.isBlank()) {
            JsonNode config;
            try { config = JsonUtils.parseTree(configJson); } catch (RuntimeException ignored) { return List.copyOf(result.values()); }
            if (config != null) {
                JsonNode list = config.path("voices");
                if (list.isArray()) for (JsonNode node : list) {
                    String id = node.isTextual() ? node.asText() : node.path("id").asText();
                    if (!id.isBlank()) result.put(id, new Voice(id, node.isTextual() ? id : node.path("name").asText(id)));
                }
                String current = config.path("voice").asText("");
                if (!current.isBlank()) result.putIfAbsent(current, new Voice(current, current));
            }
        }
        return List.copyOf(result.values());
    }
    public record Voice(String id, String name) {}
    public record ModelOption(Long id, String name, String modelType, String status, List<Voice> voices) {}
    public record Options(List<AiDisplayOptionMapper.Option> agents, List<AiDisplayOptionMapper.Option> robots,
                          List<AiDisplayOptionMapper.Option> members, List<AiDisplayOptionMapper.Option> providers,
                          List<ModelOption> models) {}
}
