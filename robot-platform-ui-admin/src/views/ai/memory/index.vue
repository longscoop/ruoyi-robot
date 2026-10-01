<template>
  <ContentWrap>
    <el-collapse class="mb-4">
      <el-collapse-item title="查询智能体使用的记忆" name="provider">
        <el-form inline>
          <el-form-item label="智能体"
            ><el-select v-model="preview.agentId" filterable class="!w-48"
              ><el-option
                v-for="item in agentOptions"
                :key="item.id"
                :label="item.name"
                :value="item.id" /></el-select
          ></el-form-item>
          <el-form-item label="机器人"
            ><el-select v-model="preview.robotId" filterable class="!w-48"
              ><el-option
                v-for="item in options.robots"
                :key="item.id"
                :label="item.name"
                :value="item.id" /></el-select
          ></el-form-item>
          <el-form-item label="当前问题"
            ><el-input
              v-model="preview.question"
              placeholder="输入问题检索相关记忆"
              maxlength="2000"
          /></el-form-item>
          <el-form-item
            ><el-button :loading="previewLoading" @click="queryProvider"
              >查询</el-button
            ></el-form-item
          >
        </el-form>
        <el-alert
          :closable="false"
          title="这里查询机器人范围的本地摘要或外部记忆。下方列表管理数据库分类记忆；已验证成员的记忆由实际会话按身份读取。"
          type="info"
        />
        <el-table :data="previewRows"
          ><el-table-column label="分类" width="120"
            ><template #default="{ row }">{{
              memoryTypes[row.memoryType] || row.memoryType
            }}</template></el-table-column
          ><el-table-column prop="content" label="记忆内容"
        /></el-table>
      </el-collapse-item>
    </el-collapse>
    <el-form inline>
      <el-form-item><el-button :loading="loading" @click="load">刷新</el-button></el-form-item>
      <el-form-item label="分类">
        <el-select v-model="q.memoryType" clearable class="!w-36">
          <el-option
            v-for="(label, value) in memoryTypes"
            :key="value"
            :value="value"
            :label="label"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="范围"
        ><el-select v-model="q.scope" clearable class="!w-36"
          ><el-option
            v-for="(label, value) in scopes"
            :key="value"
            :value="value"
            :label="label" /></el-select
      ></el-form-item>
      <el-form-item label="成员"
        ><el-select v-model="q.memberId" clearable filterable class="!w-40"
          ><el-option
            v-for="item in options.members"
            :key="item.id"
            :label="item.name"
            :value="item.id" /></el-select
      ></el-form-item>
      <el-form-item label="机器人"
        ><el-select v-model="q.robotId" clearable filterable class="!w-48"
          ><el-option
            v-for="item in options.robots"
            :key="item.id"
            :label="item.name"
            :value="item.id" /></el-select
      ></el-form-item>
      <el-form-item label="状态"
        ><el-select v-model="q.status" clearable class="!w-32"
          ><el-option label="有效" value="ACTIVE" /><el-option
            label="已替换"
            value="SUPERSEDED" /></el-select
      ></el-form-item>
    </el-form>
    <el-table :data="filtered" v-loading="loading">
      <el-table-column label="范围" width="110"
        ><template #default="{ row }">{{
          scopes[row.scope] || row.scope
        }}</template></el-table-column
      >
      <el-table-column label="分类" width="110"
        ><template #default="{ row }">{{
          memoryTypes[row.memoryType] || row.memoryType
        }}</template></el-table-column
      >
      <el-table-column label="成员" min-width="100"
        ><template #default="{ row }">{{
          name('members', row.memberId)
        }}</template></el-table-column
      >
      <el-table-column label="机器人" min-width="160"
        ><template #default="{ row }">{{ name('robots', row.robotId) }}</template></el-table-column
      >
      <el-table-column prop="content" label="内容" min-width="260" />
      <el-table-column label="过期时间" min-width="170"
        ><template #default="{ row }">{{
          formatDate(row.expiresAt) || '长期有效'
        }}</template></el-table-column
      >
      <el-table-column label="状态" width="100"
        ><template #default="{ row }">{{
          row.status === 'ACTIVE' ? '有效' : statusName(row.status)
        }}</template></el-table-column
      >
      <el-table-column label="来源" width="110"
        ><template #default="{ row }"
          ><router-link
            v-if="row.sourceConversationId"
            :to="`/ai/conversation?id=${row.sourceConversationId}`"
            class="text-primary"
            >查看来源对话</router-link
          ></template
        ></el-table-column
      >
      <el-table-column label="操作" width="130"
        ><template #default="{ row }"
          ><el-button link @click="edit(row)">编辑</el-button
          ><el-button link type="danger" @click="remove(row)">失效</el-button></template
        ></el-table-column
      >
    </el-table>
    <el-dialog v-model="open" title="编辑记忆" width="560px">
      <el-form label-position="top">
        <el-form-item label="分类">
          <el-select v-model="form.memoryType" class="!w-full">
            <el-option
              v-for="(label, value) in memoryTypes"
              :key="value"
              :value="value"
              :label="label"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="内容"
          ><el-input v-model="form.content" type="textarea" :rows="4"
        /></el-form-item>
        <el-form-item label="摘要"><el-input v-model="form.summary" /></el-form-item>
        <el-form-item label="重要程度"
          ><el-input-number v-model="form.importance" :min="0" :max="1" :step="0.1"
        /></el-form-item>
        <el-form-item label="过期时间"
          ><el-date-picker
            v-model="form.expiresAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            clearable
        /></el-form-item>
      </el-form>
      <template #footer
        ><el-button @click="open = false">取消</el-button
        ><el-button type="primary" @click="save">保存</el-button></template
      >
    </el-dialog>
  </ContentWrap>
