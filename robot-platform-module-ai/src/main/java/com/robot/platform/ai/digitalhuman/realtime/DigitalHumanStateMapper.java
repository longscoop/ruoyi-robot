package com.robot.platform.ai.digitalhuman.realtime;
import com.robot.platform.ai.realtime.protocol.RealtimeServerEvent;
public final class DigitalHumanStateMapper {
 private DigitalHumanStateMapper(){}
 public static String stateFor(RealtimeServerEvent e){
  if(e instanceof RealtimeServerEvent.ToolStartedEvent)return "EXECUTING";
  if(e instanceof RealtimeServerEvent.InputTranscriptDoneEvent)return "THINKING";
  if(e instanceof RealtimeServerEvent.AssistantAudioStartedEvent)return "SPEAKING";
  if(e instanceof RealtimeServerEvent.AssistantAudioDoneEvent||e instanceof RealtimeServerEvent.AssistantDoneEvent||e instanceof RealtimeServerEvent.PlaybackStopEvent||e instanceof RealtimeServerEvent.AssistantInterruptedEvent)return "IDLE";
  if(e instanceof RealtimeServerEvent.SessionErrorEvent)return "ERROR";
  return null;
 }
}
