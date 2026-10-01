<template>
  <ContentWrap>
    <div class="mb-4 flex gap-2"
      ><el-button type="primary" :disabled="!agents.length" @click="createNew">新增数字人</el-button
      ><el-button :loading="loading" @click="load">刷新</el-button></div
    >
    <el-alert
      v-if="!agents.length"
      type="info"
      :closable="false"
      title="请先在智能体管理中创建智能体"
    />
    <el-table :data="rows" v-loading="loading">
      <el-table-column prop="name" label="名称" min-width="150" />
      <el-table-column label="渲染服务" min-width="140">
        <template #default="{ row }">{{ renderName(row) }}</template>
      </el-table-column>
      <el-table-column label="形象" width="90"
        ><template #default="{ row }"
          ><el-image
            v-if="row.avatarUrl"
            :src="row.avatarUrl"
            :preview-src-list="[row.avatarUrl]"
            preview-teleported
            fit="contain"
            class="h-12 w-12"
          /><span v-else>未上传</span></template
        ></el-table-column
      >
      <el-table-column label="智能体" min-width="160"
        ><template #default="{ row }">{{ name('agents', row.agentId) }}</template></el-table-column
      >
      <el-table-column label="语音合成模型" min-width="170"
        ><template #default="{ row }">{{
          row.voiceModelId ? name('models', row.voiceModelId) : '跟随智能体'
        }}</template></el-table-column
      >
      <el-table-column label="音色" min-width="160"
        ><template #default="{ row }">{{ voiceName(row) }}</template></el-table-column
      >
      <el-table-column label="状态" width="90"
        ><template #default="{ row }">{{ statusName(row.status) }}</template></el-table-column
      >
      <el-table-column label="操作" width="170"
        ><template #default="{ row }"
          ><el-button link type="primary" @click="edit(row)">编辑</el-button
          ><el-button link @click="preview(row)">预览</el-button
          ><el-button link type="danger" @click="remove(row)">删除</el-button></template
        ></el-table-column
      >
    </el-table>
    <el-dialog
      v-model="open"
      :title="editing ? '编辑数字人' : '新增数字人'"
      width="min(650px, 95vw)"
      destroy-on-close
    >
      <el-form :model="form" label-position="top">
        <el-form-item label="名称" required
          ><el-input v-model="form.name" maxlength="128"
        /></el-form-item>
        <el-form-item label="智能体" required
          ><el-select
            v-model="form.agentId"
            @change="form.voiceId = ''"
            filterable
            class="w-full"
            placeholder="选择智能体"
            ><el-option
              v-for="item in agents"
              :key="item.id"
              :label="item.name"
              :value="item.id" /></el-select
        ></el-form-item>
        <el-form-item label="渲染服务">
          <el-select v-model="rendering.provider" class="w-full" @change="changeRenderer">
            <el-option label="内置静态形象" value="BUILTIN" />
            <el-option label="LiveTalking 实时数字人" value="LIVETALKING" />
          </el-select>
        </el-form-item>
        <template v-if="rendering.provider === 'LIVETALKING'">
          <el-form-item label="服务实例" required>
            <el-select v-model="rendering.service" class="w-full" placeholder="选择已配置的服务">
              <el-option
                v-for="item in renderServices"
                :key="item.id"
                :label="item.name"
                :value="item.id"
              />
            </el-select>
            <span v-if="!renderServices.length" class="mt-1 text-xs text-gray-500"
              >尚无可用实例，请先在后端启用 LiveTalking 服务。</span
            >
          </el-form-item>
          <el-form-item label="形象 ID">
            <el-input
              v-model="rendering.avatarId"
              placeholder="例如 wav2lip256_avatar1，留空使用服务默认形象"
              maxlength="128"
            />
          </el-form-item>
          <el-alert
            type="info"
            :closable="false"
            class="mb-4"
            title="实时对话使用智能体生成的声音驱动口型；切换服务实例可切换底层模型。"
          />
        </template>
        <el-form-item
          :label="rendering.provider === 'BUILTIN' ? '形象图片' : '封面图片（可选）'"
          :required="rendering.provider === 'BUILTIN'"
        >
          <UploadImg
            v-model="form.avatarUrl"
            directory="digital-human"
            :file-type="['image/png', 'image/jpeg', 'image/webp']"
            :file-size="5"
            width="180px"
            height="180px"
            ><template #tip>上传 PNG、JPG 或 WebP 图片，最大 5 MB</template></UploadImg
          >
        </el-form-item>
        <el-form-item label="语音合成模型"
          ><el-select
            v-model="form.voiceModelId"
            @change="form.voiceId = ''"
            clearable
            filterable
            class="w-full"
            placeholder="跟随智能体语音配置"
            ><el-option
              v-for="model in ttsModels"
              :key="model.id"
              :label="model.name"
              :value="model.id" /></el-select
          ><span v-if="nativeAgent" class="mt-1 text-xs text-gray-500"
            >当前智能体使用实时语音模型；语音合成模型用于级联语音，音色会用于当前实时回答。</span
          ></el-form-item
        >
        <el-form-item label="音色"
          ><el-select
            v-model="form.voiceId"
            clearable
            filterable
            allow-create
            default-first-option
            class="w-full"
            placeholder="选择音色，留空跟随智能体"
            ><el-option
              v-for="voice in voiceOptions"
              :key="voice.id"
              :label="voice.name"
              :value="voice.id" /></el-select
        ></el-form-item>
        <el-form-item label="欢迎语"
          ><el-input v-model="form.welcomeText" placeholder="例如：你好，有什么可以帮你？"
        /></el-form-item>
        <el-form-item label="允许打断"><el-switch v-model="form.interruptEnabled" /></el-form-item>
      </el-form>
      <template #footer
        ><el-button @click="open = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="save">保存</el-button></template
      >
    </el-dialog>
    <DigitalHumanPreview v-model="previewOpen" :config="previewConfig" />
  </ContentWrap>
