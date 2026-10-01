<template>
  <ContentWrap>
    <el-tabs v-model="activeTab" @tab-change="loadActive">
      <el-tab-pane v-if="can('ai:agent:query')" label="智能体" name="agents" />
      <el-tab-pane v-if="can('ai:prompt:query')" label="角色" name="prompts" />
      <el-tab-pane v-if="can('ai:model:query')" label="模型" name="models" />
      <el-tab-pane v-if="can('ai:provider:query')" label="服务商" name="providers" />
    </el-tabs>
    <div class="mb-4 flex items-center justify-between">
      <span class="text-sm text-gray-500">{{ tabDescription }}</span>
      <el-button v-if="can(createPermission)" type="primary" @click="openCreate"
        >新增{{ tabLabel }}</el-button
      >
    </div>

    <el-table
      v-if="activeTab === 'agents'"
      v-loading="loading"
      :data="agents"
      stripe
      empty-text="暂无智能体"
    >
      <el-table-column label="名称" prop="name" min-width="150" />
      <el-table-column label="编码" prop="code" min-width="130" />
      <el-table-column label="语音模式" min-width="100"
        ><template #default="{ row }">{{ modeLabel(row.realtimeMode) }}</template></el-table-column
      >
      <el-table-column label="角色" min-width="150"
        ><template #default="{ row }">{{
          promptName(row.systemPromptId)
        }}</template></el-table-column
      >
      <el-table-column label="状态" width="100"
        ><template #default="{ row }"
          ><el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'">{{
            statusLabel(row.status)
          }}</el-tag></template
        ></el-table-column
      >
      <el-table-column label="操作" width="220" fixed="right"
        ><template #default="{ row }">
          <el-button v-if="can('ai:agent:update')" link type="primary" @click="openEdit(row)"
            >编辑</el-button
          >
          <el-button v-if="can('ai:agent:bind')" link type="primary" @click="openBindings(row)"
            >绑定机器人</el-button
          >
          <el-button v-if="can('ai:agent:delete')" link type="danger" @click="removeRecord(row)"
            >删除</el-button
          >
        </template></el-table-column
      >
    </el-table>

    <el-table
      v-if="activeTab === 'prompts'"
      v-loading="loading"
      :data="prompts"
      stripe
      empty-text="暂无角色"
    >
      <el-table-column label="名称" prop="name" min-width="160" />

      <el-table-column label="用途" width="130"
        ><template #default="{ row }">{{ promptTypeName(row.type) }}</template></el-table-column
      >
      <el-table-column label="版本" prop="version" width="80" />
      <el-table-column label="状态" width="100"
        ><template #default="{ row }">{{ statusLabel(row.status) }}</template></el-table-column
      >
      <el-table-column label="操作" width="130"
        ><template #default="{ row }"
          ><el-button v-if="can('ai:prompt:update')" link type="primary" @click="openEdit(row)"
            >编辑</el-button
          ><el-button v-else link type="primary" @click="showPrompt(row)">查看</el-button></template
        ></el-table-column
      >
    </el-table>

    <el-table
      v-if="activeTab === 'models'"
      v-loading="loading"
      :data="models"
      stripe
      empty-text="暂无模型"
    >
      <el-table-column label="名称" prop="name" min-width="150" />
      <el-table-column label="模型编码" prop="modelCode" min-width="160" />
      <el-table-column label="类型" prop="modelType" min-width="120" />
      <el-table-column label="服务商" min-width="140"
        ><template #default="{ row }">{{ providerName(row.providerId) }}</template></el-table-column
      >
      <el-table-column label="状态" width="90"
        ><template #default="{ row }">{{ statusLabel(row.status) }}</template></el-table-column
      >
      <el-table-column label="操作" width="130"
        ><template #default="{ row }"
          ><el-button v-if="can('ai:model:update')" link type="primary" @click="openEdit(row)"
            >编辑</el-button
          ><el-button v-if="can('ai:model:delete')" link type="danger" @click="removeRecord(row)"
            >删除</el-button
          ></template
        ></el-table-column
      >
    </el-table>

    <el-table
      v-if="activeTab === 'providers'"
      v-loading="loading"
      :data="providers"
      stripe
      empty-text="暂无服务商"
    >
      <el-table-column label="名称" prop="name" min-width="150" />
      <el-table-column label="编码" prop="code" min-width="140" />
      <el-table-column label="类型" prop="providerType" width="110" />
      <el-table-column label="接口地址" prop="baseUrl" min-width="190" show-overflow-tooltip />
      <el-table-column label="密钥" width="90"
        ><template #default="{ row }">{{
          row.apiKeyConfigured ? '已配置' : '未配置'
        }}</template></el-table-column
      >
      <el-table-column label="状态" width="90"
        ><template #default="{ row }">{{ statusLabel(row.status) }}</template></el-table-column
      >
      <el-table-column label="操作" width="130"
        ><template #default="{ row }"
          ><el-button v-if="can('ai:provider:update')" link type="primary" @click="openEdit(row)"
            >编辑</el-button
          ><el-button v-if="can('ai:provider:delete')" link type="danger" @click="removeRecord(row)"
            >删除</el-button
          ></template
        ></el-table-column
      >
    </el-table>
  </ContentWrap>

  <el-dialog
    v-model="dialogVisible"
    :title="`${editingId ? '编辑' : '新增'}${tabLabel}`"
    width="min(680px, 94vw)"
    destroy-on-close
  >
    <el-form label-position="top" :model="form" v-loading="saving">
      <el-form-item label="名称" required
        ><el-input v-model="form.name" maxlength="128"
      /></el-form-item>
      <el-form-item v-if="activeTab === 'agents' || activeTab === 'providers'" label="编码" required
        ><el-input v-model="form.code" maxlength="64"
      /></el-form-item>

      <template v-if="activeTab === 'providers'">
        <el-form-item label="服务商类型" required
          ><el-select v-model="form.providerType" class="w-full"
            ><el-option label="Coze" value="COZE" /><el-option
              label="Dify"
              value="DIFY" /><el-option label="FastGPT" value="FASTGPT" /><el-option
              label="通义千问"
              value="QWEN" /><el-option label="DeepSeek" value="DEEPSEEK" /><el-option
              label="豆包"
              value="DOUBAO" /></el-select
        ></el-form-item>
        <el-form-item label="接口地址" required
          ><el-input v-model="form.baseUrl" placeholder="https://..."
        /></el-form-item>
        <el-form-item :label="editingId ? 'API Key（留空保持原值）' : 'API Key'"
          ><el-input
            v-model="form.apiKey"
            type="password"
            show-password
            autocomplete="new-password"
        /></el-form-item>
        <el-form-item label="高级配置 JSON"
          ><el-input v-model="form.configJson" type="textarea" :rows="3" placeholder="可留空"
        /></el-form-item>
      </template>

      <template v-if="activeTab === 'models'">
        <el-form-item label="服务商" required
          ><el-select v-model="form.providerId" class="w-full" filterable
            ><el-option
              v-for="item in providers"
              :key="item.id"
              :label="item.name"
              :value="item.id" /></el-select
        ></el-form-item>
        <el-form-item label="模型编码" required
          ><el-input v-model="form.modelCode" placeholder="供应商提供的模型 ID"
        /></el-form-item>
        <el-form-item label="模型类型" required
          ><el-select v-model="form.modelType" class="w-full"
            ><el-option
              v-for="type in modelTypes"
              :key="type"
              :label="type"
              :value="type" /></el-select
        ></el-form-item>
        <el-form-item label="能力 JSON"
          ><el-input v-model="form.capabilitiesJson" type="textarea" :rows="2" placeholder="可留空"
        /></el-form-item>
        <el-form-item label="高级配置 JSON"
          ><el-input v-model="form.configJson" type="textarea" :rows="2" placeholder="可留空"
        /></el-form-item>
      </template>

      <template v-if="activeTab === 'prompts'">
        <el-form-item label="类型" required
          ><el-select v-model="form.type" class="w-full" :disabled="!!editingId"
            ><el-option label="角色" value="SYSTEM" /><el-option
              label="记忆提取"
              value="MEMORY_EXTRACT" /><el-option
              label="记忆摘要"
              value="MEMORY_SUMMARY" /><el-option
              label="工具路由"
              value="TOOL_ROUTING" /></el-select
        ></el-form-item>
        <el-form-item label="内容" required
          ><el-input v-model="form.content" type="textarea" :rows="9"
        /></el-form-item>
      </template>

      <template v-if="activeTab === 'agents'">
        <el-form-item label="描述"
          ><el-input v-model="form.description" type="textarea" :rows="2" maxlength="1000"
        /></el-form-item>
        <el-form-item label="角色" required
          ><el-select v-model="form.systemPromptId" class="w-full" filterable
            ><el-option
              v-for="item in systemPrompts"
              :key="item.id"
              :label="`${item.name} · v${item.version}`"
              :value="item.id" /></el-select
        ></el-form-item>
        <el-form-item label="语音模式" required
          ><el-segmented
            v-model="form.realtimeMode"
            :options="[
              { label: '原生实时', value: 'NATIVE' },
              { label: '级联', value: 'CASCADE' },
              { label: '自动', value: 'AUTO' }
            ]"
        /></el-form-item>
        <el-form-item v-if="form.realtimeMode !== 'CASCADE'" label="实时语音模型" required
          ><el-select v-model="form.realtimeModelId" class="w-full" filterable
            ><el-option
              v-for="item in modelsOfType('REALTIME_S2S')"
              :key="item.id"
              :label="item.name"
              :value="item.id" /></el-select
        ></el-form-item>
        <template v-if="form.realtimeMode !== 'NATIVE'">
          <el-form-item label="对话模型" required
            ><el-select v-model="form.conversationModelId" class="w-full" filterable
              ><el-option
                v-for="item in modelsOfType('CHAT')"
                :key="item.id"
                :label="item.name"
                :value="item.id" /></el-select
          ></el-form-item>
          <el-form-item label="语音识别模型" required
            ><el-select v-model="form.asrModelId" class="w-full" filterable
              ><el-option
                v-for="item in modelsOfType('ASR')"
                :key="item.id"
                :label="item.name"
                :value="item.id" /></el-select
          ></el-form-item>
          <el-form-item label="语音合成模型" required
            ><el-select v-model="form.ttsModelId" class="w-full" filterable
              ><el-option
                v-for="item in modelsOfType('TTS')"
                :key="item.id"
                :label="item.name"
                :value="item.id" /></el-select
          ></el-form-item>
        </template>
        <el-form-item label="记忆实现">
          <el-select v-model="form.memoryMode" class="w-full">
            <el-option
              v-for="option in memoryOptions"
              :key="option.mode"
              :label="option.name + (option.configured ? '' : '（待配置）')"
              :value="option.mode"
              :disabled="!option.configured"
            />
            <el-option label="仅保留会话上下文" value="SESSION" />
            <el-option label="不使用（旧配置）" value="NONE" />
          </el-select>
        </el-form-item>
        <el-alert
          v-if="persistentMemory(form.memoryMode)"
          :closable="false"
          type="info"
          class="mb-4"
        >
          <template #title>{{ memoryDescription }}</template>
          <span v-if="form.realtimeMode !== 'CASCADE'"
            >原生实时仅在连接时载入称呼、表达方式等沟通偏好；宠物、工作等历史事实需要选择级联模式，按当前问题检索。</span
          >
        </el-alert>
        <el-form-item
          v-if="persistentMemory(form.memoryMode) && form.realtimeMode === 'NATIVE'"
          label="记忆总结模型"
          :required="needsSummaryModel(form.memoryMode)"
        >
          <el-select v-model="form.conversationModelId" class="w-full" filterable>
            <el-option
              v-for="item in modelsOfType('CHAT')"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="persistentMemory(form.memoryMode)" label="记忆权限">
          <el-checkbox v-model="form.memoryReadEnabled">允许读取</el-checkbox>
          <el-checkbox v-model="form.memoryWriteEnabled">允许保存</el-checkbox>
        </el-form-item>
        <el-form-item label="语音配置 JSON"
          ><el-input v-model="form.voiceConfigJson" type="textarea" :rows="2" placeholder="可留空"
        /></el-form-item>
      </template>

      <el-form-item label="状态"
        ><el-switch
          v-model="form.status"
          active-value="ENABLED"
          inactive-value="DISABLED"
          active-text="启用"
          inactive-text="停用"
      /></el-form-item>
    </el-form>
    <template #footer
      ><el-button @click="dialogVisible = false">取消</el-button
      ><el-button type="primary" :loading="saving" @click="save">保存</el-button></template
    >
  </el-dialog>

  <el-dialog
    v-model="bindingVisible"
    :title="`机器人绑定 · ${bindingAgent?.name || ''}`"
    width="min(620px, 94vw)"
  >
    <div class="mb-4 flex gap-2">
      <el-select
        v-model="selectedRobotId"
        class="min-w-0 flex-1"
        filterable
        remote
        :remote-method="searchRobots"
        placeholder="搜索机器人名称或编号"
        ><el-option
          v-for="robot in robotOptions"
          :key="robot.id"
          :label="`${robot.name} (${robot.robotCode})`"
          :value="robot.id"
      /></el-select>
      <el-checkbox v-model="defaultAgent">默认智能体</el-checkbox>
      <el-button type="primary" :disabled="!selectedRobotId" @click="bindRobot">绑定</el-button>
    </div>
    <el-table :data="bindings" empty-text="暂无绑定"
      ><el-table-column label="机器人" min-width="180"
        ><template #default="{ row }">{{ robotLabel(row.robotId) }}</template></el-table-column
      ><el-table-column label="默认" width="90"
        ><template #default="{ row }">{{
          row.defaultAgent ? '是' : '否'
        }}</template></el-table-column
      ><el-table-column label="操作" width="80"
        ><template #default="{ row }"
          ><el-button link type="danger" @click="unbindRobot(row.robotId)"
            >解绑</el-button
          ></template
        ></el-table-column
      ></el-table
    >
  </el-dialog>
