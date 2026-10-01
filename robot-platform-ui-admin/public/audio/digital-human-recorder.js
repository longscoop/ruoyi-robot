/* Batches microphone PCM without playing it back to the speakers. */
class DigitalHumanRecorder extends AudioWorkletProcessor {
  constructor() {
    super()
    this.buffer = new Float32Array(2048)
    this.offset = 0
    this.port.onmessage = ({ data }) => {
      if (data === 'flush') {
        if (this.offset) {
          const tail = this.buffer.slice(0, this.offset)
          this.port.postMessage(tail, [tail.buffer])
          this.offset = 0
        }
        this.port.postMessage('flushed')
      }
    }
  }
  process(inputs) {
    const input = inputs[0]?.[0]
    if (input) for (const value of input) {
      this.buffer[this.offset++] = value
      if (this.offset === this.buffer.length) {
        this.port.postMessage(this.buffer, [this.buffer.buffer])
        this.buffer = new Float32Array(2048)
        this.offset = 0
      }
    }
    return true
  }
}
registerProcessor('digital-human-recorder', DigitalHumanRecorder)
