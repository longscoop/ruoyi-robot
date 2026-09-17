<template>
  <Dialog v-model="visible" title="激活设备" width="500px">
    <el-alert :closable="false" title="激活后将创建机器人并显示一次性凭据。" type="warning" />
    <el-form ref="formRef" class="mt-16px" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="机器人编号" prop="robotCode"
        ><el-input v-model="form.robotCode"
      /></el-form-item>
      <el-form-item label="机器人名称" prop="robotName"
        ><el-input v-model="form.robotName"
      /></el-form-item>
    </el-form>
    <template #footer
      ><el-button @click="visible = false">取消</el-button
      ><el-button :loading="loading" type="primary" @click="activate">确认激活</el-button></template
    >
  </Dialog>
</template>

<script lang="ts" setup>
import {
  DeviceApi,
  type DeviceActivateReqVO,
  type DeviceCredentialResultVO
} from '@/api/device/device'

defineOptions({ name: 'RobotDeviceActivateForm' })
const emit = defineEmits<{ success: [credential: DeviceCredentialResultVO] }>()
const visible = ref(false)
const loading = ref(false)
const deviceId = ref<number>()
const formRef = ref()
const form = reactive<DeviceActivateReqVO>({ robotCode: '', robotName: '' })
const rules = {
  robotCode: [{ required: true, message: '请输入机器人编号', trigger: 'blur' }],
  robotName: [{ required: true, message: '请输入机器人名称', trigger: 'blur' }]
}
const open = (id: number) => {
  deviceId.value = id
  form.robotCode = ''
  form.robotName = ''
  visible.value = true
}
const activate = async () => {
  await formRef.value?.validate()
  if (!deviceId.value) return
  await useMessage().confirm('激活后会创建机器人，并且凭据仅显示一次，是否继续？')
  loading.value = true
  try {
    const credential = await DeviceApi.activate(deviceId.value, form)
    visible.value = false
    emit('success', credential)
  } finally {
    loading.value = false
  }
}
defineExpose({ open })
</script>