</template>

<script lang="ts" setup>
import { MemoryProviderApi, type MemoryProviderStatus } from '@/api/ai/memory'
import { AgentApi, ModelApi, PromptApi, ProviderApi } from '@/api/ai/enterprise'
import type {
  Agent,
  AgentInput,
  AgentRobotBinding,
  Model,
  ModelInput,
  Prompt,
  PromptInput,
  Provider,
  ProviderInput,
  RecordStatus
} from '@/api/ai/enterprise'
import { RobotApi, type RobotVO } from '@/api/robot/robot'
import { hasPermission } from '@/directives/permission/hasPermi'

defineOptions({ name: 'AiEnterpriseManagement' })
type Tab = 'agents' | 'prompts' | 'models' | 'providers'
const message = useMessage()
const can = (permission: string) => hasPermission([permission])
const props = defineProps<{ initialTab?: Tab }>()
const activeTab = ref<Tab>(props.initialTab || 'agents')
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const bindingVisible = ref(false)
const editingId = ref<number | null>(null)
const memoryOptions = ref<MemoryProviderStatus[]>([
  { mode: 'NOMEM', provider: 'nomem', name: '关闭记忆', configured: true, saveMessageThreshold: 6 },
  {
    mode: 'MEM_LOCAL_SHORT',
    provider: 'mem_local_short',
    name: '本地短期记忆',
    configured: true,
    saveMessageThreshold: 6
  },
  {
    mode: 'LONG_TERM',
    provider: 'mysql',
    name: '数据库分类记忆（兼容）',
    configured: true,
    saveMessageThreshold: 6
  }
])
const persistentMemory = (mode: string) =>
  ['LONG_TERM', 'MEM_LOCAL_SHORT', 'MEM0AI', 'POWERMEM'].includes(mode)
