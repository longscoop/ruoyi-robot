import { describe, expect, it, vi } from 'vitest'
const request = vi.hoisted(() => ({ get: vi.fn() }))
vi.mock('@/config/axios', () => ({ default: request }))
import { useAiNames } from './index'
describe('AI display names', () => {
  it('loads names and avoids exposing identifiers when a reference was removed', async () => {
    request.get.mockResolvedValue({
      agents: [{ id: 1, name: '家庭助手' }],
      robots: [{ id: 2, name: '小智' }],
      members: [],
      providers: [],
      models: []
    })
    const names = useAiNames()
    await names.loadNames()
    expect(names.name('agents', 1)).toBe('家庭助手')
    expect(names.name('robots', 2)).toBe('小智')
    expect(names.name('members', 932)).toBe('已删除的成员')
    expect(names.name('models', null)).toBe('—')
  })
})
