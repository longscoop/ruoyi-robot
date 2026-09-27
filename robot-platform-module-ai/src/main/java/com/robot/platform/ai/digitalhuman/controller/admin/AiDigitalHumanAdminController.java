package com.robot.platform.ai.digitalhuman.controller.admin;

import com.robot.platform.ai.digitalhuman.dal.dataobject.*;
import com.robot.platform.ai.digitalhuman.service.AiDigitalHumanService;
import com.robot.platform.framework.common.pojo.CommonResult;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import lombok.Data;import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;import java.util.*;
import static com.robot.platform.framework.common.pojo.CommonResult.success;

@RestController @RequestMapping("/admin-api/ai/digital-humans") @RequiredArgsConstructor
public class AiDigitalHumanAdminController {
 private final AiDigitalHumanService service;
 @GetMapping @PreAuthorize("@ss.hasPermission('ai:digital-human:query')") public CommonResult<List<Resp>> list(){return success(service.list(tenant()).stream().map(AiDigitalHumanAdminController::resp).toList());}
 @GetMapping("/{id}") @PreAuthorize("@ss.hasPermission('ai:digital-human:query')") public CommonResult<Resp> get(@PathVariable long id){return success(resp(service.get(tenant(),id)));}
 @PostMapping @PreAuthorize("@ss.hasPermission('ai:digital-human:create')") public CommonResult<Long> create(@RequestBody Req r){return success(service.create(cmd(r)).getId());}
 @PutMapping("/{id}") @PreAuthorize("@ss.hasPermission('ai:digital-human:update')") public CommonResult<Boolean> update(@PathVariable long id,@RequestBody Req r){service.update(id,cmd(r));return success(true);}
 @DeleteMapping("/{id}") @PreAuthorize("@ss.hasPermission('ai:digital-human:delete')") public CommonResult<Boolean> delete(@PathVariable long id){service.delete(tenant(),id);return success(true);}
 @PutMapping("/{id}/actions") @PreAuthorize("@ss.hasPermission('ai:digital-human:update')") public CommonResult<List<ActionResp>> actions(@PathVariable long id,@RequestBody List<ActionReq> rs){return success(service.replaceActions(tenant(),id,rs.stream().map(x->new AiDigitalHumanService.ActionCommand(x.state,x.actionCode,x.configJson)).toList()).stream().map(x->new ActionResp(x.getId(),x.getState(),x.getActionCode(),x.getConfigJson())).toList());}
 @PostMapping("/{id}/preview-session") @PreAuthorize("@ss.hasPermission('ai:digital-human:preview')") public CommonResult<PreviewResp> preview(@PathVariable long id){AiDigitalHumanDO h=service.get(tenant(),id);return success(new PreviewResp(h.getId(),h.getCode(),h.getAgentId(),h.getAvatarType(),h.getAvatarUrl(),h.getAvatarResourceUrl(),h.getVoiceModelId(),h.getVoiceId(),h.getLipSyncMode(),h.getWelcomeText(),Boolean.TRUE.equals(h.getInterruptEnabled())));}
 private AiDigitalHumanService.Command cmd(Req r){return new AiDigitalHumanService.Command(tenant(),r.name,r.code,r.description,r.agentId,r.avatarType,r.avatarUrl,r.avatarResourceUrl,r.coverUrl,r.voiceModelId,r.voiceId,r.speechRate,r.pitch,r.volume,r.lipSyncMode,r.welcomeText,r.interruptEnabled,r.configJson,r.status);}
 private static long tenant(){return TenantContextHolder.getRequiredTenantId();}
 private static Resp resp(AiDigitalHumanDO h){return new Resp(h.getId(),h.getName(),h.getCode(),h.getDescription(),h.getAgentId(),h.getAvatarType(),h.getAvatarUrl(),h.getAvatarResourceUrl(),h.getCoverUrl(),h.getVoiceModelId(),h.getVoiceId(),h.getSpeechRate(),h.getPitch(),h.getVolume(),h.getLipSyncMode(),h.getWelcomeText(),Boolean.TRUE.equals(h.getInterruptEnabled()),h.getConfigJson(),h.getStatus());}
 @Data public static class Req {String name;String code;String description;long agentId;String avatarType="STATIC_2D";String avatarUrl;String avatarResourceUrl;String coverUrl;Long voiceModelId;String voiceId;BigDecimal speechRate;BigDecimal pitch;BigDecimal volume;String lipSyncMode="AUDIO_LEVEL";String welcomeText;boolean interruptEnabled=true;String configJson;String status;}
 @Data public static class ActionReq {String state;String actionCode;String configJson;}
 public record Resp(Long id,String name,String code,String description,Long agentId,String avatarType,String avatarUrl,String avatarResourceUrl,String coverUrl,Long voiceModelId,String voiceId,BigDecimal speechRate,BigDecimal pitch,BigDecimal volume,String lipSyncMode,String welcomeText,boolean interruptEnabled,String configJson,String status){}
 public record ActionResp(Long id,String state,String actionCode,String configJson){}
 public record PreviewResp(Long id,String code,Long agentId,String avatarType,String avatarUrl,String avatarResourceUrl,Long voiceModelId,String voiceId,String lipSyncMode,String welcomeText,boolean interruptEnabled){}
}