const needsSummaryModel = (mode: string) => ['LONG_TERM', 'MEM_LOCAL_SHORT'].includes(mode)
const memoryDescription = computed(() => {
  const option = memoryOptions.value.find((item) => item.mode === form.memoryMode)
  const messages = option?.saveMessageThreshold || 6
  return `积累 ${messages} 条消息或会话结束后保存；明确要求记住时立即处理。记忆服务异常时仍可继续对话。`
})
const agents = ref<Agent[]>([])
const prompts = ref<Prompt[]>([])
const models = ref<Model[]>([])
const providers = ref<Provider[]>([])
const bindings = ref<AgentRobotBinding[]>([])
const bindingAgent = ref<Agent | null>(null)
const robotOptions = ref<RobotVO[]>([])
const selectedRobotId = ref<number | null>(null)
const defaultAgent = ref(false)
const modelTypes = ['CHAT', 'REALTIME_S2S', 'ASR', 'TTS', 'EMBEDDING']
const tabLabels: Record<Tab, string> = {
  agents: '智能体',
  prompts: '角色',
  models: '模型',
  providers: '服务商'
}
const tabDescriptions: Record<Tab, string> = {
  agents: '配置对话与实时语音能力，并绑定到机器人',
  prompts: '定义角色的行为和表达方式，提示词可随时修改',
  models: '登记可供智能体选择的模型',
  providers: '管理模型服务地址及访问密钥'
}
const tabLabel = computed(() => tabLabels[activeTab.value])
const tabDescription = computed(() => tabDescriptions[activeTab.value])
const createPermission = computed(
  () =>
    ({
      agents: 'ai:agent:create',
      prompts: 'ai:prompt:create',
      models: 'ai:model:create',
      providers: 'ai:provider:create'
    })[activeTab.value]
)
const emptyForm = () => ({
  name: '',
  code: '',
  description: '',
  providerType: 'QWEN',
  baseUrl: '',
  apiKey: '',
  configJson: '',
  providerId: null as number | null,
  modelCode: '',
  modelType: 'CHAT',
  capabilitiesJson: '',
  type: 'SYSTEM',
  content: '',
  systemPromptId: null as number | null,
  conversationModelId: null as number | null,
  realtimeModelId: null as number | null,
  asrModelId: null as number | null,
  ttsModelId: null as number | null,
  realtimeMode: 'NATIVE' as Agent['realtimeMode'],
  memoryMode: 'NONE' as Agent['memoryMode'],
  memoryReadEnabled: false,
  memoryWriteEnabled: false,
  voiceConfigJson: '',
  status: 'ENABLED' as RecordStatus
})
const form = reactive(emptyForm())
const promptTypeName = (type: string) =>
  ({
    SYSTEM: '对话角色',
    MEMORY_EXTRACT: '记忆提取',
    MEMORY_SUMMARY: '记忆摘要',
    TOOL_ROUTING: '工具路由'
  })[type] || type
