import request from '@/config/axios'
export interface MemoryVO { id:number; scope:string; memberId?:number; robotId?:number; memoryType:string; content:string; summary?:string; importance:number; confidence:number; expiresAt?:string; status:string; createdAt?:string; updatedAt?:string }
export type MemoryUpdateVO=Pick<MemoryVO,'content'|'summary'|'importance'|'expiresAt'>
export const AiMemoryApi={list:()=>request.get<MemoryVO[]>({url:'/ai/memories'}),update:(id:number,data:MemoryUpdateVO)=>request.put({url:`/ai/memories/${id}`,data}),delete:(id:number)=>request.delete({url:`/ai/memories/${id}`})}
