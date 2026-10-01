<template>
  <div class="avatar-panel">
    <div class="avatar-toolbar">
      <el-radio-group v-model="mode" size="small" aria-label="形象展示方式">
        <el-radio-button value="character">动画角色</el-radio-button>
        <el-radio-button v-if="avatarUrl" value="portrait">上传图片</el-radio-button>
      </el-radio-group>
      <el-select
        v-if="mode === 'character'"
        v-model="expression"
        size="small"
        class="expression"
        aria-label="角色表情"
      >
        <el-option label="自动表情" value="auto" /><el-option label="开心" value="happy" />
        <el-option label="思考" value="thinking" /><el-option label="惊讶" value="surprised" />
      </el-select>
    </div>
    <div class="avatar-stage" :data-state="state" :data-action="action" :data-expression="face">
      <div class="halo"></div>
      <svg
        v-if="mode === 'character'"
        viewBox="0 0 280 280"
        class="character"
        role="img"
        :aria-label="`数字人：${labels[state]}`"
      >
        <ellipse cx="140" cy="257" rx="72" ry="10" fill="#dce5f2" />
        <g class="body-breathe">
          <rect x="80" y="166" width="120" height="81" rx="35" fill="#668bf2" />
          <rect x="106" y="184" width="68" height="40" rx="18" fill="#e5edff" />
          <circle cx="140" cy="204" r="9" fill="#9dd9be" />
          <g class="left-arm">
            <rect x="55" y="175" width="27" height="62" rx="13" fill="#96aff5" />
          </g>
          <g class="right-arm">
            <rect x="198" y="175" width="27" height="62" rx="13" fill="#96aff5" />
          </g>
          <g class="head-motion">
            <rect x="132" y="25" width="16" height="28" rx="8" fill="#7699f1" />
            <circle cx="140" cy="26" r="10" fill="#9dd9be" />
            <rect x="43" y="92" width="20" height="43" rx="10" fill="#668bf2" />
            <rect x="217" y="92" width="20" height="43" rx="10" fill="#668bf2" />
            <rect
              x="57"
              y="49"
              width="166"
              height="130"
              rx="48"
              fill="#dae6ff"
              stroke="#c1d1f8"
              stroke-width="3"
            />
            <rect x="74" y="69" width="132" height="92" rx="32" fill="#293b64" />
            <g class="eyes-blink">
              <path
                v-if="face === 'happy'"
                d="M94 108 Q104 91 114 108 M166 108 Q176 91 186 108"
                fill="none"
                stroke="#ccfce8"
                stroke-width="7"
                stroke-linecap="round"
              />
              <g v-else class="eyes-look">
                <ellipse
                  cx="104"
                  cy="105"
                  :rx="face === 'surprised' ? 10 : 7"
                  :ry="face === 'surprised' ? 14 : 11"
                  fill="#ccfce8"
                />
                <ellipse
                  cx="176"
                  cy="105"
                  :rx="face === 'surprised' ? 10 : 7"
                  :ry="face === 'surprised' ? 14 : 11"
                  fill="#ccfce8"
                />
              </g>
            </g>
            <ellipse cx="89" cy="126" rx="10" ry="5" fill="#e6a8bc" opacity=".5" />
            <ellipse cx="191" cy="126" rx="10" ry="5" fill="#e6a8bc" opacity=".5" />
            <ellipse
              v-if="state === 'SPEAKING' || face === 'surprised'"
              cx="140"
              cy="135"
              :rx="face === 'surprised' ? 9 : 13 + level * 5"
              :ry="face === 'surprised' ? 12 : 2 + level * 13"
              fill="#ccfce8"
            />
            <path
              v-else
              d="M125 131 Q140 144 155 131"
              fill="none"
              stroke="#ccfce8"
              stroke-width="5"
              stroke-linecap="round"
            />
          </g>
        </g>
      </svg>
      <div v-else class="portrait-breathe"
        ><img :src="avatarUrl" alt="数字人上传形象" class="portrait"
      /></div>
      <div class="status-pill"
        ><span :class="{ pulsing: state !== 'IDLE' }"></span>{{ labels[state] }}</div
      >
      <div class="voice-bars" aria-hidden="true">
        <i v-for="n in 9" :key="n" :style="{ height: `${4 + level * ((n % 3) + 1) * 11}px` }"></i>
      </div>
    </div>
    <div class="gesture-controls">
      <el-button size="small" @click="gesture('nod')">点头</el-button>
      <el-button size="small" @click="gesture('shake')">摇头</el-button>
      <el-button v-if="mode === 'character'" size="small" @click="gesture('wave')">挥手</el-button>
      <span>{{ mode === 'character' ? '口型跟随实际播放的音量' : '图片呈现呼吸与摆动效果' }}</span>
    </div>
  </div>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import type { DigitalHumanState } from '@/api/ai/digital-human'