</template>
<script setup lang="ts">
import { checkPermi } from '@/utils/permission'
import { computed, onMounted, reactive, ref } from 'vue'
import {
  AiMemoryApi,
  MemoryProviderApi,
  type MemorySnippet,
  type MemoryVO,
  type MemoryUpdateVO
} from '@/api/ai/memory'
import { useAiNames, statusName } from '@/api/ai/display'
import { AgentApi } from '@/api/ai/enterprise'
import { formatDate } from '@/utils/formatTime'
const { options, name, loadNames } = useAiNames()
const message = useMessage()
const agentOptions = ref<{ id: number; name: string }[]>([])
const preview = reactive({
  agentId: undefined as number | undefined,
  robotId: undefined as number | undefined,
  question: ''
})
const previewRows = ref<MemorySnippet[]>([])
const previewLoading = ref(false)
async function queryProvider() {
  if (!preview.agentId || !preview.robotId) {
    message.warning('请选择智能体和机器人')
    return
  }
  previewLoading.value = true
  try {
    previewRows.value = await MemoryProviderApi.query(
      preview.agentId,
      preview.robotId,
      preview.question
    )
  } finally {
    previewLoading.value = false
  }
}
const rows = ref<MemoryVO[]>([])
const loading = ref(false)
const open = ref(false)
const editing = ref<number>()
const q = reactive({
  scope: '',
  memoryType: '',
  memberId: undefined as number | undefined,
  robotId: undefined as number | undefined,
  status: ''
})
const form = reactive<MemoryUpdateVO>({
  content: '',
  memoryType: 'FACT',
  summary: '',
  importance: 0.5,
  expiresAt: undefined
})
const scopes: Record<string, string> = {
  MEMBER: '成员',
  MEMBER_ROBOT: '成员与机器人',
  ROBOT: '机器人'
}
const memoryTypes: Record<string, string> = {
  IDENTITY: '身份信息',
  CONTEXT: '情景',
  WORK: '工作',
  PROFILE: '身份信息（旧分类）',
  PREFERENCE: '用户偏好',
  RELATION: '关系',
  HABIT: '习惯',
  FACT: '事实',
  ENVIRONMENT: '环境',
  INSTRUCTION: '要求',
  EVENT: '临时事件',
  SUMMARY: '摘要'
}
const filtered = computed(() =>
  rows.value.filter(
    (row) =>
      (!q.scope || row.scope === q.scope) &&
      (!q.memoryType || row.memoryType === q.memoryType) &&
      (!q.memberId || row.memberId === q.memberId) &&
      (!q.robotId || row.robotId === q.robotId) &&
      (!q.status || row.status === q.status)
  )
)
async function load() {
  loading.value = true
  try {
    const [data] = await Promise.all([AiMemoryApi.list(), loadNames()])
    rows.value = data
    if (checkPermi(['ai:agent:query'])) agentOptions.value = await AgentApi.list()
  } finally {
    loading.value = false
  }
}
function edit(row: MemoryVO) {
  editing.value = row.id
  Object.assign(form, {
    content: row.content,
    memoryType: row.memoryType,
    summary: row.summary,
    importance: row.importance,
    expiresAt: row.expiresAt
  })
  open.value = true
}
async function save() {
  if (editing.value) await AiMemoryApi.update(editing.value, form)
  open.value = false
  await load()
}
async function remove(row: MemoryVO) {
  await message.confirm('确定使这条记忆失效？')
  await AiMemoryApi.delete(row.id)
  await load()
}
onMounted(load)
</script>
