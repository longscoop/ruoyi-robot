import { beforeEach, describe, expect, it, vi } from 'vitest'

const request = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  delete: vi.fn()
}))

vi.mock('@/config/axios', () => ({ default: request }))

import { AgentApi, KnowledgeApi, ModelApi, PromptApi, ProviderApi } from './index'

describe('enterprise AI API routes', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('uses the admin API routes registered by the AI module', async () => {
    await ProviderApi.list()
    await ModelApi.list()
    await PromptApi.list()
    await AgentApi.list()

    expect(request.get.mock.calls.map(([option]) => option.url)).toEqual([
      '/ai/providers',
      '/ai/models',
      '/ai/prompts',
      '/ai/agents'
    ])
  })

  it('creates a role without a code and edits the same role', async () => {
    const data = {
      name: '家庭助手角色',
      type: 'SYSTEM',
      content: '简洁回答',
      status: 'ENABLED' as const
    }
    await PromptApi.create(data)
    await PromptApi.update(7, { ...data, content: '使用中文简洁回答' })
    expect(request.post).toHaveBeenCalledWith({ url: '/ai/prompts', data })
    expect(request.put).toHaveBeenCalledWith({
      url: '/ai/prompts/7',
      data: { ...data, content: '使用中文简洁回答' }
    })
  })

  it('binds and unbinds a robot under its selected agent', async () => {
    await AgentApi.bindRobot(12, { robotId: 34, defaultAgent: true })
    await AgentApi.unbindRobot(12, 34)

    expect(request.post).toHaveBeenCalledWith({
      url: '/ai/agents/12/robots',
      data: { robotId: 34, defaultAgent: true }
    })
    expect(request.delete).toHaveBeenCalledWith({ url: '/ai/agents/12/robots/34' })
  })

  it('sends agent changes to the selected record', async () => {
    const data = { name: 'Reception', code: 'reception' }
    await AgentApi.create(data as never)
    await AgentApi.update(12, data as never)
    await AgentApi.remove(12)

    expect(request.post).toHaveBeenCalledWith({ url: '/ai/agents', data })
    expect(request.put).toHaveBeenCalledWith({ url: '/ai/agents/12', data })
    expect(request.delete).toHaveBeenCalledWith({ url: '/ai/agents/12' })
  })

  it('scopes document operations to the selected knowledge base', async () => {
    await KnowledgeApi.listDocuments(9)
    await KnowledgeApi.createDocument(9, { title: 'Guide', content: 'Text' })
    await KnowledgeApi.updateDocument(9, 13, { title: 'Guide', content: 'New text' })
    await KnowledgeApi.removeDocument(9, 13)

    expect(request.get).toHaveBeenCalledWith({ url: '/ai/knowledge-bases/9/documents' })
    expect(request.post).toHaveBeenCalledWith({
      url: '/ai/knowledge-bases/9/documents',
      data: { title: 'Guide', content: 'Text' }
    })
    expect(request.put).toHaveBeenCalledWith({
      url: '/ai/knowledge-bases/9/documents/13',
      data: { title: 'Guide', content: 'New text' }
    })
    expect(request.delete).toHaveBeenCalledWith({ url: '/ai/knowledge-bases/9/documents/13' })
  })
})
