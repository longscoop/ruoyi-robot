<template>
  <el-dialog
    :model-value="modelValue"
    title="数字人预览"
    width="min(650px, 95vw)"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <div v-if="remote" class="stage" :data-state="state">
      <video
        ref="video"
        class="video"
        autoplay
        playsinline
        controls
        :poster="config?.avatarUrl"
      ></video>
      <div class="state">{{ state }}</div>
      <div class="subtitle">{{ config?.welcomeText }}</div>
    </div>
    <template v-if="remote">
      <p class="hint"
        >试听使用 LiveTalking
        服务自身的音色。实时对话使用智能体的语音配置；若无声音，请点击视频播放按钮。</p
      >
      <div class="controls">
        <el-button type="primary" :loading="connecting" :disabled="connected" @click="connect"
          >连接视频</el-button
        >
        <el-button :disabled="!connected && !connecting" @click="stop">断开</el-button>
      </div>
      <el-input
        v-model="text"
        type="textarea"
        :rows="2"
        maxlength="2000"
        placeholder="输入要播报的文字"
        class="mt-3"
      />
      <div class="controls mt-3">
        <el-button
          type="primary"
          :disabled="!connected || !text.trim()"
          :loading="sending"
          @click="speak"
          >播报</el-button
        >
        <el-button :disabled="!connected" @click="interrupt">打断播报</el-button>
      </div>
    </template>
    <StaticHumanDialogue v-if="!remote && modelValue && config" :key="config.id" :config="config" />
  </el-dialog>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import {
  DigitalHumanApi,
  type DigitalHumanPreviewVO,
  type DigitalHumanState
} from '@/api/ai/digital-human'
import { createDigitalHumanRenderer, LiveTalkingRenderer } from '../runtime/LiveTalkingRenderer'
import StaticHumanDialogue from './StaticHumanDialogue.vue'
const props = defineProps<{ modelValue: boolean; config?: DigitalHumanPreviewVO }>()
defineEmits(['update:modelValue'])
const state = ref<DigitalHumanState>('IDLE')
const video = ref<HTMLVideoElement>()
const text = ref('')
const error = ref('')
const connecting = ref(false),
  connected = ref(false),
  sending = ref(false)
const remote = computed(
  () => !!props.config?.rendering && props.config.rendering.provider !== 'BUILTIN'
)
let renderer: LiveTalkingRenderer | undefined
let poll: ReturnType<typeof setTimeout> | undefined
let generation = 0
function stop() {
  generation++
  clearTimeout(poll)
  renderer?.dispose()
  renderer = undefined
  connected.value = connecting.value = sending.value = false
  state.value = 'IDLE'
}
function fail(message: string) {
  stop()
  error.value = message
  state.value = 'ERROR'
}
async function connect() {
  if (!props.config || !video.value || connecting.value || connected.value) return
  stop()
  const current = generation
  const id = props.config.id
  connecting.value = true
  error.value = ''
  try {
    const client = createDigitalHumanRenderer(
      props.config,
      video.value,
      {
        offer: (sdp) => DigitalHumanApi.renderOffer(id, sdp),
        close: (session) => DigitalHumanApi.renderClose(id, session)
      },
      (message) => {
        if (current === generation) fail(message)
      }
    )
    if (!(client instanceof LiveTalkingRenderer)) return
    renderer = client
    await client.connect()
    if (current !== generation || !client.sessionId) return
    connected.value = true
    void pollState(id, client.sessionId, current)
  } catch (cause) {
    if (current === generation) fail(cause instanceof Error ? cause.message : '数字人连接失败')
  } finally {
    if (current === generation) connecting.value = false
  }
}
async function pollState(id: number, session: string, current: number) {
  try {
    const speaking = await DigitalHumanApi.renderSpeaking(id, session)
    if (current !== generation) return
    state.value = speaking ? 'SPEAKING' : 'IDLE'
    poll = setTimeout(() => void pollState(id, session, current), 1500)
  } catch {
    if (current === generation) fail('数字人连接已失效，请重新连接')
  }
}
async function speak() {
  if (!props.config || !renderer?.sessionId) return
  const current = generation
  sending.value = true
  try {
    await DigitalHumanApi.renderSpeak(props.config.id, renderer.sessionId, text.value)
  } catch {
    if (current === generation) fail('播报失败，请检查 LiveTalking 的 TTS 配置')
  } finally {
    if (current === generation) sending.value = false
  }
}
async function interrupt() {
  if (!props.config || !renderer?.sessionId) return
  const current = generation
  try {
    await DigitalHumanApi.renderInterrupt(props.config.id, renderer.sessionId)
  } catch {
    if (current === generation) fail('打断失败，请重新连接')
  }
}
watch(
  [() => props.modelValue, () => props.config],
  () => {
    stop()
    error.value = ''
    text.value = props.config?.welcomeText || '你好，我是你的数字人助手。'
  },
  { immediate: true }
)
onBeforeUnmount(stop)
</script>
<style scoped>
.stage {
  min-height: 320px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
}
.avatar {
  max-height: 240px;
  max-width: 100%;
  transition: transform 0.15s;
}
.video {
  width: 100%;
  max-height: 420px;
  background: #15181e;
}
.stage[data-state='SPEAKING'] .avatar {
  transform: scale(1.02);
}
.fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 220px;
  height: 240px;
  background: var(--el-fill-color-light);
}
.state {
  font-weight: 600;
}
.subtitle {
  min-height: 24px;
}
.controls {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.hint {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}
</style>
