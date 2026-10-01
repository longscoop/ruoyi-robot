import { afterEach, describe, expect, it, vi } from 'vitest'
import { audioBase64, DialogueAudio, microphonePcm } from './dialogue-audio'
vi.mock('@/utils/auth', () => ({
  getAccessToken: () => 'test-token',
  getTenantId: () => 1,
  getVisitTenantId: () => undefined
}))
vi.mock('@/config/axios/config', () => ({ config: { base_url: '/admin-api' } }))
import { DialogueEventDecoder, streamDialogue } from './dialogue-stream'

afterEach(() => vi.unstubAllGlobals())

describe('microphone PCM', () => {
  it('resamples across chunk boundaries with little-endian signed samples', () => {
    const pcm = microphonePcm([new Float32Array([1, 1]), new Float32Array([1, -1, -1, -1])], 48000)
    expect([...pcm]).toEqual([255, 127, 0, 128])
    expect(audioBase64(pcm)).toBe('/38AgA==')
  })
  it('preserves duration for 44.1 kHz microphones and clips out-of-range input', () => {
    expect(microphonePcm([new Float32Array(44100)], 44100).length).toBe(32000)
    expect([...microphonePcm([new Float32Array([2, -2])], 16000)]).toEqual([255, 127, 0, 128])
  })
})

describe('dialogue SSE', () => {
  it('handles fragmented CRLF, multiple events and Unicode', () => {
    const receive = vi.fn(),
      decoder = new DialogueEventDecoder(receive)
    const data =
      ': heartbeat\r\ndata: {"type":"assistant.delta","text":"你好"}\r\n\r\ndata:{"type":"done"}\n\n'
    for (const char of data) decoder.push(char)
    expect(receive.mock.calls.map(([event]) => event)).toEqual([
      { type: 'assistant.delta', text: '你好' },
      { type: 'done' }
    ])
  })
  it('rejects a truncated response instead of leaving the character thinking', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(
          new Response('data:{"type":"thinking"}\n\n', {
            headers: { 'content-type': 'text/event-stream' }
          })
        )
    )
    await expect(
      streamDialogue(42, 'session', '', new AbortController().signal, vi.fn())
    ).rejects.toThrow('连接中断')
  })
  it('sends authenticated audio and accepts terminal events', async () => {
    const fetch = vi
      .fn()
      .mockResolvedValue(
        new Response('data:{"type":"done"}\n\n', {
          headers: { 'content-type': 'text/event-stream' }
        })
      )
    vi.stubGlobal('fetch', fetch)
    await streamDialogue(42, 'session', 'AQI=', new AbortController().signal, vi.fn())
    expect(fetch).toHaveBeenCalledWith(
      '/admin-api/ai/digital-humans/42/dialogue-sessions/session/turns',
      expect.objectContaining({
        body: '{"pcm":"AQI="}',
        headers: expect.objectContaining({ Authorization: 'Bearer test-token' })
      })
    )
  })
})

describe('audio lifecycle', () => {
  function setupAudio() {
    const sources: any[] = []
    const buffers: Float32Array[] = []
    const context = {
      state: 'running',
      currentTime: 1,
      destination: {},
      resume: vi.fn(),
      close: vi.fn().mockResolvedValue(undefined),
      createAnalyser: () => ({
        fftSize: 256,
        connect: vi.fn(),
        getFloatTimeDomainData: (data: Float32Array) => data.fill(0.1)
      }),
      createBuffer: (_channels: number, count: number, rate: number) => {
        const data = new Float32Array(count)
        buffers.push(data)
        return { duration: count / rate, getChannelData: () => data }
      },
      createBufferSource: () => {
        const source = {
          connect: vi.fn(),
          disconnect: vi.fn(),
          start: vi.fn(),
          stop: vi.fn(),
          onended: null
        }
        sources.push(source)
        return source
      }
    }
    vi.stubGlobal(
      'AudioContext',
      class {
        constructor() {
          return context
        }
      }
    )
    vi.stubGlobal('requestAnimationFrame', vi.fn().mockReturnValue(1))
    vi.stubGlobal('cancelAnimationFrame', vi.fn())
    return { context, sources, buffers }
  }
  it('schedules PCM in order, carries partial samples, and stops queued sound immediately', async () => {
    const { sources, buffers } = setupAudio(),
      level = vi.fn(),
      audio = new DialogueAudio(level)
    await audio.prepare()
    audio.play(audioBase64(new Uint8Array([255])))
    expect(sources).toHaveLength(0)
    audio.play(audioBase64(new Uint8Array([127, 0, 128])))
    audio.play(audioBase64(new Uint8Array([0, 0])))
    expect([...buffers[0]]).toEqual([32767 / 32768, -1])
    expect(sources[1].start.mock.calls[0][0]).toBeCloseTo(1.02 + 2 / 24000, 8)
    audio.stopPlayback()
    expect(sources.every((source) => source.stop.mock.calls.length === 1)).toBe(true)
    expect(level).toHaveBeenLastCalledWith(0, false)
    audio.dispose()
  })
  it('stops a microphone permission result that arrives after closing preview', async () => {
    setupAudio()
    let resolve!: (stream: any) => void
    const getUserMedia = vi.fn(
      () =>
        new Promise((done) => {
          resolve = done
        })
    )
    vi.stubGlobal('window', { isSecureContext: true })
    vi.stubGlobal('navigator', { mediaDevices: { getUserMedia } })
    const audio = new DialogueAudio(vi.fn()),
      stopped = vi.fn()
    const pending = audio.startRecording()
    await vi.waitFor(() => expect(getUserMedia).toHaveBeenCalled())
    audio.dispose()
    resolve({ getTracks: () => [{ stop: stopped }] })
    expect(await pending).toBe(false)
    expect(stopped).toHaveBeenCalledOnce()
  })
})
