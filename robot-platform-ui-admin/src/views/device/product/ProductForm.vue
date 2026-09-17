<template>
  <Dialog v-model="visible" :title="title" width="520px">
    <el-form ref="formRef" v-loading="loading" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="产品标识" prop="productKey"
        ><el-input v-model="form.productKey"
      /></el-form-item>
      <el-form-item label="产品名称" prop="name"><el-input v-model="form.name" /></el-form-item>
      <el-form-item label="状态" prop="status"
        ><el-radio-group v-model="form.status"
          ><el-radio :value="0">启用</el-radio><el-radio :value="1">停用</el-radio></el-radio-group
        ></el-form-item
      >
      <el-form-item v-if="isPlatformAdmin && !form.id" label="产品范围"
        ><el-checkbox v-model="publicProduct">平台公共产品</el-checkbox
        ><div class="text-12px text-gray-500">公共产品仅可由平台管理员维护。</div></el-form-item
      >
      <el-form-item label="备注"
        ><el-input v-model="form.remark" :rows="3" type="textarea"
      /></el-form-item>
    </el-form>
    <template #footer
      ><el-button @click="visible = false">取消</el-button
      ><el-button :loading="loading" type="primary" @click="submit">保存</el-button></template
    >
  </Dialog>
</template>

<script lang="ts" setup>
import {
  DeviceProductApi,
  type DeviceProductSaveReqVO,
  type DeviceProductVO
} from '@/api/device/product'
import { useUserStore } from '@/store/modules/user'

defineOptions({ name: 'RobotProductForm' })
const emit = defineEmits<{ success: [] }>()
const message = useMessage()
const visible = ref(false)
const loading = ref(false)
const title = ref('新增产品型号')
const user = useUserStore()
const isPlatformAdmin = computed(() => user.getRoles.includes('super_admin'))
const publicProduct = ref(false)
const formRef = ref()
const form = reactive<DeviceProductVO>({ productKey: '', name: '', status: 0, remark: '' })
const rules = {
  productKey: [{ required: true, message: '请输入产品标识', trigger: 'blur' }],
  name: [{ required: true, message: '请输入产品名称', trigger: 'blur' }]
}

/**
 * The list response is the editing source. Product visibility is deliberately
 * enforced by the list endpoint, so there is no unrestricted product-by-id call.
 */
const open = (product?: DeviceProductVO) => {
  visible.value = true
  title.value = product?.id ? '编辑产品型号' : '新增产品型号'
  Object.assign(form, {
    id: product?.id,
    productKey: product?.productKey || '',
    name: product?.name || '',
    status: product?.status ?? 0,
    remark: product?.remark || ''
  })
  publicProduct.value = false
}
const submit = async () => {
  await formRef.value?.validate()
  loading.value = true
  try {
    const data: DeviceProductSaveReqVO = {
      productKey: form.productKey,
      name: form.name,
      status: form.status,
      remark: form.remark,
      publicProduct: !form.id && isPlatformAdmin.value ? publicProduct.value : undefined
    }
    if (form.id) await DeviceProductApi.update(form.id, data)
    else await DeviceProductApi.create(data)
    message.success('保存成功')
    visible.value = false
    emit('success')
  } finally {
    loading.value = false
  }
}
defineExpose({ open })
</script>
