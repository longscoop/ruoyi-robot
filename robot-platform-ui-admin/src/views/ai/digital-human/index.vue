<template>
  <ContentWrap>
    <el-button type="primary" :disabled="!agents.length" @click="createNew">新增数字人</el-button>
    <el-alert v-if="!agents.length" type="info" :closable="false" title="请先在 AI 大模型的智能体管理中创建智能体" />
    <el-table :data="rows">
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="code" label="Code" />
      <el-table-column prop="avatarType" label="形象" />
      <el-table-column label="智能体"><template #default="{ row }">{{ agentName(row.agentId) }}</template></el-table-column>
      <el-table-column prop="voiceId" label="音色" />
      <el-table-column prop="status" label="状态" />
      <el-table-column label="操作"><template #default="{ row }">
        <el-button link @click="edit(row)">编辑</el-button>
        <el-button link @click="preview(row)">预览</el-button>
        <el-button link type="danger" @click="remove(row)">删除</el-button>
      </template></el-table-column>
    </el-table>
    <el-dialog v-model="open" title="数字人">
      <el-form :model="form" label-width="110px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="Code"><el-input v-model="form.code" /></el-form-item>
        <el-form-item label="智能体">
          <el-select v-model="form.agentId" filterable class="w-full" placeholder="选择 AI 大模型智能体">
            <el-option v-for="agent in agents" :key="agent.id" :label="agent.name" :value="agent.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="形象类型"><el-select v-model="form.avatarType">
          <el-option v-for="x in avatars" :key="x" :label="x" :value="x" />
        </el-select></el-form-item>
        <el-form-item label="形象 URL"><el-input v-model="form.avatarUrl" /></el-form-item>
        <el-form-item label="语音合成模型"><el-select v-model="form.voiceModelId" clearable filterable class="w-full">
          <el-option v-for="model in ttsModels" :key="model.id" :label="model.name" :value="model.id" />
        </el-select></el-form-item>
        <el-form-item label="音色"><el-input v-model="form.voiceId" /></el-form-item>
        <el-form-item label="口型"><el-select v-model="form.lipSyncMode">
          <el-option label="音量驱动" value="AUDIO_LEVEL" />
          <el-option label="Viseme" value="VISEME" />
          <el-option label="Provider" value="PROVIDER" />
        </el-select></el-form-item>
        <el-form-item label="欢迎语"><el-input v-model="form.welcomeText" /></el-form-item>
        <el-form-item label="允许打断"><el-switch v-model="form.interruptEnabled" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="open = false">取消</el-button>
        <el-button type="primary" :disabled="!form.agentId" @click="save">保存</el-button>
      </template>
    </el-dialog>
    <DigitalHumanPreview v-model="previewOpen" :config="previewConfig" />
  </ContentWrap>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { AiAgentApi, type AgentVO } from '@/api/ai/agent'
import { AiModelApi, type AiModelVO } from '@/api/ai/model'
import { DigitalHumanApi, type DigitalHumanPreviewVO, type DigitalHumanVO, type AvatarType } from '@/api/ai/digital-human'
import DigitalHumanPreview from './components/DigitalHumanPreview.vue'

const rows = ref<DigitalHumanVO[]>([])
const agents = ref<AgentVO[]>([])
const ttsModels = ref<AiModelVO[]>([])
const open = ref(false)
const editing = ref<number>()
const previewOpen = ref(false)
const previewConfig = ref<DigitalHumanPreviewVO>()
const avatars: AvatarType[] = ['STATIC_2D', 'LIVE2D', 'THREE_D', 'EXTERNAL']
const form = reactive<any>({ name: '', code: '', agentId: undefined, avatarType: 'STATIC_2D', lipSyncMode: 'AUDIO_LEVEL', interruptEnabled: true, status: 'ENABLED' })
const agentName = (id: number) => agents.value.find((agent) => agent.id === id)?.name || `#${id}`

async function load() { rows.value = await DigitalHumanApi.list() }
function createNew() {
  editing.value = undefined
  Object.assign(form, { name: '', code: '', agentId: undefined, avatarType: 'STATIC_2D', avatarUrl: '', voiceModelId: undefined, voiceId: '', lipSyncMode: 'AUDIO_LEVEL', welcomeText: '', interruptEnabled: true, status: 'ENABLED' })
  open.value = true
}
function edit(row: DigitalHumanVO) { editing.value = row.id; Object.assign(form, row); open.value = true }
async function save() {
  if (!form.agentId) return
  if (editing.value) await DigitalHumanApi.update(editing.value, form)
  else await DigitalHumanApi.create(form)
  open.value = false
  await load()
}
async function remove(row: DigitalHumanVO) { await DigitalHumanApi.delete(row.id); await load() }
async function preview(row: DigitalHumanVO) {
  previewConfig.value = await DigitalHumanApi.preview(row.id)
  previewOpen.value = true
}
onMounted(async () => {
  await load()
  const [agentResult, modelResult] = await Promise.allSettled([AiAgentApi.list(), AiModelApi.listModels()])
  if (agentResult.status === 'fulfilled') agents.value = agentResult.value
  if (modelResult.status === 'fulfilled') {
    ttsModels.value = modelResult.value.filter((model) => model.modelType === 'TTS')
  }
})
</script>
