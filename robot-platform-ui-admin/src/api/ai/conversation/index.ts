import request from '@/config/axios'
export interface ConversationMessageVO { id:number; turnId:string; role:'USER'|'ASSISTANT'|'TOOL_CALL'|'TOOL_RESULT'; content:string; modelId?:number; latencyMs?:number; createdAt?:string }
export interface ConversationVO { id:number; agentId:number; robotId:number; memberId?:number; channel:string; status:string; createdAt?:string; messages?:ConversationMessageVO[] }
export const AiConversationApi={list:()=>request.get<ConversationVO[]>({url:'/ai/conversations'}),get:(id:number)=>request.get<ConversationVO>({url:`/ai/conversations/${id}`})}
