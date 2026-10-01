<template>
  <StaticAvatarStage :avatar-url="config.avatarUrl" :state="state" :level="level" />
  <el-alert v-if="error" class="mt-3" :title="error" type="error" :closable="false" />
  <p class="dialogue-hint">{{
    session
      ? '点击“开始说话”，说完后发送录音。每次最长 30 秒。'
      : '使用绑定的智能体和音色进行语音对话。请先开始对话。'
  }}</p>
  <div class="dialogue-controls">
    <el-button v-if="!session" type="primary" :loading="connecting" @click="connect"
      >开始对话</el-button
    >
    <el-button
      v-if="session && !recording"
      type="primary"
      :loading="micPending"
      :disabled="sending || interrupting || ((responding || playing) && !config.interruptEnabled)"
      @click="record"
      >{{ responding || playing ? '打断并说话' : '开始说话' }}</el-button
    >
    <el-button v-if="recording" type="primary" :loading="sending" @click="send"
      >发送录音（{{ seconds }}s）</el-button
    >
    <el-button
      v-if="session"
      :disabled="(!responding && !playing) || interrupting || !config.interruptEnabled"
      @click="interrupt"
      >打断回答</el-button
    >
    <el-button v-if="session || connecting" @click="stop">结束对话</el-button>
  </div>
  <div class="subtitles" aria-live="polite">
    <p v-if="!messages.length" class="empty-subtitle">{{
      config.welcomeText || '你好，准备好就开始聊天吧。'
    }}</p>
    <div v-for="(message, index) in messages" :key="index" class="message" :class="message.role">
      <span>{{ message.role === 'user' ? '你' : '数字人' }}</span>
      <p>{{ message.text || (message.role === 'user' ? '语音已发送，等待识别…' : '正在回答…') }}</p>
    </div>
  </div>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import {
  DigitalHumanApi,
  type DigitalHumanPreviewVO,
  type DigitalHumanState
} from '@/api/ai/digital-human'
import { DialogueAudio, audioBase64 } from '../runtime/dialogue-audio'
import { streamDialogue, type DialogueEvent } from '../runtime/dialogue-stream'
import StaticAvatarStage from './StaticAvatarStage.vue'
const props = defineProps<{ config: DigitalHumanPreviewVO }>()
const session = ref(''),
  error = ref(''),
  level = ref(0),
  seconds = ref(0)
const connecting = ref(false),
  recording = ref(false),
  playing = ref(false),
  responding = ref(false)
const sending = ref(false),
  micPending = ref(false),
  interrupting = ref(false)
const messages = ref<Array<{ role: 'user' | 'assistant'; text: string }>>([])
const state = computed<DigitalHumanState>(() =>
  error.value
    ? 'ERROR'
    : recording.value
      ? 'LISTENING'
      : playing.value
        ? 'SPEAKING'
        : responding.value
          ? 'THINKING'
          : 'IDLE'
)
const audio = new DialogueAudio((value, audible) => {
  level.value = value
  playing.value = audible
})
audio.onLimit = () => {
  if (recording.value) void send()
}
let generation = 0,
  turn = 0
