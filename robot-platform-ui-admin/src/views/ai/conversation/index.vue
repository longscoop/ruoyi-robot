<template>
  <ContentWrap>
    <div class="mb-4 flex items-center gap-3">
      <el-button :loading="loading" @click="load">刷新</el-button>
      <el-input v-model="search" clearable placeholder="搜索智能体或机器人名称" class="!w-64" />
      <span class="text-sm text-gray-500">对话内容与语音连接信息统一查看，每 5 秒刷新</span>
    </div>
    <el-table :data="filtered" v-loading="loading" @row-click="show">
      <el-table-column label="智能体" min-width="170"
        ><template #default="{ row }">{{ name('agents', row.agentId) }}</template></el-table-column
      >
      <el-table-column label="机器人" min-width="160"
        ><template #default="{ row }">{{ name('robots', row.robotId) }}</template></el-table-column
      >
      <el-table-column label="成员" min-width="100"
        ><template #default="{ row }">{{
          name('members', row.memberId)
        }}</template></el-table-column
      >
      <el-table-column label="对话方式" min-width="150"
        ><template #default="{ row }">{{
          modeName(row.realtimeSessions?.[0]?.mode)
        }}</template></el-table-column
      >
      <el-table-column label="模型" min-width="160"
        ><template #default="{ row }">{{
          name('models', row.realtimeSessions?.[0]?.modelId)
        }}</template></el-table-column
      >
      <el-table-column label="状态" width="100"
        ><template #default="{ row }"
          ><el-tag
            :type="row.status === 'ERROR' ? 'danger' : row.status === 'ACTIVE' ? 'success' : 'info'"
            >{{ statusName(row.status) }}</el-tag
          ></template
        ></el-table-column
      >
      <el-table-column label="开始时间" min-width="180"
        ><template #default="{ row }">{{
          formatDate(row.startedAt || row.createdAt) || '—'
        }}</template></el-table-column
      >
      <el-table-column label="结束时间" min-width="180"
        ><template #default="{ row }">{{
          formatDate(row.endedAt) || '—'
        }}</template></el-table-column
      >
      <el-table-column label="操作" width="100"
        ><template #default="{ row }"
          ><el-button link type="primary" @click.stop="show(row)">查看对话</el-button></template
        ></el-table-column
      >
    </el-table>
    <el-drawer
      v-model="open"
      :title="detail ? `${name('agents', detail.conversation.agentId)} · 对话详情` : '对话详情'"
      size="min(720px, 95vw)"
    >
      <el-tabs v-if="detail" v-model="detailTab">
        <el-tab-pane label="聊天内容" name="messages">
          <el-empty v-if="!detail.messages.length" description="本次连接尚无聊天内容" />
          <div v-for="message in detail.messages" :key="message.id" class="mb-5">
            <div class="flex items-center gap-2"
              ><el-tag :type="message.role === 'USER' ? 'info' : 'success'">{{
                message.role === 'USER'
                  ? '用户'
                  : message.role === 'ASSISTANT'
                    ? name('agents', detail.conversation.agentId)
                    : '工具'
              }}</el-tag
              ><span class="text-xs text-gray-500">{{ formatDate(message.createdAt) }}</span></div
            >
            <p class="whitespace-pre-wrap leading-7">{{ message.content }}</p>
          </div>
        </el-tab-pane>
        <el-tab-pane label="语音连接" name="sessions">
          <el-empty
            v-if="!detail.conversation.realtimeSessions.length"
            description="这是一段文字对话，无语音连接"
          />
          <el-descriptions
            v-for="session in detail.conversation.realtimeSessions"
            :key="session.id"
            :column="1"
            border
            class="mb-4"
          >
            <el-descriptions-item label="方式">{{ modeName(session.mode) }}</el-descriptions-item>
            <el-descriptions-item label="服务商">{{
              name('providers', session.providerId)
            }}</el-descriptions-item>
            <el-descriptions-item label="模型">{{
              name('models', session.modelId)
            }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{
              statusName(session.status)
            }}</el-descriptions-item>
            <el-descriptions-item label="首个输入音频">{{
              formatDate(session.firstAudioAt) || '—'
            }}</el-descriptions-item>
            <el-descriptions-item label="首次响应">{{
              formatDate(session.firstResponseAt) || '—'
            }}</el-descriptions-item>
            <el-descriptions-item label="结束时间">{{
              formatDate(session.endedAt) || '—'
            }}</el-descriptions-item>
            <el-descriptions-item label="打断次数">{{
              session.interruptCount
            }}</el-descriptions-item>
            <el-descriptions-item v-if="session.errorCode" label="异常原因">{{
              session.errorCode
            }}</el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>
      </el-tabs>
    </el-drawer>
  </ContentWrap>
</template>
<script setup lang="ts">
import { formatDate } from '@/utils/formatTime'
import { computed, onMounted, onUnmounted, onActivated, onDeactivated, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  AiConversationApi,
  type ConversationVO,
  type ConversationDetailVO
} from '@/api/ai/conversation'
import { useAiNames, statusName, modeName } from '@/api/ai/display'
const { name, loadNames } = useAiNames()
const rows = ref<ConversationVO[]>([])
const detail = ref<ConversationDetailVO>()
const open = ref(false)
const loading = ref(false)
const search = ref('')
const detailTab = ref('messages')
const route = useRoute()
const filtered = computed(() =>
  rows.value.filter((row) =>
    `${name('agents', row.agentId)} ${name('robots', row.robotId)}`.includes(search.value)
  )
)
let timer: ReturnType<typeof setInterval> | undefined
async function load() {
  if (loading.value) return
  loading.value = true
  try {
    const [data] = await Promise.all([AiConversationApi.list(), loadNames()])
    rows.value = data
    if (open.value && detail.value)
      detail.value = await AiConversationApi.get(detail.value.conversation.id)
  } finally {
    loading.value = false
  }
}
async function show(row: Pick<ConversationVO, 'id'>) {
  detail.value = await AiConversationApi.get(row.id)
  detailTab.value = 'messages'
  open.value = true
}
function start() {
  if (timer) return
  void load()
  timer = setInterval(() => {
    if (!document.hidden) void load()
  }, 5000)
}
function stop() {
  clearInterval(timer)
  timer = undefined
}
watch(
  () => route.query.id,
  (id) => {
    if (Number(id) > 0) void show({ id: Number(id) })
  }
)
onMounted(() => {
  start()
  if (Number(route.query.id) > 0) void show({ id: Number(route.query.id) })
})
onActivated(start)
onDeactivated(stop)
onUnmounted(stop)
</script>
