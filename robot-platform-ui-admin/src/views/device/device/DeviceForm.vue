<template>
  <Dialog v-model="visible" :title="form.id ? '编辑库存设备' : '设备入库'" width="520px">
    <el-form ref="formRef" v-loading="loading" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="产品 ID" prop="productId"
        ><el-input-number v-model="form.productId" :min="1"
      /></el-form-item>
      <el-form-item label="设备序列号" prop="deviceSn"
        ><el-input v-model="form.deviceSn"
      /></el-form-item>
      <el-form-item label="设备名称" prop="name"><el-input v-model="form.name" /></el-form-item>
    </el-form>
    <template #footer
      ><el-button @click="visible = false">取消</el-button
      ><el-button :loading="loading" type="primary" @click="submit">保存</el-button></template
    >
  </Dialog>
</template>

<script lang="ts" setup>
import { DeviceApi, type DeviceVO } from '@/api/device/device'

defineOptions({ name: 'RobotDeviceForm' })
const emit = defineEmits<{ success: [] }>()
const message = useMessage()
const visible = ref(false)
const loading = ref(false)
const formRef = ref()
const form = reactive<Pick<DeviceVO, 'id' | 'productId' | 'deviceSn' | 'name'>>({
  productId: 0,
  deviceSn: '',
  name: ''
})
const rules = {
  productId: [{ required: true, message: '请输入产品 ID', trigger: 'change' }],
  deviceSn: [{ required: true, message: '请输入设备序列号', trigger: 'blur' }],
  name: [{ required: true, message: '请输入设备名称', trigger: 'blur' }]
}
const open = async (device?: DeviceVO) => {
  visible.value = true
  Object.assign(form, {
    id: device?.id,
    productId: device?.productId || 0,
    deviceSn: device?.deviceSn || '',
    name: device?.name || ''
  })
}
const submit = async () => {
  await formRef.value?.validate()
  loading.value = true
  try {
    if (form.id) await DeviceApi.updateInventory(form.id, form)
    else await DeviceApi.create(form)
    message.success('保存成功')
    visible.value = false
    emit('success')
  } finally {
    loading.value = false
  }
}
defineExpose({ open })
</script>