const statusLabel = (status: string) => (status === 'ENABLED' ? '启用' : '停用')
const modeLabel = (mode: string) =>
  ({ NATIVE: '原生实时', CASCADE: '级联', AUTO: '自动' })[mode] || mode
const promptName = (id: number) => prompts.value.find((item) => item.id === id)?.name || '已删除'
const providerName = (id: number) =>
  providers.value.find((item) => item.id === id)?.name || '已删除'
const modelsOfType = (type: string) =>
  models.value.filter((item) => item.modelType === type && item.status === 'ENABLED')
const systemPrompts = computed(() =>
  prompts.value.filter((item) => item.type === 'SYSTEM' && item.status === 'ENABLED')
)
const robotLabel = (id: number) =>
  robotOptions.value.find((item) => item.id === id)?.name || '已删除的机器人'

const loadAll = async () => {
  loading.value = true
  try {
    if (can('ai:agent:query')) memoryOptions.value = await MemoryProviderApi.list()
    const [agentData, promptData, modelData, providerData] = await Promise.all([
      can('ai:agent:query') ? AgentApi.list() : Promise.resolve<Agent[]>([]),
      can('ai:prompt:query') ? PromptApi.list() : Promise.resolve<Prompt[]>([]),
      can('ai:model:query') ? ModelApi.list() : Promise.resolve<Model[]>([]),
      can('ai:provider:query') ? ProviderApi.list() : Promise.resolve<Provider[]>([])
    ])
    agents.value = agentData
    prompts.value = promptData
    models.value = modelData
    providers.value = providerData
  } catch {
    message.error('加载 AI 管理数据失败')
  } finally {
    loading.value = false
  }
}
const loadActive = () => loadAll()
const openCreate = () => {
  editingId.value = null
  Object.assign(form, emptyForm())
  dialogVisible.value = true
}
const openEdit = (row: Agent | Model | Provider | Prompt) => {
  editingId.value = row.id
  Object.assign(form, emptyForm(), row, { apiKey: '' })
  dialogVisible.value = true
}
const showPrompt = (row: Prompt) => {
  message.alert(row.content)
}
const validJson = (value: string, label: string) => {
  if (!value.trim()) return true
  try {
    JSON.parse(value)
    return true
  } catch {
    message.error(`${label}不是有效 JSON`)
    return false
  }
}
const save = async () => {
  if (
    !form.name.trim() ||
    (['agents', 'providers'].includes(activeTab.value) && !form.code.trim())
  ) {
    message.warning(
      ['agents', 'providers'].includes(activeTab.value) ? '请填写名称和编码' : '请填写名称'
    )
    return
  }
  if (
    !validJson(form.configJson, '高级配置') ||
    !validJson(form.capabilitiesJson, '能力配置') ||
    !validJson(form.voiceConfigJson, '语音配置')
  )
    return
  saving.value = true
  try {
    if (activeTab.value === 'providers') {
      if (!form.baseUrl.trim()) {
        message.warning('请填写接口地址')
        return
      }
      const data: ProviderInput = {
        name: form.name.trim(),
        code: form.code.trim(),
        providerType: form.providerType,
        baseUrl: form.baseUrl.trim(),
        apiKey: form.apiKey || undefined,
        configJson: form.configJson || null,
        status: form.status
      }
      if (editingId.value) await ProviderApi.update(editingId.value, data)
      else await ProviderApi.create(data)
    } else if (activeTab.value === 'models') {
      if (!form.providerId || !form.modelCode.trim()) {
        message.warning('请选择服务商并填写模型编码')
        return
      }
      const data: ModelInput = {
        name: form.name.trim(),
        providerId: form.providerId,
        modelCode: form.modelCode.trim(),
        modelType: form.modelType,
        capabilitiesJson: form.capabilitiesJson || null,
        configJson: form.configJson || null,
        status: form.status
      }
      if (editingId.value) await ModelApi.update(editingId.value, data)
      else await ModelApi.create(data)
    } else if (activeTab.value === 'prompts') {
      if (!form.content.trim()) {
        message.warning('请填写角色提示词')
        return
      }
      const data: PromptInput = {
        name: form.name.trim(),
        type: form.type,
        content: form.content,
        status: form.status
      }
      if (editingId.value) await PromptApi.update(editingId.value, data)
      else await PromptApi.create(data)
    } else {
      if (
        !form.systemPromptId ||
        (form.realtimeMode !== 'CASCADE' && !form.realtimeModelId) ||
        (form.realtimeMode !== 'NATIVE' &&
          (!form.conversationModelId || !form.asrModelId || !form.ttsModelId))
      ) {
        message.warning('请选齐当前语音模式需要的角色和模型')
        return
      }
      if (needsSummaryModel(form.memoryMode) && !form.conversationModelId) {
        message.warning('请选择记忆总结模型')
        return
      }
      const data: AgentInput = {
        name: form.name.trim(),
        code: form.code.trim(),
        description: form.description || null,
        systemPromptId: form.systemPromptId,
        conversationModelId:
          form.realtimeMode === 'NATIVE' && !persistentMemory(form.memoryMode)
            ? null
            : form.conversationModelId,
        realtimeModelId: form.realtimeMode === 'CASCADE' ? null : form.realtimeModelId,
        asrModelId: form.realtimeMode === 'NATIVE' ? null : form.asrModelId,
        ttsModelId: form.realtimeMode === 'NATIVE' ? null : form.ttsModelId,
        realtimeMode: form.realtimeMode,
        memoryMode: form.memoryMode,
        memoryReadEnabled: persistentMemory(form.memoryMode) && form.memoryReadEnabled,
        memoryWriteEnabled: persistentMemory(form.memoryMode) && form.memoryWriteEnabled,
        knowledgeEnabled: false,
        voiceConfigJson: form.voiceConfigJson || null,
        status: form.status
      }
      if (editingId.value) await AgentApi.update(editingId.value, data)
      else await AgentApi.create(data)
    }
    dialogVisible.value = false
    message.success('保存成功')
    await loadAll()
  } catch {
    /* axios displays the server's validation message */
  } finally {
    saving.value = false
  }
}
const removeRecord = async (row: Agent | Model | Provider) => {
  try {
    await message.confirm(`确定删除“${row.name}”？`)
    if (activeTab.value === 'agents') await AgentApi.remove(row.id)
    else if (activeTab.value === 'models') await ModelApi.remove(row.id)
    else await ProviderApi.remove(row.id)
    message.success('删除成功')
    await loadAll()
  } catch {
    /* canceled or handled by axios */
  }
}
const searchRobots = async (name: string) => {
  try {
    const result = await RobotApi.page({ pageNo: 1, pageSize: 50, name })
    robotOptions.value = result.list || []
  } catch {
    robotOptions.value = []
  }
}
const openBindings = async (agent: Agent) => {
  bindingAgent.value = agent
  selectedRobotId.value = null
  defaultAgent.value = false
  bindingVisible.value = true
  await Promise.all([refreshBindings(), searchRobots('')])
}
const refreshBindings = async () => {
  if (bindingAgent.value) bindings.value = await AgentApi.listRobots(bindingAgent.value.id)
}
const bindRobot = async () => {
  if (!bindingAgent.value || !selectedRobotId.value) return
  try {
    await AgentApi.bindRobot(bindingAgent.value.id, {
      robotId: selectedRobotId.value,
      defaultAgent: defaultAgent.value
    })
    selectedRobotId.value = null
    message.success('绑定成功')
    await refreshBindings()
  } catch {
    /* handled by axios */
  }
}
const unbindRobot = async (robotId: number) => {
  if (!bindingAgent.value) return
  try {
    await message.confirm('确定解绑这台机器人？')
    await AgentApi.unbindRobot(bindingAgent.value.id, robotId)
    await refreshBindings()
  } catch {
    /* canceled or handled by axios */
  }
}
onMounted(loadAll)
</script>
