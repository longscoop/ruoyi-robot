package com.robot.platform.ai.digitalhuman.controller.admin;

import com.robot.platform.ai.digitalhuman.provider.DigitalHumanProviders;
import com.robot.platform.ai.digitalhuman.service.*;
import com.robot.platform.framework.common.pojo.CommonResult;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static com.robot.platform.framework.common.pojo.CommonResult.success;
import static com.robot.platform.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static com.robot.platform.framework.common.exception.util.ServiceExceptionUtil.invalidParamException;

@RestController
@RequestMapping("/ai/digital-humans")
@RequiredArgsConstructor
public class DigitalHumanRenderController {
    private final AiDigitalHumanService humans;
    private final DigitalHumanProviders providers;
    private final DigitalHumanPreviewSessions sessions;

    @GetMapping("/render-services")
    @PreAuthorize("@ss.hasPermission('ai:digital-human:query')")
    public CommonResult<List<DigitalHumanProviders.ServiceOption>> services() { return success(providers.services()); }

    @PostMapping("/{id}/render-sessions")
    @PreAuthorize("@ss.hasPermission('ai:digital-human:preview')")
    public CommonResult<DigitalHumanPreviewSessions.Answer> open(@PathVariable long id, @RequestBody Offer offer) {
        var human = humans.get(tenant(), id);
        if (!"ENABLED".equals(human.getStatus())) throw invalidParamException("数字人已停用");
        return success(sessions.open(tenant(), user(), id, human.getConfigJson(), offer.sdp()));
    }

    @PostMapping("/{id}/render-sessions/{sessionId}/speak")
    @PreAuthorize("@ss.hasPermission('ai:digital-human:preview')")
    public CommonResult<Boolean> speak(@PathVariable long id, @PathVariable String sessionId, @RequestBody Speech speech) {
        sessions.speak(tenant(), user(), id, sessionId, speech.text()); return success(true);
    }

    @PostMapping("/{id}/render-sessions/{sessionId}/interrupt")
    @PreAuthorize("@ss.hasPermission('ai:digital-human:preview')")
    public CommonResult<Boolean> interrupt(@PathVariable long id, @PathVariable String sessionId) {
        sessions.interrupt(tenant(), user(), id, sessionId); return success(true);
    }

    @GetMapping("/{id}/render-sessions/{sessionId}/speaking")
    @PreAuthorize("@ss.hasPermission('ai:digital-human:preview')")
    public CommonResult<Boolean> speaking(@PathVariable long id, @PathVariable String sessionId) {
        return success(sessions.speaking(tenant(), user(), id, sessionId));
    }

    @DeleteMapping("/{id}/render-sessions/{sessionId}")
    @PreAuthorize("@ss.hasPermission('ai:digital-human:preview')")
    public CommonResult<Boolean> close(@PathVariable long id, @PathVariable String sessionId) {
        sessions.close(tenant(), user(), id, sessionId); return success(true);
    }

    private static long tenant() { return TenantContextHolder.getRequiredTenantId(); }
    private static long user() { Long user = getLoginUserId(); if (user == null) throw invalidParamException("需要登录后预览"); return user; }
    public record Offer(String sdp) {}
    public record Speech(String text) {}
}
