import request from '@/config/axios'
import { ref } from 'vue'

export interface NamedOption {
  id: number
  name: string
}
export interface VoiceOption {
  id: string
  name: string
}
export interface ModelOption extends NamedOption {
  modelType: string
  status: string
  voices: VoiceOption[]
}
export interface DisplayOptions {
  agents: NamedOption[]
  robots: NamedOption[]
  members: NamedOption[]
  providers: NamedOption[]
  models: ModelOption[]
}
export const AiDisplayApi = {
  get: () => request.get<DisplayOptions>({ url: '/ai/display-options' })
}
export function useAiNames() {
  const options = ref<DisplayOptions>({
    agents: [],
    robots: [],
    members: [],
    providers: [],
    models: []
  })
  const name = (kind: keyof DisplayOptions, id?: number | null) => {
    if (!id) return '—'
    return (
      options.value[kind].find((item) => item.id === id)?.name ||
      {
        agents: '已删除的智能体',
        robots: '已删除的机器人',
        members: '已删除的成员',
        providers: '已删除的服务商',
        models: '已删除的模型'
      }[kind]
    )
  }
  const loadNames = async () => {
    options.value = await AiDisplayApi.get()
  }
  return { options, name, loadNames }
}
export const statusName = (status?: string) =>
  ({
    ACTIVE: '进行中',
    CONNECTED: '已连接',
    CLOSED: '已结束',
    ERROR: '异常',
    ENABLED: '启用',
    DISABLED: '停用',
    DELETED: '已失效',
    SUPERSEDED: '已替换'
  })[status || ''] ||
  status ||
  '—'
export const modeName = (mode?: string) =>
  ({ NATIVE: '原生实时语音', CASCADE: '级联语音', AUTO: '自动' })[mode || ''] || '文字对话'
