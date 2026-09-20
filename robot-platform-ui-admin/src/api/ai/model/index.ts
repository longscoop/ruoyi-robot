import request from '@/config/axios'
export interface ProviderVO { id:number; name:string; code:string; providerType:'QWEN'|'DEEPSEEK'|'DOUBAO'; baseUrl:string; configJson?:string; status:string; apiKeyConfigured:boolean }
export interface ProviderWriteVO { name:string; code:string; providerType:string; baseUrl:string; apiKey?:string; configJson?:string; status?:string }
export interface AiModelVO { id:number; providerId:number; name:string; modelCode:string; modelType:'CHAT'|'REALTIME_S2S'|'ASR'|'TTS'|'EMBEDDING'; capabilitiesJson?:string; configJson?:string; status:string }
export const AiModelApi = {
  listProviders:()=>request.get<ProviderVO[]>({url:'/ai/providers'}), getProvider:(id:number)=>request.get<ProviderVO>({url:`/ai/providers/${id}`}),
  createProvider:(data:ProviderWriteVO)=>request.post<number>({url:'/ai/providers',data}), updateProvider:(id:number,data:ProviderWriteVO)=>request.put({url:`/ai/providers/${id}`,data}), deleteProvider:(id:number)=>request.delete({url:`/ai/providers/${id}`}),
  listModels:()=>request.get<AiModelVO[]>({url:'/ai/models'}), getModel:(id:number)=>request.get<AiModelVO>({url:`/ai/models/${id}`}),
  createModel:(data:Omit<AiModelVO,'id'>)=>request.post<number>({url:'/ai/models',data}), updateModel:(id:number,data:Omit<AiModelVO,'id'>)=>request.put({url:`/ai/models/${id}`,data}), deleteModel:(id:number)=>request.delete({url:`/ai/models/${id}`})
}
