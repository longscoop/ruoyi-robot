package com.robot.platform.ai.digitalhuman.realtime;
import com.robot.platform.ai.agent.service.AiAgentConfig;import com.robot.platform.ai.digitalhuman.dal.dataobject.AiDigitalHumanDO;
public final class DigitalHumanVoiceResolver {
 public ResolvedVoice resolve(AiDigitalHumanDO h,AiAgentConfig a,boolean clientViseme,boolean providerViseme){
  Long model=h.getVoiceModelId()!=null?h.getVoiceModelId():a.ttsModelId();
  String voice=h.getVoiceId();
  String lip="AUDIO_LEVEL";
  if("VISEME".equals(h.getLipSyncMode())&&clientViseme&&providerViseme)lip="VISEME";
  else if("PROVIDER".equals(h.getLipSyncMode())&&providerViseme)lip="PROVIDER";
  return new ResolvedVoice(model,voice,lip);
 }
 public record ResolvedVoice(Long modelId,String voiceId,String lipSyncMode){}
}
