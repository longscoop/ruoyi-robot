<template>
  <ContentWrap>
    <div class="mb-4 flex items-center justify-between">
      <h2 class="m-0 text-base font-semibold">知识库</h2>
      <el-button v-if="can('ai:knowledge:create')" type="primary" @click="openBase()"
        >新增知识库</el-button
      >
    </div>
    <el-table
      v-loading="loading"
      :data="bases"
      stripe
      empty-text="暂无知识库"
      highlight-current-row
      @current-change="selectBase"
    >
      <el-table-column label="名称" prop="name" min-width="170" />
      <el-table-column label="编码" prop="code" min-width="140" />
      <el-table-column label="描述" prop="description" min-width="220" show-overflow-tooltip />
      <el-table-column v-if="can('ai:document:query')" label="文档" width="100"
        ><template #default="{ row }"
          ><el-button link type="primary" @click.stop="selectBase(row)">查看</el-button></template
        ></el-table-column
      >
      <el-table-column label="操作" width="130"
        ><template #default="{ row }"
          ><el-button
            v-if="can('ai:knowledge:update')"
            link
            type="primary"
            @click.stop="openBase(row)"
            >编辑</el-button
          ><el-button
            v-if="can('ai:knowledge:delete')"
            link
            type="danger"
            @click.stop="removeBase(row)"
            >删除</el-button
          ></template
        ></el-table-column
      >
    </el-table>
  </ContentWrap>

  <ContentWrap v-if="can('ai:document:query')">
    <div class="mb-4 flex items-center justify-between">
      <h2 class="m-0 min-w-0 truncate text-base font-semibold">{{
        selectedBase ? `${selectedBase.name} · 文档` : '文档'
      }}</h2>
      <el-button
        v-if="can('ai:document:create')"
        type="primary"
        :disabled="!selectedBase"
        @click="openDocument()"
        >新增文档</el-button
      >
    </div>
    <el-table
      v-loading="documentsLoading"
      :data="documents"
      stripe
      :empty-text="selectedBase ? '暂无文档' : '请先选择知识库'"
    >
      <el-table-column label="标题" prop="title" min-width="230" />
      <el-table-column label="更新时间" prop="updateTime" min-width="180" />
      <el-table-column label="操作" width="140"
        ><template #default="{ row }"
          ><el-button
            v-if="can('ai:document:update')"
            link
            type="primary"
            @click="openDocument(row)"
            >编辑</el-button
          ><el-button
            v-if="can('ai:document:delete')"
            link
            type="danger"
            @click="removeDocument(row)"
            >删除</el-button
          ></template
        ></el-table-column
      >
    </el-table>
  </ContentWrap>

  <el-dialog
    v-model="baseDialog"
    :title="editingBaseId ? '编辑知识库' : '新增知识库'"
    width="min(560px, 94vw)"
    destroy-on-close
  >
    <el-form label-position="top" :model="baseForm">
      <el-form-item label="名称" required
        ><el-input v-model="baseForm.name" maxlength="128"
      /></el-form-item>
      <el-form-item label="编码" required
        ><el-input v-model="baseForm.code" maxlength="64"
      /></el-form-item>
      <el-form-item label="描述"
        ><el-input v-model="baseForm.description" type="textarea" :rows="3" maxlength="1000"
      /></el-form-item>
    </el-form>
    <template #footer
      ><el-button @click="baseDialog = false">取消</el-button
      ><el-button type="primary" :loading="saving" @click="saveBase">保存</el-button></template
    >
  </el-dialog>

  <el-dialog
    v-model="documentDialog"
    :title="editingDocumentId ? '编辑文档' : '新增文档'"
    width="min(820px, 96vw)"
    destroy-on-close
  >
    <el-form label-position="top" :model="documentForm">
      <el-form-item label="标题" required
        ><el-input v-model="documentForm.title" maxlength="255"
      /></el-form-item>
      <el-form-item label="正文" required
        ><el-input v-model="documentForm.content" type="textarea" :rows="18"
      /></el-form-item>
    </el-form>
    <template #footer
      ><el-button @click="documentDialog = false">取消</el-button
      ><el-button type="primary" :loading="saving" @click="saveDocument">保存</el-button></template
    >
  </el-dialog>
</template>

