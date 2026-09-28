import request from '@/config/axios'

export type RecordStatus = 'ENABLED' | 'DISABLED'

export interface Provider {
  id: number
  name: string
  code: string
  providerType: string
  baseUrl: string | null
  configJson: string | null
  status: RecordStatus
  apiKeyConfigured: boolean
}

export type ProviderInput = Omit<Provider, 'id' | 'apiKeyConfigured'> & { apiKey?: string }

export interface Model {
  id: number
  providerId: number
  name: string
  modelCode: string
  modelType: string
  capabilitiesJson: string | null
  configJson: string | null
  status: RecordStatus
}

export type ModelInput = Omit<Model, 'id'>

export interface Prompt {
  id: number
  name: string
  code: string
  type: string
  content: string
  version: number
  status: RecordStatus
}

export type PromptInput = Omit<Prompt, 'id' | 'version'>

export interface Agent {
  id: number
  name: string
  code: string
  description: string | null
  systemPromptId: number
  conversationModelId: number | null
  realtimeModelId: number | null
  asrModelId: number | null
  ttsModelId: number | null
  realtimeMode: 'NATIVE' | 'CASCADE' | 'AUTO'
  memoryMode: 'NONE' | 'SESSION' | 'LONG_TERM'
  memoryReadEnabled: boolean
  memoryWriteEnabled: boolean
  knowledgeEnabled: boolean
  voiceConfigJson: string | null
  status: RecordStatus
}

export type AgentInput = Omit<Agent, 'id'>

export interface AgentRobotBinding {
  id: number
  agentId: number
  robotId: number
  defaultAgent: boolean
  status: RecordStatus
}

export interface KnowledgeBase {
  id: number
  name: string
  code: string
  description: string | null
  createTime: string
}

export type KnowledgeBaseInput = Pick<KnowledgeBase, 'name' | 'code' | 'description'>

export interface KnowledgeDocument {
  id: number
  baseId: number
  title: string
  content: string
  updateTime: string
}

export type KnowledgeDocumentInput = Pick<KnowledgeDocument, 'title' | 'content'>

export const ProviderApi = {
  list: () => request.get<Provider[]>({ url: '/ai/providers' }),
  create: (data: ProviderInput) => request.post<number>({ url: '/ai/providers', data }),
  update: (id: number, data: ProviderInput) =>
    request.put<boolean>({ url: `/ai/providers/${id}`, data }),
  remove: (id: number) => request.delete<boolean>({ url: `/ai/providers/${id}` })
}

export const ModelApi = {
  list: () => request.get<Model[]>({ url: '/ai/models' }),
  create: (data: ModelInput) => request.post<number>({ url: '/ai/models', data }),
  update: (id: number, data: ModelInput) => request.put<boolean>({ url: `/ai/models/${id}`, data }),
  remove: (id: number) => request.delete<boolean>({ url: `/ai/models/${id}` })
}

export const PromptApi = {
  list: () => request.get<Prompt[]>({ url: '/ai/prompts' }),
  create: (data: PromptInput) => request.post<number>({ url: '/ai/prompts', data })
}

export const AgentApi = {
  list: () => request.get<Agent[]>({ url: '/ai/agents' }),
  create: (data: AgentInput) => request.post<number>({ url: '/ai/agents', data }),
  update: (id: number, data: AgentInput) => request.put<boolean>({ url: `/ai/agents/${id}`, data }),
  remove: (id: number) => request.delete<boolean>({ url: `/ai/agents/${id}` }),
  listRobots: (id: number) => request.get<AgentRobotBinding[]>({ url: `/ai/agents/${id}/robots` }),
  bindRobot: (id: number, data: { robotId: number; defaultAgent: boolean }) =>
    request.post<number>({ url: `/ai/agents/${id}/robots`, data }),
  unbindRobot: (id: number, robotId: number) =>
    request.delete<boolean>({ url: `/ai/agents/${id}/robots/${robotId}` })
}

export const KnowledgeApi = {
  listBases: () => request.get<KnowledgeBase[]>({ url: '/ai/knowledge-bases' }),
  createBase: (data: KnowledgeBaseInput) =>
    request.post<number>({ url: '/ai/knowledge-bases', data }),
  updateBase: (id: number, data: KnowledgeBaseInput) =>
    request.put<boolean>({ url: `/ai/knowledge-bases/${id}`, data }),
  removeBase: (id: number) => request.delete<boolean>({ url: `/ai/knowledge-bases/${id}` }),
  listDocuments: (baseId: number) =>
    request.get<KnowledgeDocument[]>({ url: `/ai/knowledge-bases/${baseId}/documents` }),
  createDocument: (baseId: number, data: KnowledgeDocumentInput) =>
    request.post<number>({ url: `/ai/knowledge-bases/${baseId}/documents`, data }),
  updateDocument: (baseId: number, id: number, data: KnowledgeDocumentInput) =>
    request.put<boolean>({ url: `/ai/knowledge-bases/${baseId}/documents/${id}`, data }),
  removeDocument: (baseId: number, id: number) =>
    request.delete<boolean>({ url: `/ai/knowledge-bases/${baseId}/documents/${id}` })
}
