/** Average source samples into 16 kHz PCM; all chunks share one resampling timeline. */
export function microphonePcm(chunks: Float32Array[], sourceRate: number): Uint8Array {
  const length = chunks.reduce((total, chunk) => total + chunk.length, 0)
  const samples = new Float32Array(length)
  let offset = 0
  for (const chunk of chunks) {
    samples.set(chunk, offset)
    offset += chunk.length
  }
  const ratio = sourceRate / 16000
  const result = new Uint8Array(Math.floor(length / ratio) * 2)
  const view = new DataView(result.buffer)
  for (let i = 0; i < result.length / 2; i++) {
    const start = Math.floor(i * ratio),
      end = Math.min(length, Math.max(start + 1, Math.floor((i + 1) * ratio)))
    let sum = 0
    for (let j = start; j < end; j++) sum += samples[j]
    const value = Math.max(-1, Math.min(1, sum / (end - start)))
    view.setInt16(i * 2, Math.round(value * (value < 0 ? 32768 : 32767)), true)
  }
  return result
}
export function audioBase64(bytes: Uint8Array) {
  let binary = ''
  for (let i = 0; i < bytes.length; i += 8192)
    binary += String.fromCharCode(...bytes.subarray(i, i + 8192))
  return btoa(binary)
}

export class DialogueAudio {
  private context?: AudioContext
  private recorderLoaded = false
  private stream?: MediaStream
  private source?: MediaStreamAudioSourceNode
  private recorder?: AudioWorkletNode
  private mute?: GainNode
  private chunks: Float32Array[] = []
  private samples = 0
  private recording = false
  private version = 0
  private nextPlay = 0
  private sources = new Set<AudioBufferSourceNode>()
  private analyser?: AnalyserNode
  private frame = 0
  private pendingByte?: number
  private pendingFlush?: () => void
  onLimit = () => {}
  constructor(private onLevel: (level: number, playing: boolean) => void) {}

  async prepare() {
    if (!this.context || this.context.state === 'closed') {
      this.context = new AudioContext()
      this.recorderLoaded = false
      this.analyser = this.context.createAnalyser()
      this.analyser.fftSize = 256
      this.analyser.connect(this.context.destination)
    }
    await this.context.resume()
  }

