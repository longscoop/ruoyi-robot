import type { DigitalHumanPreviewVO, RenderAnswer } from '@/api/ai/digital-human'
import { Static2DRenderer } from './DigitalHumanRenderer'

/** Signaling can use the admin REST API or digital_human.offer/answer on the device WebSocket. */
export interface RenderSignaling {
  offer(sdp: string): Promise<RenderAnswer>
  close(sessionId: string): Promise<unknown>
}

export class LiveTalkingRenderer extends Static2DRenderer {
  private peer?: RTCPeerConnection
  private stream?: MediaStream
  private abort?: AbortController
  sessionId?: string

  constructor(
    private video: HTMLVideoElement,
    private signaling: RenderSignaling,
    private onError: (message: string) => void
  ) {
    super()
  }

  async connect() {
    this.dispose()
    const abort = new AbortController()
    this.abort = abort
    const peer = new RTCPeerConnection({ iceServers: this.config?.rendering?.iceServers || [] })
    this.peer = peer
    const stream = new MediaStream()
    this.stream = stream
    this.video.srcObject = stream
    peer.ontrack = ({ track }) => {
      if (abort.signal.aborted) {
        track.stop()
        return
      }
      stream.addTrack(track)
      // Native controls remain available when autoplay with sound requires another user gesture.
      void this.video.play().catch(() => {})
    }
    peer.onconnectionstatechange = () => {
      if (peer.connectionState === 'failed' && !abort.signal.aborted) {
        this.dispose()
        this.onError('数字人视频连接失败，请检查网络或 TURN 配置后重新连接')
      }
    }
    peer.addTransceiver('audio', { direction: 'recvonly' })
    peer.addTransceiver('video', { direction: 'recvonly' })
    try {
      await peer.setLocalDescription(await peer.createOffer())
      await waitForIce(peer, abort.signal)
      if (abort.signal.aborted) return
      const answer = await this.signaling.offer(peer.localDescription!.sdp)
      if (abort.signal.aborted) {
        void this.signaling.close(answer.sessionId).catch(() => {})
        return
      }
      this.sessionId = answer.sessionId
      await peer.setRemoteDescription({ type: 'answer', sdp: answer.sdp })
      await waitForConnection(peer, abort.signal)
    } catch (error) {
      if (!abort.signal.aborted) {
        this.dispose()
        throw error
      }
    }
  }

  dispose() {
    this.abort?.abort()
    this.peer?.close()
    this.peer = undefined
    this.stream?.getTracks().forEach((track) => track.stop())
    this.stream = undefined
    this.video.srcObject = null
    if (this.sessionId) void this.signaling.close(this.sessionId).catch(() => {})
    this.sessionId = undefined
    this.reset()
  }
}

function waitForIce(peer: RTCPeerConnection, signal: AbortSignal) {
  return waitForPeer(
    peer,
    'icegatheringstatechange',
    () => peer.iceGatheringState === 'complete',
    signal
  )
}

function waitForConnection(peer: RTCPeerConnection, signal: AbortSignal) {
  return waitForPeer(
    peer,
    'connectionstatechange',
    () => peer.connectionState === 'connected',
    signal
  )
}

function waitForPeer(
  peer: RTCPeerConnection,
  event: string,
  ready: () => boolean,
  signal: AbortSignal
) {
  return new Promise<void>((resolve, reject) => {
    const finish = (error?: Error) => {
      clearTimeout(timer)
      peer.removeEventListener(event, check)
      signal.removeEventListener('abort', cancel)
      error ? reject(error) : resolve()
    }
    const check = () => {
      if (ready()) finish()
    }
    const cancel = () => finish(new Error('连接已取消'))
    const timer = setTimeout(
      () => finish(new Error('数字人连接超时，请检查网络和 ICE 配置')),
      15000
    )
    peer.addEventListener(event, check)
    signal.addEventListener('abort', cancel, { once: true })
    signal.aborted ? cancel() : check()
  })
}

export function createDigitalHumanRenderer(
  config: DigitalHumanPreviewVO,
  video: HTMLVideoElement,
  signaling: RenderSignaling,
  onError: (message: string) => void
): Static2DRenderer | LiveTalkingRenderer {
  const provider = config.rendering?.provider || 'BUILTIN'
  if (provider !== 'BUILTIN' && provider !== 'LIVETALKING')
    throw Error(`不支持的数字人渲染服务：${provider}`)
  const renderer =
    provider === 'LIVETALKING'
      ? new LiveTalkingRenderer(video, signaling, onError)
      : new Static2DRenderer()
  renderer.load(config)
  return renderer
}
