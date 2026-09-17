<template>
  <Dialog v-model="visible" title="创建任务" width="680px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="机器人 ID" prop="robotId"
        ><el-input-number v-model="form.robotId" :min="1" /></el-form-item
      ><el-form-item label="任务类型" prop="missionType"
        ><el-input v-model="form.missionType" placeholder="例如 INSPECTION" /></el-form-item
      ><el-form-item label="优先级" prop="priority"
        ><el-slider v-model="form.priority" :max="100" /></el-form-item
      ><el-form-item label="计划时间"
        ><el-date-picker
          v-model="form.scheduledTime"
          type="datetime"
          value-format="YYYY-MM-DDTHH:mm:ss"
      /></el-form-item>
      <el-divider>动作</el-divider
      ><el-form-item
        v-for="(action, index) in form.actions"
        :key="index"
        :label="`动作 ${index + 1}`"
        ><div class="flex w-full gap-8px"
          ><el-input v-model="action.actionType" placeholder="动作类型" /><el-input
            v-model="action.parameters"
            placeholder="JSON 参数"
          /><el-button
            :disabled="form.actions.length === 1"
            type="danger"
            @click="form.actions.splice(index, 1)"
            >删除</el-button
          ></div
        ></el-form-item
      ><el-button plain @click="form.actions.push({ actionType: '', parameters: '{}' })"
        >添加动作</el-button
      >
    </el-form>
    <template #footer
      ><el-button @click="visible = false">取消</el-button
      ><el-button :loading="loading" type="primary" @click="submit">创建</el-button></template
    >
  </Dialog>
</template>

<script lang="ts" setup>
import { MissionApi, type MissionCreateVO } from '@/api/robot/mission'

defineOptions({ name: 'MissionCreateForm' })
const emit = defineEmits<{ success: [] }>()
const visible = ref(false)
const loading = ref(false)
const formRef = ref()
const blank = (): MissionCreateVO => ({
  robotId: 0,
  missionType: '',
  requestId: crypto.randomUUID(),
  priority: 50,
  actions: [{ actionType: '', parameters: '{}' }]
})
const form = reactive<MissionCreateVO>(blank())
const rules = {
  robotId: [{ required: true, message: '请选择机器人', trigger: 'change' }],
  missionType: [{ required: true, message: '请输入任务类型', trigger: 'blur' }]
}
const open = () => {
  Object.assign(form, blank())
  visible.value = true
}
const submit = async () => {
  await formRef.value?.validate()
  loading.value = true
  try {
    await MissionApi.create(form)
    visible.value = false
    emit('success')
  } finally {
    loading.value = false
  }
}
defineExpose({ open })
</script>
