package com.robot.platform.ai.digitalhuman.service;

import com.robot.platform.ai.agent.dal.mysql.AiAgentMapper;
import com.robot.platform.ai.digitalhuman.dal.dataobject.*;
import com.robot.platform.ai.digitalhuman.dal.mysql.*;
import com.robot.platform.ai.model.dal.dataobject.AiModelDO;
import com.robot.platform.ai.model.dal.mysql.AiModelMapper;
import com.robot.platform.framework.tenant.core.context.TenantContextHolder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.robot.platform.framework.common.exception.util.ServiceExceptionUtil.invalidParamException;

@Service
public class AiDigitalHumanServiceImpl implements AiDigitalHumanService {
 private static final Set<String> AVATARS=Set.of("STATIC_2D","LIVE2D","THREE_D","EXTERNAL");
 private static final Set<String> LIPS=Set.of("AUDIO_LEVEL","VISEME","PROVIDER");
 private static final Set<String> STATES=Set.of("IDLE","LISTENING","THINKING","SPEAKING","EXECUTING","ERROR");
 private final AiDigitalHumanMapper mapper; private final AiDigitalHumanActionMapper actionMapper;
 private final AiAgentMapper agentMapper; private final AiModelMapper modelMapper;
 public AiDigitalHumanServiceImpl(AiDigitalHumanMapper m,AiDigitalHumanActionMapper a,AiAgentMapper ag,AiModelMapper mm){mapper=m;actionMapper=a;agentMapper=ag;modelMapper=mm;}
 public AiDigitalHumanDO create(Command c){tenant(c.tenantId()); validate(c); AiDigitalHumanDO r=new AiDigitalHumanDO(); apply(r,c); try{mapper.insert(r);}catch(DuplicateKeyException e){throw invalidParamException("Digital human code already exists in current tenant");} return r;}
 public AiDigitalHumanDO update(long id,Command c){tenant(c.tenantId()); AiDigitalHumanDO r=require(c.tenantId(),id);validate(c);apply(r,c);try{mapper.updateById(r);}catch(DuplicateKeyException e){throw invalidParamException("Digital human code already exists in current tenant");}return r;}
 public AiDigitalHumanDO get(long t,long id){tenant(t);return require(t,id);}
 public List<AiDigitalHumanDO> list(long t){tenant(t);return mapper.selectByTenantId(t);}
 @Transactional public void delete(long t,long id){tenant(t);require(t,id);actionMapper.logicalDeleteByDigitalHumanIdAndTenantId(id,t);mapper.logicalDeleteByIdAndTenantId(id,t);}
 @Transactional public List<AiDigitalHumanActionDO> replaceActions(long t,long id,List<ActionCommand> cs){tenant(t);require(t,id);actionMapper.logicalDeleteByDigitalHumanIdAndTenantId(id,t);List<AiDigitalHumanActionDO> out=new ArrayList<>();Set<String> seen=new HashSet<>();for(ActionCommand c:cs){String s=norm(c.state());if(!STATES.contains(s)||!seen.add(s))throw invalidParamException("Invalid or duplicate digital human state: {}",s);if(c.actionCode()==null||c.actionCode().isBlank())throw invalidParamException("actionCode must not be blank");AiDigitalHumanActionDO a=new AiDigitalHumanActionDO();a.setTenantId(t);a.setDigitalHumanId(id);a.setState(s);a.setActionCode(c.actionCode().trim());a.setConfigJson(c.configJson());actionMapper.insert(a);out.add(a);}return out;}
 private void validate(Command c){if(c.name()==null||c.name().isBlank()||c.code()==null||c.code().isBlank())throw invalidParamException("Digital human name and code are required");if(agentMapper.selectByIdAndTenantId(c.agentId(),c.tenantId())==null)throw invalidParamException("Agent does not exist in current tenant");if(c.voiceModelId()!=null){AiModelDO m=modelMapper.selectByIdAndTenantId(c.voiceModelId(),c.tenantId());if(m==null||!"TTS".equals(m.getModelType()))throw invalidParamException("voiceModelId must reference a TTS model in current tenant");}if(!AVATARS.contains(norm(c.avatarType())))throw invalidParamException("Unsupported avatar type");if(!LIPS.contains(norm(c.lipSyncMode())))throw invalidParamException("Unsupported lip sync mode");}
 private AiDigitalHumanDO require(long t,long id){AiDigitalHumanDO r=mapper.selectByIdAndTenantId(id,t);if(r==null)throw invalidParamException("Digital human does not exist");return r;}
 private static void tenant(long t){if(TenantContextHolder.getRequiredTenantId()!=t)throw invalidParamException("AI tenant context mismatch");}
 private static String norm(String s){return s==null?"":s.trim().toUpperCase(Locale.ROOT);}
 private static void apply(AiDigitalHumanDO r,Command c){r.setTenantId(c.tenantId());r.setName(c.name().trim());r.setCode(c.code().trim());r.setDescription(c.description());r.setAgentId(c.agentId());r.setAvatarType(norm(c.avatarType()));r.setAvatarUrl(c.avatarUrl());r.setAvatarResourceUrl(c.avatarResourceUrl());r.setCoverUrl(c.coverUrl());r.setVoiceModelId(c.voiceModelId());r.setVoiceId(c.voiceId());r.setSpeechRate(c.speechRate());r.setPitch(c.pitch());r.setVolume(c.volume());r.setLipSyncMode(norm(c.lipSyncMode()));r.setWelcomeText(c.welcomeText());r.setInterruptEnabled(c.interruptEnabled());r.setConfigJson(c.configJson());r.setStatus(c.status()==null||c.status().isBlank()?"ENABLED":norm(c.status()));}
}
