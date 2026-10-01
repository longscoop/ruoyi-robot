import { config } from '@/config/axios/config'
import { getAccessToken, getTenantId, getVisitTenantId } from '@/utils/auth'

export interface DialogueEvent {
  type: string
  text?: string
  pcm?: string
  sampleRate?: number
  message?: string
}

/** Handles SSE boundaries split across arbitrary HTTP chunks, including CRLF. */
export class DialogueEventDecoder {
  private buffer = ''
  constructor(private receive: (event: DialogueEvent) => void) {}
  push(text: string) {
    this.buffer += text
    if (this.buffer.length > 2_000_000) throw Error('对话事件过大')
    let match: RegExpExecArray | null
    while ((match = /\r?\n\r?\n/.exec(this.buffer))) {
      const block = this.buffer.slice(0, match.index)
      this.buffer = this.buffer.slice(match.index + match[0].length)
      const data = block
        .split(/\r?\n/)
        .filter((line) => line.startsWith('data:'))
        .map((line) => line.slice(5).trimStart())
        .join('\n')
      if (data) this.receive(JSON.parse(data))
    }
  }
}

export async function streamDialogue(
  id: number,
  session: string,
  pcm: string,
  signal: AbortSignal,
  receive: (event: DialogueEvent) => void
) {
  const tenant = getTenantId(),
    visitTenant = getVisitTenantId()
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Accept: 'text/event-stream',
    Authorization: `Bearer ${getAccessToken()}`
  }
  if (import.meta.env.VITE_APP_TENANT_ENABLE === 'true') {
    if (tenant) headers['tenant-id'] = String(tenant)
    if (visitTenant) headers['visit-tenant-id'] = String(visitTenant)
  }
  const response = await fetch(
    `${config.base_url}/ai/digital-humans/${id}/dialogue-sessions/${encodeURIComponent(session)}/turns`,
    {
      method: 'POST',
      headers,
      body: JSON.stringify({ pcm }),
      signal
    }
  )
  if (!response.ok || !response.headers.get('content-type')?.includes('text/event-stream')) {
    const error = await response.json().catch(() => ({}))
    throw Error(error.msg || '对话请求失败，请重新开始或检查登录状态')
  }
  if (!response.body) throw Error('浏览器不支持流式对话')
  let complete = false
  const decoder = new DialogueEventDecoder((event) => {
    if (['done', 'interrupted', 'error'].includes(event.type)) complete = true
    receive(event)
  })
  const reader = response.body.getReader(),
    utf8 = new TextDecoder()
  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) {
        decoder.push(utf8.decode())
        break
      }
      decoder.push(utf8.decode(value, { stream: true }))
    }
    if (!complete && !signal.aborted) throw Error('对话连接中断，请重新开始')
  } finally {
    await reader.cancel().catch(() => {})
    reader.releaseLock()
  }
}