let sessionHumanId = props.config.id
let abort: AbortController | undefined
let clock: ReturnType<typeof setInterval> | undefined
let heartbeat: ReturnType<typeof setInterval> | undefined
function settleMessages(userText: string, assistantText: string) {
  for (const message of messages.value) {
    if (!message.text) message.text = message.role === 'user' ? userText : assistantText
  }
}
function stop() {
  settleMessages('本轮识别已结束', '本轮回答已结束')
  generation++
  turn++
  abort?.abort()
  abort = undefined
  clearInterval(clock)
  clearInterval(heartbeat)
  const oldSession = session.value,
    id = sessionHumanId
  session.value = ''
  connecting.value = recording.value = responding.value = playing.value = false
  micPending.value = sending.value = interrupting.value = false
  audio.dispose()
  if (oldSession) void DigitalHumanApi.closeDialogue(id, oldSession).catch(() => {})
}
function fail(cause: unknown) {
  settleMessages('本轮未完成识别', '本轮回答失败，请重新开始对话')
  stop()
  error.value = cause instanceof Error ? cause.message : '语音对话失败，请检查智能体配置后重试'
}
async function connect() {
  if (connecting.value || session.value) return
  stop()
  error.value = ''
  messages.value = []
  const current = generation,
    id = props.config.id
  connecting.value = true
  try {
    await audio.prepare()
    if (current !== generation) return
    const created = await DigitalHumanApi.openDialogue(id)
    if (current !== generation) {
      void DigitalHumanApi.closeDialogue(id, created).catch(() => {})
      return
    }
    sessionHumanId = id
    session.value = created
    heartbeat = setInterval(() => {
      void DigitalHumanApi.keepDialogue(id, created).catch(() => {
        if (current === generation) fail(Error('测试对话已失效，请重新开始'))
      })
    }, 45000)
  } catch (cause) {
    if (current === generation) fail(cause)
  } finally {
    if (current === generation) connecting.value = false
  }
}
async function interrupt() {
  if (!session.value || interrupting.value) return
  const current = generation
  turn++
  abort?.abort()
  abort = undefined
  audio.stopPlayback()
  settleMessages('本轮识别已打断', '本轮回答已打断')
  responding.value = false
  interrupting.value = true
  try {
    await DigitalHumanApi.interruptDialogue(props.config.id, session.value)
  } catch (cause) {
    if (current === generation) fail(cause)
  } finally {
    if (current === generation) interrupting.value = false
  }
}
async function record() {
  if (!session.value || micPending.value || recording.value || interrupting.value) return
  const current = generation
  micPending.value = true
  error.value = ''
  try {
    if (responding.value || playing.value) await interrupt()
    if (current !== generation || !session.value) return
    audio.stopPlayback()
    const started = await audio.startRecording()
    if (current !== generation || !started) return
    recording.value = true
    seconds.value = 0
    clock = setInterval(() => {
      seconds.value++
      if (seconds.value >= 30) void send()
    }, 1000)
  } catch (cause) {
    if (current === generation) {
      const detail =
        cause && typeof cause === 'object' && 'name' in cause && cause.name === 'NotAllowedError'
          ? '麦克风权限未开启，请在浏览器中允许后重试'
          : cause
      fail(typeof detail === 'string' ? Error(detail) : detail)
    }
  } finally {
    if (current === generation) micPending.value = false
  }
}
async function send() {
  if (!session.value || !recording.value || sending.value) return
  const current = generation,
    currentTurn = ++turn
  clearInterval(clock)
  recording.value = false
  sending.value = true
  try {
    const bytes = await audio.finishRecording()
    if (current !== generation) return
    if (bytes.length < 3200) {
      error.value = '录音太短，请至少说一个词后再发送'
      return
    }
    responding.value = true
    if (messages.value.length >= 40) messages.value.splice(0, 2)
    const userMessage = { role: 'user' as const, text: '' },
      assistantMessage = { role: 'assistant' as const, text: '' }
    messages.value.push(userMessage, assistantMessage)
    // Use reactive proxies so streaming subtitle changes immediately update the view.
    const userIndex = messages.value.length - 2,
      assistantIndex = messages.value.length - 1
    abort = new AbortController()
    const onEvent = (event: DialogueEvent) => {
      if (current !== generation || currentTurn !== turn) return
      if (event.type === 'user.delta') messages.value[userIndex].text = event.text || ''
      if (event.type === 'user.done') messages.value[userIndex].text = event.text || ''
      if (event.type === 'assistant.delta') messages.value[assistantIndex].text += event.text || ''
      if (event.type === 'assistant.text') messages.value[assistantIndex].text = event.text || ''
      if (event.type === 'audio' && event.pcm) audio.play(event.pcm, event.sampleRate)
      if (event.type === 'done' || event.type === 'interrupted') {
        responding.value = false
        settleMessages(
          '未返回识别文字',
          event.type === 'done' ? '未返回回答文字' : '本轮回答已打断'
        )
      }
      if (event.type === 'error') throw Error(event.message || '语音服务不可用')
    }
    sending.value = false
    await streamDialogue(props.config.id, session.value, audioBase64(bytes), abort.signal, onEvent)
  } catch (cause) {
    if (current === generation && currentTurn === turn) fail(cause)
  } finally {
    if (current === generation && currentTurn === turn) {
      responding.value = false
      sending.value = false
    }
  }
}
watch(
  () => props.config.id,
  () => {
    stop()
    messages.value = []
    error.value = ''
  }
)
onBeforeUnmount(stop)
</script>
<style scoped>
.dialogue-hint {
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.dialogue-controls {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.subtitles {
  max-height: 240px;
  padding: 12px 16px;
  margin-top: 18px;
  overflow-y: auto;
  background: var(--el-fill-color-light);
  border-radius: 12px;
}

.empty-subtitle {
  margin: 4px 0;
  color: var(--el-text-color-secondary);
}

.message + .message {
  margin-top: 12px;
}

.message span {
  font-size: 12px;
  color: var(--el-color-primary);
}

.message.user span {
  color: var(--el-text-color-secondary);
}

.message p {
  margin: 3px 0;
  line-height: 1.6;
  white-space: pre-wrap;
}
</style>
