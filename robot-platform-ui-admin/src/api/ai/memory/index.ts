import request from '@/config/axios'
export interface MemoryVO {
  id: number
  scope: string
  memberId?: number
  robotId?: number
  memoryType: string
  content: string
  summary?: string
  importance: number
  confidence: number
  expiresAt?: string
  status: string
  createdAt?: string
  updatedAt?: string
  sourceConversationId?: number
}
export type MemoryUpdateVO = Pick<MemoryVO, 'content' | 'summary' | 'importance' | 'expiresAt'> & {
  memoryType?: string
}
export const AiMemoryApi = {
  list: (params?: Record<string, unknown>) =>
    request.get<MemoryVO[]>({ url: '/ai/memories', params }),
  update: (id: number, data: MemoryUpdateVO) => request.put({ url: `/ai/memories/${id}`, data }),
  delete: (id: number) => request.delete({ url: `/ai/memories/${id}` })
}

export interface MemoryProviderStatus {
  mode: string
  provider: string
  name: string
  configured: boolean
  saveMessageThreshold: number
}
export interface MemorySnippet {
  scope: string
  memoryType: string
  content: string
  score: number
}
export const MemoryProviderApi = {
  list: () => request.get<MemoryProviderStatus[]>({ url: '/ai/memory-providers' }),
  query: (agentId: number, robotId: number, question = '') =>
    request.get<MemorySnippet[]>({
      url: `/ai/memory-providers/${agentId}/query`,
      params: { robotId, question }
    })
}
