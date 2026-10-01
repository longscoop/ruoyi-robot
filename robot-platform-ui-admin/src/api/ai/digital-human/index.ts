import request from '@/config/axios'
export type AvatarType = 'STATIC_2D' | 'LIVE2D' | 'THREE_D' | 'EXTERNAL'
export type LipSyncMode = 'AUDIO_LEVEL' | 'VISEME' | 'PROVIDER'
export type RenderProvider = 'BUILTIN' | 'LIVETALKING'
export interface RenderSettings {
  provider: RenderProvider
  service: string
  avatarId: string
}
export interface RenderClientConfig {
  provider: RenderProvider
  iceServers: RTCIceServer[]
}
export interface RenderServiceOption {
  id: string
  name: string
  provider: RenderProvider
}
export interface RenderAnswer {
  sessionId: string
  type: 'answer'
  sdp: string
}
export type DigitalHumanState =
  | 'IDLE'
  | 'LISTENING'
  | 'THINKING'
  | 'SPEAKING'
  | 'EXECUTING'
  | 'ERROR'
export interface DigitalHumanVO {
  id: number
  name: string
  code: string
  description?: string
  agentId: number
  avatarType: AvatarType
  avatarUrl?: string
  avatarResourceUrl?: string
  coverUrl?: string
  voiceModelId?: number | null
  voiceId?: string | null
  speechRate?: number
  pitch?: number
  volume?: number
  lipSyncMode: LipSyncMode
  welcomeText?: string
  interruptEnabled: boolean
  configJson?: string
  status: string
}
export interface DigitalHumanActionVO {
  id?: number
  state: DigitalHumanState
  actionCode: string
  configJson?: string
}
export interface DigitalHumanPreviewVO {
  id: number
  code: string
  agentId: number
  avatarType: AvatarType
  avatarUrl?: string
  avatarResourceUrl?: string
  voiceModelId?: number | null
  voiceId?: string | null
  lipSyncMode: LipSyncMode
  welcomeText?: string
  interruptEnabled: boolean
  rendering?: RenderClientConfig
}
export const DigitalHumanApi = {
  openDialogue: (id: number) =>
    request.post<string>({ url: `/ai/digital-humans/${id}/dialogue-sessions`, timeout: 60000 }),
  interruptDialogue: (id: number, session: string) =>
    request.post({ url: `/ai/digital-humans/${id}/dialogue-sessions/${session}/interrupt` }),
  keepDialogue: (id: number, session: string) =>
    request.post({ url: `/ai/digital-humans/${id}/dialogue-sessions/${session}/keepalive` }),
  closeDialogue: (id: number, session: string) =>
    request.delete({ url: `/ai/digital-humans/${id}/dialogue-sessions/${session}` }),
  renderServices: () =>
    request.get<RenderServiceOption[]>({ url: '/ai/digital-humans/render-services' }),
  renderOffer: (id: number, sdp: string) =>
    request.post<RenderAnswer>({
      url: `/ai/digital-humans/${id}/render-sessions`,
      data: { sdp },
      timeout: 120000
    }),
  renderSpeak: (id: number, sessionId: string, text: string) =>
    request.post({
      url: `/ai/digital-humans/${id}/render-sessions/${sessionId}/speak`,
      data: { text }
    }),
  renderInterrupt: (id: number, sessionId: string) =>
    request.post({ url: `/ai/digital-humans/${id}/render-sessions/${sessionId}/interrupt` }),
  renderSpeaking: (id: number, sessionId: string) =>
    request.get<boolean>({ url: `/ai/digital-humans/${id}/render-sessions/${sessionId}/speaking` }),
  renderClose: (id: number, sessionId: string) =>
    request.delete({ url: `/ai/digital-humans/${id}/render-sessions/${sessionId}` }),
  list: () => request.get<DigitalHumanVO[]>({ url: '/ai/digital-humans' }),
  get: (id: number) => request.get<DigitalHumanVO>({ url: `/ai/digital-humans/${id}` }),
  create: (data: Omit<DigitalHumanVO, 'id'>) =>
    request.post<number>({ url: '/ai/digital-humans', data }),
  update: (id: number, data: Omit<DigitalHumanVO, 'id'>) =>
    request.put({ url: `/ai/digital-humans/${id}`, data }),
  delete: (id: number) => request.delete({ url: `/ai/digital-humans/${id}` }),
  updateActions: (id: number, data: DigitalHumanActionVO[]) =>
    request.put({ url: `/ai/digital-humans/${id}/actions`, data }),
  preview: (id: number) =>
    request.post<DigitalHumanPreviewVO>({ url: `/ai/digital-humans/${id}/preview-session` })
}
