<template>
  <Dialog v-model="visible" :title="form.id ? '编辑设备组' : '新增设备组'" width="500px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="80px"
      ><el-form-item label="名称" prop="name"><el-input v-model="form.name" /></el-form-item
      ><el-form-item label="备注"
        ><el-input v-model="form.remark" :rows="3" type="textarea" /></el-form-item
    ></el-form>
    <template #footer
      ><el-button @click="visible = false">取消</el-button
      ><el-button :loading="loading" type="primary" @click="submit">保存</el-button></template
    >
  </Dialog>
</template>

<script lang="ts" setup>
import { DeviceGroupApi, type DeviceGroupVO } from '@/api/device/group'

defineOptions({ name: 'RobotDeviceGroupForm' })
const emit = defineEmits<{ success: [] }>()
const visible = ref(false)
const loading = ref(false)
const formRef = ref()
const form = reactive<DeviceGroupVO>({ name: '', remark: '' })
const rules = { name: [{ required: true, message: '请输入设备组名称', trigger: 'blur' }] }
const open = (group?: DeviceGroupVO) => {
  Object.assign(form, { id: group?.id, name: group?.name || '', remark: group?.remark || '' })
  visible.value = true
}
const submit = async () => {
  await formRef.value?.validate()
  loading.value = true
  try {
    const data = { name: form.name, remark: form.remark }
    if (form.id) await DeviceGroupApi.update(form.id, data)
    else await DeviceGroupApi.create(data)
    visible.value = false
    emit('success')
  } finally {
    loading.value = false
  }
}
defineExpose({ open })
</script>
