<template>
  <ContentWrap>
    <div class="mb-12px flex gap-8px">
      <el-button v-hasPermi="['device:product:create']" plain type="primary" @click="formRef.open()"
        ><Icon icon="ep:plus" class="mr-5px" />新增产品型号</el-button
      >
      <el-button @click="load">刷新</el-button>
    </div>
    <el-table v-loading="loading" :data="products" empty-text="暂无可见产品型号" stripe>
      <el-table-column label="产品标识" prop="productKey" min-width="160" />
      <el-table-column label="名称" prop="name" min-width="160" />
      <el-table-column label="范围" min-width="100"
        ><template #default="{ row }"
          ><el-tag :type="row.tenantId == null ? 'warning' : 'success'">{{
            row.tenantId == null ? '平台公共' : '租户私有'
          }}</el-tag></template
        ></el-table-column
      >
      <el-table-column label="状态" min-width="90"
        ><template #default="{ row }"
          ><el-tag :type="row.status === 0 ? 'success' : 'info'">{{
            row.status === 0 ? '启用' : '停用'
          }}</el-tag></template
        ></el-table-column
      >
      <el-table-column label="备注" prop="remark" min-width="180" />
      <el-table-column label="操作" width="150"
        ><template #default="{ row }"
          ><el-button
            v-if="canMaintain(row)"
            v-hasPermi="['device:product:update']"
            link
            type="primary"
            @click="formRef.open(row)"
            >编辑</el-button
          ><el-button
            v-if="canMaintain(row)"
            v-hasPermi="['device:product:delete']"
            link
            type="danger"
            @click="remove(row.id)"
            >删除</el-button
          ></template
        ></el-table-column
      >
    </el-table>
  </ContentWrap>
  <ProductForm ref="formRef" @success="load" />
</template>

<script lang="ts" setup>
import { DeviceProductApi, type DeviceProductVO } from '@/api/device/product'
import { useUserStore } from '@/store/modules/user'
import ProductForm from './ProductForm.vue'
import { canMaintainProduct } from './productPermissions'

defineOptions({ name: 'RobotDeviceProduct' })
const products = ref<DeviceProductVO[]>([])
const loading = ref(false)
const formRef = ref()
const user = useUserStore()
const canMaintain = (product: DeviceProductVO) => canMaintainProduct(product, user.getRoles)
const load = async () => {
  loading.value = true
  try {
    products.value = await DeviceProductApi.list()
  } finally {
    loading.value = false
  }
}
const remove = async (id: number) => {
  try {
    await useMessage().delConfirm()
    await DeviceProductApi.delete(id)
    await load()
  } catch {}
}
onMounted(load)
</script>
