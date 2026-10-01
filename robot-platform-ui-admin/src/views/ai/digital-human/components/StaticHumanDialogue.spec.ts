// @vitest-environment jsdom
import { mount, flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
const mocks = vi.hoisted(() => ({
  open: vi.fn(),
  close: vi.fn(),
  interrupt: vi.fn(),
  keep: vi.fn(),
  prepare: vi.fn(),
  start: vi.fn(),
  finish: vi.fn(),
  dispose: vi.fn(),
  stop: vi.fn(),
  play: vi.fn(),
  stream: vi.fn()
}))
vi.mock('@/api/ai/digital-human', () => ({
  DigitalHumanApi: {
    openDialogue: mocks.open,
    closeDialogue: mocks.close,
    interruptDialogue: mocks.interrupt,
    keepDialogue: mocks.keep
  }
}))
vi.mock('../runtime/dialogue-audio', () => ({
  audioBase64: () => 'pcm',
  DialogueAudio: class {
    prepare = mocks.prepare
    startRecording = mocks.start
    finishRecording = mocks.finish
    dispose = mocks.dispose
    stopPlayback = mocks.stop
    play = mocks.play
  }
}))
vi.mock('../runtime/dialogue-stream', () => ({ streamDialogue: mocks.stream }))
import StaticHumanDialogue from './StaticHumanDialogue.vue'
const Button = defineComponent({
  props: { disabled: Boolean },
  template: '<button :disabled="disabled"><slot /></button>'
})
let wrapper: ReturnType<typeof mount>
beforeEach(() => {
  vi.clearAllMocks()
  mocks.open.mockResolvedValue('session')
  mocks.close.mockResolvedValue(true)
  mocks.interrupt.mockResolvedValue(true)
  mocks.prepare.mockResolvedValue(undefined)
  mocks.start.mockResolvedValue(true)
  mocks.finish.mockResolvedValue(new Uint8Array(6400))
  wrapper = mount(StaticHumanDialogue, {
    props: { config: { id: 42, interruptEnabled: true } as any },
    global: { stubs: { StaticAvatarStage: true, 'el-button': Button, 'el-alert': true } }
  })
})
afterEach(() => wrapper.unmount())
async function click(text: string) {
  await wrapper
    .findAll('button')
    .find((button) => button.text().includes(text))!
    .trigger('click')
  await flushPromises()
}
it('streams reactive subtitles and audio using the bound session', async () => {
  mocks.stream.mockImplementation(async (_id, _session, _pcm, _signal, receive) => {
    receive({ type: 'user.done', text: '你好' })
    receive({ type: 'assistant.delta', text: '你好呀' })
    receive({ type: 'audio', pcm: 'AQI=', sampleRate: 24000 })
    receive({ type: 'done' })
  })
  await click('开始对话')
  await click('开始说话')
  await click('发送录音')
  expect(mocks.open).toHaveBeenCalledWith(42)
  expect(wrapper.text()).toContain('你好呀')
  expect(mocks.play).toHaveBeenCalledWith('AQI=', 24000)
  await click('结束对话')
  expect(mocks.close).toHaveBeenCalledWith(42, 'session')
  expect(mocks.dispose).toHaveBeenCalled()
})
it('closes a connection arriving after dismissal', async () => {
  let resolve!: (value: string) => void
  mocks.open.mockImplementation(
    () =>
      new Promise((done) => {
        resolve = done
      })
  )
  await click('开始对话')
  wrapper.unmount()
  resolve('late-session')
  await flushPromises()
  expect(mocks.close).toHaveBeenCalledWith(42, 'late-session')
})
it('interrupts response before the next recording', async () => {
  let signal!: AbortSignal
  mocks.stream.mockImplementation((_id, _session, _pcm, abort) => {
    signal = abort
    return new Promise<void>((resolve) => abort.addEventListener('abort', () => resolve()))
  })
  await click('开始对话')
  await click('开始说话')
  await click('发送录音')
  await click('打断并说话')
  expect(signal.aborted).toBe(true)
  expect(mocks.interrupt).toHaveBeenCalledWith(42, 'session')
  expect(mocks.start).toHaveBeenCalledTimes(2)
  expect(wrapper.text()).toContain('发送录音')
})
it('explains microphone denial and releases the session', async () => {
  mocks.start.mockRejectedValue(new DOMException('Permission denied', 'NotAllowedError'))
  await click('开始对话')
  await click('开始说话')
  expect(wrapper.find('el-alert-stub').attributes('title')).toContain('麦克风权限未开启')
  expect(mocks.close).toHaveBeenCalledWith(42, 'session')
})

it('replaces pending subtitles when the voice service fails', async () => {
  mocks.stream.mockImplementation(async (_id, _session, _pcm, _signal, receive) => {
    receive({ type: 'error', message: '录音提交到语音服务失败，请重新开始对话' })
  })
  await click('开始对话')
  await click('开始说话')
  await click('发送录音')
  expect(wrapper.text()).not.toContain('正在回答…')
  expect(wrapper.text()).not.toContain('等待识别…')
  expect(wrapper.text()).toContain('本轮回答失败')
  expect(mocks.close).toHaveBeenCalledWith(42, 'session')
})
it('replaces cumulative ASR partial text instead of duplicating it', async () => {
  mocks.stream.mockImplementation(async (_id, _session, _pcm, _signal, receive) => {
    receive({ type: 'user.delta', text: '你' })
    receive({ type: 'user.delta', text: '你好' })
    receive({ type: 'done' })
  })
  await click('开始对话')
  await click('开始说话')
  await click('发送录音')
  expect(wrapper.find('.message.user p').text()).toBe('你好')
})
