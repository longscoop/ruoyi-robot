#!/usr/bin/env python3
import argparse,asyncio,json,os,time
from pathlib import Path
import websockets
def evt(t,**x):return json.dumps({"type":t,**x},ensure_ascii=False)
async def run(a):
 token=os.environ["DEVICE_TOKEN"];uri=a.base.rstrip("/")+"/device-api/ai/realtime"
 async with websockets.connect(uri,additional_headers={"Authorization":"Bearer "+token}) as ws:
  started=time.monotonic();identity={"memberId":a.member_id,"type":"APP_BOUND","confidence":1.0} if a.member_id else None
  await ws.send(evt("session.start",agentCode=a.agent_code,digitalHumanCode=a.digital_human_code or None,clientCapabilities={"viseme":True,"audioLevelLipSync":True},audio={"codec":"PCM_S16LE","sampleRate":16000,"channels":1},identity=identity));await ws.send(evt("input.speech_started"));await ws.send(Path(a.pcm).read_bytes());await ws.send(evt("input.speech_stopped"))
  async for raw in ws:
   if isinstance(raw,bytes):print("audio",len(raw),"bytes");continue
   m=json.loads(raw);print(m.get("type"),m.get("sessionId",""),f"{(time.monotonic()-started)*1000:.0f}ms")
   if m.get("type")=="assistant.done":
    if a.interrupt:await ws.send(evt("input.speech_started"));await ws.send(Path(a.pcm).read_bytes());await ws.send(evt("input.speech_stopped"));a.interrupt=False;continue
    await ws.send(evt("session.close",reason="E2E_COMPLETE"));break
def main():
 p=argparse.ArgumentParser();p.add_argument("--base",default=os.getenv("REALTIME_BASE_URL","ws://localhost:48080"));p.add_argument("--agent-code",default=os.getenv("AGENT_CODE","qwen-native"));p.add_argument("--digital-human-code",default=os.getenv("DIGITAL_HUMAN_CODE",""));p.add_argument("--pcm",default=str(Path(__file__).with_name("fixtures")/"silence-16k-s16le.pcm"));p.add_argument("--member-id",type=int);p.add_argument("--interrupt",action="store_true");a=p.parse_args()
 if a.agent_code not in {"qwen-native","doubao-native","deepseek-cascade"}:p.error("unsupported AGENT_CODE")
 asyncio.run(run(a))
if __name__=="__main__":main()
