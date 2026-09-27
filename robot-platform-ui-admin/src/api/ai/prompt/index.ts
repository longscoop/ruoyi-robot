import request from '@/config/axios'
export interface PromptVO { id:number; name:string; code:string; type:string; content:string; version:number; status:string }
export const AiPromptApi={list:()=>request.get<PromptVO[]>({url:'/ai/prompts'}),get:(id:number)=>request.get<PromptVO>({url:`/ai/prompts/${id}`}),create:(data:Omit<PromptVO,'id'|'version'>)=>request.post<number>({url:'/ai/prompts',data})}