<script lang="ts" setup>
import { KnowledgeApi } from '@/api/ai/enterprise'
import type { KnowledgeBase, KnowledgeDocument } from '@/api/ai/enterprise'
import { hasPermission } from '@/directives/permission/hasPermi'

defineOptions({ name: 'AiKnowledgeManagement' })
const message = useMessage()
const can = (permission: string) => hasPermission([permission])
const loading = ref(false)
const documentsLoading = ref(false)
const saving = ref(false)
const bases = ref<KnowledgeBase[]>([])
const documents = ref<KnowledgeDocument[]>([])
const selectedBase = ref<KnowledgeBase | null>(null)
const baseDialog = ref(false)
const documentDialog = ref(false)
const editingBaseId = ref<number | null>(null)
const editingDocumentId = ref<number | null>(null)
const baseForm = reactive({ name: '', code: '', description: '' })
const documentForm = reactive({ title: '', content: '' })

const loadBases = async () => {
  loading.value = true
  try {
    bases.value = await KnowledgeApi.listBases()
    if (selectedBase.value) {
      selectedBase.value = bases.value.find((item) => item.id === selectedBase.value?.id) || null
      if (!selectedBase.value) documents.value = []
    }
  } catch {
    message.error('加载知识库失败')
  } finally {
    loading.value = false
  }
}
const selectBase = async (base: KnowledgeBase | null) => {
  if (!base || !can('ai:document:query')) return
  selectedBase.value = base
  documentsLoading.value = true
  try {
    documents.value = await KnowledgeApi.listDocuments(base.id)
  } catch {
    message.error('加载文档失败')
  } finally {
    documentsLoading.value = false
  }
}
const openBase = (base?: KnowledgeBase) => {
  editingBaseId.value = base?.id || null
  Object.assign(baseForm, {
    name: base?.name || '',
    code: base?.code || '',
    description: base?.description || ''
  })
  baseDialog.value = true
}
const saveBase = async () => {
  if (!baseForm.name.trim() || !baseForm.code.trim()) {
    message.warning('请填写知识库名称和编码')
    return
  }
  saving.value = true
  try {
    const data = {
      name: baseForm.name.trim(),
      code: baseForm.code.trim(),
      description: baseForm.description.trim() || null
    }
    if (editingBaseId.value) await KnowledgeApi.updateBase(editingBaseId.value, data)
    else await KnowledgeApi.createBase(data)
    baseDialog.value = false
    message.success('保存成功')
    await loadBases()
  } catch {
    /* axios displays the server message */
  } finally {
    saving.value = false
  }
}
const removeBase = async (base: KnowledgeBase) => {
  try {
    await message.confirm(`确定删除知识库“${base.name}”？请先删除其中所有文档。`)
    await KnowledgeApi.removeBase(base.id)
    message.success('删除成功')
    await loadBases()
  } catch {
    /* canceled or handled by axios */
  }
}
const openDocument = (document?: KnowledgeDocument) => {
  if (!selectedBase.value) return
  editingDocumentId.value = document?.id || null
  Object.assign(documentForm, { title: document?.title || '', content: document?.content || '' })
  documentDialog.value = true
}
const saveDocument = async () => {
  if (!selectedBase.value) return
  if (!documentForm.title.trim() || !documentForm.content.trim()) {
    message.warning('请填写文档标题和正文')
    return
  }
  saving.value = true
  try {
    const data = { title: documentForm.title.trim(), content: documentForm.content }
    if (editingDocumentId.value)
      await KnowledgeApi.updateDocument(selectedBase.value.id, editingDocumentId.value, data)
    else await KnowledgeApi.createDocument(selectedBase.value.id, data)
    documentDialog.value = false
    message.success('保存成功')
    await selectBase(selectedBase.value)
  } catch {
    /* axios displays the server message */
  } finally {
    saving.value = false
  }
}
const removeDocument = async (document: KnowledgeDocument) => {
  if (!selectedBase.value) return
  try {
    await message.confirm(`确定删除文档“${document.title}”？`)
    await KnowledgeApi.removeDocument(selectedBase.value.id, document.id)
    message.success('删除成功')
    await selectBase(selectedBase.value)
  } catch {
    /* canceled or handled by axios */
  }
}
onMounted(loadBases)
</script>