</template>
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { AiAgentApi, type AgentVO } from '@/api/ai/agent'
import {
  DigitalHumanApi,
  type DigitalHumanPreviewVO,
  type DigitalHumanVO,
  type RenderServiceOption
} from '@/api/ai/digital-human'
import { UploadImg } from '@/components/UploadFile'
import { useAiNames, statusName } from '@/api/ai/display'
import DigitalHumanPreview from './components/DigitalHumanPreview.vue'
import {
  defaultRenderSettings,
  readRenderSettings,
  writeRenderSettings
} from './runtime/render-settings'
const { options, name, loadNames } = useAiNames()
const message = useMessage()
const rows = ref<DigitalHumanVO[]>([])
const agents = ref<AgentVO[]>([])
const open = ref(false)
const loading = ref(false)
const saving = ref(false)
const editing = ref<number>()
const previewOpen = ref(false)
const previewConfig = ref<DigitalHumanPreviewVO>()
const rendering = reactive(defaultRenderSettings())
const renderServices = ref<RenderServiceOption[]>([])
const empty = () => ({
  name: '',
  code: '',
  agentId: undefined as number | undefined,
  avatarType: 'STATIC_2D' as DigitalHumanVO['avatarType'],
  avatarUrl: '',
  voiceModelId: undefined as number | undefined,
  voiceId: '',
  lipSyncMode: 'AUDIO_LEVEL' as DigitalHumanVO['lipSyncMode'],
  configJson: undefined as string | undefined,
  welcomeText: '',
  interruptEnabled: true,
  status: 'ENABLED'
})
const form = reactive(empty())
function changeRenderer() {
  form.avatarType = rendering.provider === 'BUILTIN' ? 'STATIC_2D' : 'EXTERNAL'
  form.lipSyncMode = rendering.provider === 'BUILTIN' ? 'AUDIO_LEVEL' : 'PROVIDER'
  if (!renderServices.value.some((item) => item.id === rendering.service))
    rendering.service = renderServices.value[0]?.id || 'default'
}
function renderName(row: DigitalHumanVO) {
  try {
    const value = readRenderSettings(row.configJson)
    return value.provider === 'BUILTIN'
      ? '内置静态形象'
      : renderServices.value.find((item) => item.id === value.service)?.name || value.provider
  } catch {
    return '配置无效'
  }
}
const ttsModels = computed(() =>
  options.value.models.filter((model) => model.modelType === 'TTS' && model.status === 'ENABLED')
)
const agent = computed(() => agents.value.find((item) => item.id === form.agentId))
const nativeAgent = computed(() => agent.value?.realtimeMode === 'NATIVE')
const voiceOptions = computed(() => {
  const modelId = nativeAgent.value
    ? agent.value?.realtimeModelId
    : form.voiceModelId || agent.value?.ttsModelId || agent.value?.realtimeModelId
  return options.value.models.find((model) => model.id === modelId)?.voices || []
})
function voiceName(row: DigitalHumanVO) {
  const selected = agents.value.find((item) => item.id === row.agentId)
  const model = options.value.models.find(
    (item) =>
      item.id ===
      (selected?.realtimeMode === 'NATIVE'
        ? selected.realtimeModelId
        : row.voiceModelId || selected?.ttsModelId)
  )
  return row.voiceId
    ? model?.voices.find((voice) => voice.id === row.voiceId)?.name || row.voiceId
    : '跟随智能体'
}
async function load() {
  loading.value = true
  try {
    const [data, agentData, serviceData] = await Promise.all([
      DigitalHumanApi.list(),
      AiAgentApi.list(),
      DigitalHumanApi.renderServices(),
      loadNames()
    ])
    rows.value = data
    renderServices.value = serviceData
    agents.value = agentData.filter((item) => item.status === 'ENABLED')
  } finally {
    loading.value = false
  }
}
function createNew() {
  editing.value = undefined
  Object.assign(form, empty())
  Object.assign(rendering, defaultRenderSettings())
  open.value = true
}
function edit(row: DigitalHumanVO) {
  try {
    Object.assign(rendering, readRenderSettings(row.configJson))
  } catch {
    message.error('数字人配置格式无效，请先修复配置')
    return
  }
  editing.value = row.id
  Object.assign(form, empty(), row)
  open.value = true
}
async function save() {
  if (!form.name.trim() || !form.agentId || (rendering.provider === 'BUILTIN' && !form.avatarUrl)) {
    message.warning('请填写名称、选择智能体；内置形象需要上传图片')
    return
  }
  if (
    rendering.provider === 'LIVETALKING' &&
    !renderServices.value.some((item) => item.id === rendering.service)
  ) {
    message.warning('请选择可用的 LiveTalking 服务实例')
    return
  }
  if (rendering.avatarId && !/^[a-zA-Z0-9_-]{1,128}$/.test(rendering.avatarId.trim())) {
    message.warning('形象 ID 只支持字母、数字、下划线和短横线')
    return
  }
  saving.value = true
  try {
    const data = {
      ...form,
      configJson: writeRenderSettings(form.configJson, rendering),
      name: form.name.trim(),
      agentId: form.agentId,
      code: form.code || `avatar-${crypto.randomUUID()}`,
      voiceModelId: form.voiceModelId || null,
      voiceId: form.voiceId || null
    }
    if (editing.value) await DigitalHumanApi.update(editing.value, data)
    else await DigitalHumanApi.create(data)
    message.success('保存成功')
    open.value = false
    await load()
  } finally {
    saving.value = false
  }
}
async function remove(row: DigitalHumanVO) {
  await message.confirm(`确定删除“${row.name}”？`)
  await DigitalHumanApi.delete(row.id)
  await load()
}
async function preview(row: DigitalHumanVO) {
  previewConfig.value = await DigitalHumanApi.preview(row.id)
  previewOpen.value = true
}
onMounted(load)
</script>
