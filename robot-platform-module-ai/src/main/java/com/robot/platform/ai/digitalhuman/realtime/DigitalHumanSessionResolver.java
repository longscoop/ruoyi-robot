package com.robot.platform.ai.digitalhuman.realtime;
import com.robot.platform.ai.agent.dal.dataobject.AiAgentDO;import com.robot.platform.ai.agent.dal.mysql.AiAgentMapper;
import com.robot.platform.ai.digitalhuman.dal.dataobject.AiDigitalHumanDO;import com.robot.platform.ai.digitalhuman.dal.mysql.AiDigitalHumanMapper;
import static com.robot.platform.framework.common.exception.util.ServiceExceptionUtil.invalidParamException;
public class DigitalHumanSessionResolver {
 private final AiDigitalHumanMapper humans;private final AiAgentMapper agents;
 public DigitalHumanSessionResolver(AiDigitalHumanMapper h,AiAgentMapper a){humans=h;agents=a;}
 public Resolved resolve(long tenantId,String digitalHumanCode,String requestedAgentCode){
  if(digitalHumanCode==null||digitalHumanCode.isBlank()) return null;
  AiDigitalHumanDO h=humans.selectByCodeAndTenantId(digitalHumanCode,tenantId);
  if(h==null||!"ENABLED".equals(h.getStatus()))throw invalidParamException("Digital human is unavailable in current tenant");
  AiAgentDO a=agents.selectByIdAndTenantId(h.getAgentId(),tenantId);if(a==null)throw invalidParamException("Digital human agent is unavailable");
  if(requestedAgentCode!=null&&!requestedAgentCode.isBlank()&&!requestedAgentCode.equals(a.getCode()))throw invalidParamException("digitalHumanCode and agentCode must resolve to the same agent");
  return new Resolved(h,a);
 }
 public record Resolved(AiDigitalHumanDO digitalHuman,AiAgentDO agent){}
}