  async startRecording() {
    if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia)
      throw Error('麦克风需要 HTTPS 或 localhost，请使用安全地址打开后台')
    const version = ++this.version
    await this.prepare()
    if (version !== this.version) return false
    const stream = await navigator.mediaDevices.getUserMedia({
      audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true }
    })
    if (version !== this.version) {
      stream.getTracks().forEach((track) => track.stop())
      return false
    }
    this.stream = stream
    try {
      if (!this.recorderLoaded) {
        await this.context!.audioWorklet.addModule(
          `${import.meta.env.BASE_URL}audio/digital-human-recorder.js`
        )
        if (version === this.version) this.recorderLoaded = true
      }
      if (version !== this.version) return false
      const context = this.context!
      this.chunks = []
      this.samples = 0
      this.recording = true
      this.source = context.createMediaStreamSource(stream)
      this.recorder = new AudioWorkletNode(context, 'digital-human-recorder')
      this.mute = context.createGain()
      this.mute.gain.value = 0
      this.source.connect(this.recorder)
      this.recorder.connect(this.mute)
      this.mute.connect(context.destination)
      this.recorder.port.onmessage = ({ data }) => {
        if (data === 'flushed') {
          this.pendingFlush?.()
          return
        }
        if (!this.recording || version !== this.version) return
        const remaining = context.sampleRate * 30 - this.samples
        const chunk = (data as Float32Array).slice(0, Math.max(0, remaining))
        this.chunks.push(chunk)
        this.samples += chunk.length
        let sum = 0
        for (const value of chunk) sum += value * value
        this.onLevel(Math.min(1, Math.sqrt(sum / Math.max(1, chunk.length)) * 5), false)
        if (this.samples >= context.sampleRate * 30) this.onLimit()
      }
      return true
    } catch (error) {
      this.releaseMicrophone()
      throw error
    }
  }

  async finishRecording() {
    if (!this.recording || !this.context) return new Uint8Array()
    const version = this.version
    // Stop producing new frames before flushing the worklet's partial buffer.
    this.source?.disconnect()
    await new Promise<void>((resolve) => {
      const timer = setTimeout(resolve, 250)
      this.pendingFlush = () => {
        clearTimeout(timer)
        resolve()
      }
      this.recorder?.port.postMessage('flush')
    })
    this.pendingFlush = undefined
    if (version !== this.version) return new Uint8Array()
    const bytes = microphonePcm(this.chunks, this.context.sampleRate)
    this.releaseMicrophone()
    return bytes
  }

  private releaseMicrophone() {
    this.recording = false
    this.stream?.getTracks().forEach((track) => track.stop())
    this.stream = undefined
    this.source?.disconnect()
    this.source = undefined
    this.recorder?.disconnect()
    this.recorder = undefined
    this.mute?.disconnect()
    this.mute = undefined
    this.chunks = []
    this.samples = 0
    this.onLevel(0, false)
  }

  play(base64: string, sampleRate = 24000) {
    if (!this.context || !this.analyser || this.context.state === 'closed') return
    if (sampleRate !== 24000) throw Error('不支持的回答音频格式')
    const binary = atob(base64)
    const bytes = new Uint8Array(binary.length + (this.pendingByte === undefined ? 0 : 1))
    let offset = 0
    if (this.pendingByte !== undefined) bytes[offset++] = this.pendingByte
    for (let i = 0; i < binary.length; i++) bytes[offset + i] = binary.charCodeAt(i)
    this.pendingByte = bytes.length % 2 ? bytes[bytes.length - 1] : undefined
    const count = Math.floor(bytes.length / 2)
    if (!count) return
    const buffer = this.context.createBuffer(1, count, sampleRate)
    const channel = buffer.getChannelData(0),
      view = new DataView(bytes.buffer)
    for (let i = 0; i < count; i++) channel[i] = view.getInt16(i * 2, true) / 32768
    const source = this.context.createBufferSource()
    source.buffer = buffer
    source.connect(this.analyser)
    this.nextPlay = Math.max(this.nextPlay, this.context.currentTime + 0.02)
    if (this.nextPlay - this.context.currentTime > 120)
      throw Error('回答音频积压过多，请打断后重试')
    this.sources.add(source)
    source.onended = () => {
      source.disconnect()
      this.sources.delete(source)
      if (!this.sources.size) {
        cancelAnimationFrame(this.frame)
        this.frame = 0
        this.onLevel(0, false)
      }
    }
    source.start(this.nextPlay)
    this.nextPlay += buffer.duration
    if (!this.frame) this.measure()
  }

  private measure() {
    if (!this.analyser || !this.sources.size) {
      this.frame = 0
      return
    }
    const data = new Float32Array(this.analyser.fftSize)
    this.analyser.getFloatTimeDomainData(data)
    let sum = 0
    for (const value of data) sum += value * value
    this.onLevel(Math.min(1, Math.sqrt(sum / data.length) * 6), true)
    this.frame = requestAnimationFrame(() => this.measure())
  }

  stopPlayback() {
    for (const source of this.sources) {
      source.onended = null
      try {
        source.stop()
      } catch {
        /* already ended */
      }
      source.disconnect()
    }
    this.sources.clear()
    this.nextPlay = 0
    this.pendingByte = undefined
    cancelAnimationFrame(this.frame)
    this.frame = 0
    this.onLevel(0, false)
  }
  dispose() {
    this.version++
    this.pendingFlush?.()
    this.pendingFlush = undefined
    this.releaseMicrophone()
    this.stopPlayback()
    const context = this.context
    this.context = undefined
    this.analyser = undefined
    if (context && context.state !== 'closed') void context.close().catch(() => {})
  }
}