const props = defineProps<{ avatarUrl?: string; state: DigitalHumanState; level: number }>()
const mode = ref('character'),
  expression = ref('auto'),
  action = ref('')
const face = computed(() =>
  expression.value !== 'auto'
    ? expression.value
    : props.state === 'THINKING'
      ? 'thinking'
      : props.state === 'SPEAKING'
        ? 'happy'
        : 'neutral'
)
const labels: Record<DigitalHumanState, string> = {
  IDLE: '准备好了',
  LISTENING: '正在听你说',
  THINKING: '正在思考',
  SPEAKING: '正在回答',
  EXECUTING: '正在处理',
  ERROR: '连接异常'
}
let timer: ReturnType<typeof setTimeout> | undefined
let frame = 0
function gesture(value: string) {
  clearTimeout(timer)
  cancelAnimationFrame(frame)
  action.value = ''
  frame = requestAnimationFrame(() => {
    action.value = value
    timer = setTimeout(() => {
      action.value = ''
    }, 1300)
  })
}
watch(
  () => props.state,
  (state, previous) => {
    if (state === 'SPEAKING' && previous !== 'SPEAKING') gesture('nod')
  }
)
onBeforeUnmount(() => {
  clearTimeout(timer)
  cancelAnimationFrame(frame)
})
</script>
<style scoped>
.avatar-toolbar {
  display: flex;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}

.expression {
  width: 120px;
}

.avatar-stage {
  position: relative;
  display: flex;
  min-height: 330px;
  padding-top: 10px;
  margin-top: 14px;
  overflow: hidden;
  background: linear-gradient(160deg, #f3f7ff, #eff9f5);
  border-radius: 22px;
  flex-direction: column;
  align-items: center;
}

.character {
  position: relative;
  width: 280px;
  height: 280px;
  max-width: 100%;
}

.halo {
  position: absolute;
  top: 45px;
  width: 210px;
  height: 210px;
  border: 1px solid #d9e4f7;
  border-radius: 50%;
  box-shadow: 0 0 0 20px #e5edf960;
}

[data-state='LISTENING'] .halo {
  border-color: #76c8a4;
  animation: pulse 1.5s infinite;
}

.body-breathe,
.portrait-breathe {
  animation: breathe 4s ease-in-out infinite;
}

.eyes-blink {
  transform-box: fill-box;
  transform-origin: center;
  animation: blink 4.8s infinite;
}

[data-expression='thinking'] .eyes-look {
  transform: translate(3px, -4px);
}

.head-motion {
  transform-origin: 140px 150px;
}

.right-arm {
  transform-origin: 210px 182px;
}

[data-action='nod'] .head-motion,
[data-action='nod'] .portrait {
  animation: nod 1s ease-in-out;
}

[data-action='shake'] .head-motion,
[data-action='shake'] .portrait {
  animation: shake 1s ease-in-out;
}

[data-action='wave'] .right-arm {
  animation: wave 1.2s ease-in-out;
}

.portrait-breathe {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 280px;
}

.portrait {
  max-width: 260px;
  max-height: 250px;
  border-radius: 20px;
  object-fit: contain;
}

.status-pill {
  display: flex;
  font-size: 12px;
  color: #47617f;
  gap: 7px;
  align-items: center;
}

.status-pill span {
  width: 7px;
  height: 7px;
  background: #72b99a;
  border-radius: 50%;
}

.pulsing {
  animation: pulse 1s infinite;
}

.voice-bars {
  display: flex;
  height: 28px;
  margin: 4px;
  align-items: center;
  gap: 4px;
}

.voice-bars i {
  display: block;
  width: 4px;
  background: #7193e9;
  border-radius: 3px;
  transition: height 0.08s;
}

.gesture-controls {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 12px;
}

.gesture-controls span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

@keyframes breathe {
  50% {
    transform: translateY(-4px);
  }
}

@keyframes blink {
  0%,
  93%,
  98%,
  100% {
    transform: scaleY(1);
  }

  95%,
  96% {
    transform: scaleY(0.08);
  }
}

@keyframes nod {
  25%,
  70% {
    transform: translateY(5px) rotate(3deg);
  }

  45%,
  90% {
    transform: translateY(-2px);
  }
}

@keyframes shake {
  20%,
  60% {
    transform: rotate(-7deg);
  }

  40%,
  80% {
    transform: rotate(7deg);
  }
}

@keyframes wave {
  25%,
  65% {
    transform: rotate(-155deg);
  }

  45%,
  85% {
    transform: rotate(-120deg);
  }
}

@keyframes pulse {
  50% {
    opacity: 0.45;
  }
}

@media (prefers-reduced-motion: reduce) {
  .body-breathe,
  .portrait-breathe,
  .eyes-blink,
  .head-motion,
  .right-arm,
  .portrait,
  .halo,
  .pulsing {
    animation: none !important;
  }
}
</style>
