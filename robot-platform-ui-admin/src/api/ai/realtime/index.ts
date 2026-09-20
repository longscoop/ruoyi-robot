import request from '@/config/axios'
export interface RealtimeSessionVO { id:number; conversationId:number; agentId:number; robotId:number; memberId?:number; mode:'NATIVE'|'CASCADE'; providerId?:number; modelId?:number; providerSessionId?:string; status:string; errorCode?:string; firstAudioAt?:string; firstResponseAt?:string; interruptCount:number; startedAt?:string; endedAt?:string }
export const AiRealtimeApi={list:()=>request.get<RealtimeSessionVO[]>({url:'/ai/realtime-sessions'}),get:(id:number)=>request.get<RealtimeSessionVO>({url:`/ai/realtime-sessions/${id}`})}
