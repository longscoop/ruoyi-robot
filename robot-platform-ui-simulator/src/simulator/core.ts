export type Direction='IN'|'OUT'|'LOCAL';export type Transport='HTTP'|'MQTT'|'WSS'|'SIM'
export type MessageType='HEARTBEAT'|'MISSION_START'|'MISSION_CANCEL'|'MISSION_ACK'|'MISSION_EVENT'|'OTA_COMMAND'
export interface Envelope<T=unknown>{messageId:string;requestId?:string;timestamp:number;version:1;type:MessageType;source:'CLOUD'|'ROBOT';data:T}
export interface TraceEvent{id:string;at:number;direction:Direction;transport:Transport;type:string;topic?:string;correlationId?:string;payload:unknown;latencyMs?:number}
export const uid=()=>crypto.randomUUID()
export class TraceStore{events:TraceEvent[]=[];push(e:Omit<TraceEvent,'id'|'at'>){this.events.unshift({id:uid(),at:Date.now(),...e});if(this.events.length>1000)this.events.length=1000}clear(){this.events=[]}export(){return JSON.stringify([...this.events].reverse(),null,2)}}
export class IdempotencyGuard{private seen=new Set<string>();accept(id:string){if(this.seen.has(id))return false;this.seen.add(id);return true}reset(){this.seen.clear()}}
export interface ScenarioStep{name:string;run:(ctx:ScenarioContext)=>Promise<void>|void}
export interface ScenarioContext{log:(type:string,payload?:unknown)=>void;signal:AbortSignal}
export class ScenarioRunner{private abort?:AbortController;paused=false;async run(steps:ScenarioStep[],log:ScenarioContext['log']){this.abort=new AbortController();for(const s of steps){while(this.paused)await new Promise(r=>setTimeout(r,100));if(this.abort.signal.aborted)break;log('scenario.step',{name:s.name});await s.run({log,signal:this.abort.signal})}}pause(){this.paused=true}resume(){this.paused=false}cancel(){this.abort?.abort()}reset(){this.cancel();this.paused=false}}
export const envelope=<T>(type:MessageType,data:T,requestId?:string):Envelope<T>=>({messageId:uid(),requestId,timestamp:Date.now(),version:1,type,source:'ROBOT',data})
