import { afterEach, describe, expect, it, vi } from 'vitest'
import { createDigitalHumanRenderer, LiveTalkingRenderer } from './LiveTalkingRenderer'
import { Static2DRenderer } from './DigitalHumanRenderer'
import type { DigitalHumanPreviewVO, RenderAnswer } from '@/api/ai/digital-human'
import { readRenderSettings, writeRenderSettings } from './render-settings'

class Peer extends EventTarget {
  iceGatheringState = 'complete'
  connectionState = 'new'
  localDescription = { type: 'offer', sdp: 'v=0\r\noffer' }
  addTransceiver = vi.fn()
  createOffer = vi.fn(async () => this.localDescription)
  setLocalDescription = vi.fn(async () => {})
  setRemoteDescription = vi.fn(async () => {
    this.connectionState = 'connected'
  })
  close = vi.fn(() => {
    this.connectionState = 'closed'
  })
}
function fixture() {
  const peer = new Peer()
  const tracks: Array<{ stop: () => void }> = []
  vi.stubGlobal(
    'RTCPeerConnection',
    vi.fn(function () {
      return peer
    })
  )
  vi.stubGlobal(
    'MediaStream',
    vi.fn(function () {
      return { addTrack: vi.fn(), getTracks: () => tracks }
    })
  )
  const video = { srcObject: null, play: vi.fn(async () => {}) } as unknown as HTMLVideoElement
  const signaling = {
    offer: vi.fn(
      async (): Promise<RenderAnswer> => ({
        sessionId: 'private-handle',
        type: 'answer',
        sdp: 'v=0\r\nanswer'
      })
    ),
    close: vi.fn(async () => {})
  }
  const config: DigitalHumanPreviewVO = {
    id: 1,
    code: 'human',
    agentId: 1,
    avatarType: 'EXTERNAL',
    lipSyncMode: 'PROVIDER',
    interruptEnabled: true,
    rendering: { provider: 'LIVETALKING', iceServers: [] }
  }
  const renderer = createDigitalHumanRenderer(
    config,
    video,
    signaling,
    vi.fn()
  ) as LiveTalkingRenderer
  return { renderer, peer, tracks, video, signaling, config }
}
afterEach(() => {
  vi.unstubAllGlobals()
  vi.useRealTimers()
})

describe('switchable digital human renderer', () => {
  it('keeps old settings and unrelated JSON when switching providers', () => {
    expect(readRenderSettings(undefined).provider).toBe('BUILTIN')
    const json = writeRenderSettings('{"custom":{"value":1}}', {
      provider: 'LIVETALKING',
      service: 'musetalk',
      avatarId: ' avatar_1 '
    })
    expect(JSON.parse(json)).toEqual({
      custom: { value: 1 },
      rendering: { provider: 'LIVETALKING', service: 'musetalk', avatarId: 'avatar_1' }
    })
    expect(readRenderSettings(json).service).toBe('musetalk')
    expect(() => writeRenderSettings('[]', readRenderSettings())).toThrow()
  })
  it('defaults to the static renderer for existing configurations', () => {
    const { video, signaling } = fixture()
    const renderer = createDigitalHumanRenderer(
      { id: 1 } as DigitalHumanPreviewVO,
      video,
      signaling,
      vi.fn()
    )
    expect(renderer).toBeInstanceOf(Static2DRenderer)
    expect(renderer).not.toBeInstanceOf(LiveTalkingRenderer)
    expect(signaling.offer).not.toHaveBeenCalled()
  })
  it('negotiates receive-only audio/video and closes both local and remote resources', async () => {
    const { renderer, peer, video, signaling, tracks } = fixture()
    await renderer.connect()
    expect(peer.addTransceiver.mock.calls).toEqual([
      ['audio', { direction: 'recvonly' }],
      ['video', { direction: 'recvonly' }]
    ])
    expect(signaling.offer).toHaveBeenCalledWith('v=0\r\noffer')
    expect(peer.setRemoteDescription).toHaveBeenCalledWith({ type: 'answer', sdp: 'v=0\r\nanswer' })
    expect(renderer.sessionId).toBe('private-handle')
    const track = { stop: vi.fn() }
    tracks.push(track)
    renderer.dispose()
    expect(peer.close).toHaveBeenCalled()
    expect(track.stop).toHaveBeenCalled()
    expect(video.srcObject).toBeNull()
    expect(signaling.close).toHaveBeenCalledWith('private-handle')
  })
  it('cleans up late answers after the dialog was closed', async () => {
    const { renderer, peer, signaling } = fixture()
    let resolve!: (answer: RenderAnswer) => void
    signaling.offer.mockImplementation(
      () =>
        new Promise((done) => {
          resolve = done
        })
    )
    const connecting = renderer.connect()
    await vi.waitFor(() => expect(signaling.offer).toHaveBeenCalled())
    renderer.dispose()
    resolve({ sessionId: 'late', type: 'answer', sdp: 'v=0 answer' })
    await connecting
    expect(signaling.close).toHaveBeenCalledWith('late')
    expect(peer.setRemoteDescription).not.toHaveBeenCalled()
    expect(renderer.sessionId).toBeUndefined()
  })
  it('times out ICE gathering instead of leaving a pending connection', async () => {
    vi.useFakeTimers()
    const { renderer, peer, signaling } = fixture()
    peer.iceGatheringState = 'gathering'
    const result = expect(renderer.connect()).rejects.toThrow('超时')
    await vi.advanceTimersByTimeAsync(15000)
    await result
    expect(peer.close).toHaveBeenCalled()
    expect(signaling.offer).not.toHaveBeenCalled()
  })
})
