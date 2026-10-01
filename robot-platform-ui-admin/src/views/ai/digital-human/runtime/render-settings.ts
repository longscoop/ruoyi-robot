import type { RenderSettings } from '@/api/ai/digital-human'

export const defaultRenderSettings = (): RenderSettings => ({
  provider: 'BUILTIN',
  service: 'default',
  avatarId: ''
})

export function readRenderSettings(json?: string): RenderSettings {
  const config = json ? JSON.parse(json) : {}
  if (!config || typeof config !== 'object' || Array.isArray(config))
    throw Error('数字人配置格式无效')
  return { ...defaultRenderSettings(), ...config.rendering }
}

export function writeRenderSettings(json: string | undefined, rendering: RenderSettings): string {
  const config = json ? JSON.parse(json) : {}
  if (!config || typeof config !== 'object' || Array.isArray(config))
    throw Error('数字人配置格式无效')
  return JSON.stringify({
    ...config,
    rendering: { ...rendering, avatarId: rendering.avatarId.trim() }
  })
}
