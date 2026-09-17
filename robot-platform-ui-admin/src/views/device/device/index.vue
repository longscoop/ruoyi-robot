<template>
  <ContentWrap>
    <div class="mb-12px flex gap-8px"
      ><el-button
        v-if="isPlatformAdmin"
        v-hasPermi="['device:device:create']"
        plain
        type="primary"
        @click="formRef.open()"
        ><Icon icon="ep:plus" class="mr-5px" />设备入库</el-button
      ><el-button @click="load">刷新</el-button></div
    >
    <el-table v-loading="loading" :data="devices" empty-text="暂无设备" stripe>
      <el-table-column label="序列号" prop="deviceSn" min-width="160" /><el-table-column
        label="名称"
        prop="name"
        min-width="150"
      /><el-table-column label="产品 ID" prop="productId" width="100" /><el-table-column
        label="生命周期"
        prop="lifecycleStatus"
        min-width="120"
      /><el-table-column
        label="凭据版本"
        prop="credentialVersion"
        min-width="100"
      /><el-table-column label="操作" width="260"
        ><template #default="{ row }"
          ><el-button
            v-if="row.lifecycleStatus === 'UNACTIVATED'"
            v-hasPermi="['device:device:activate']"
            link
            type="primary"
            @click="activateRef.open(row.id)"
            >激活</el-button
          ><el-button
            v-if="row.lifecycleStatus !== 'UNACTIVATED'"
            v-hasPermi="['device:device:rotate']"
            link
            type="warning"
            @click="rotate(row.id)"
            >轮换凭据</el-button
          ><el-button
            v-if="row.lifecycleStatus !== 'UNACTIVATED'"
            v-hasPermi="['device:device:unbind']"
            link
            type="danger"
            @click="unbind(row.id)"
            >解绑</el-button
          ><el-button
            v-if="isPlatformAdmin && row.lifecycleStatus === 'UNACTIVATED'"
            v-hasPermi="['device:device:update']"
            link
            @click="formRef.open(row)"
            >编辑</el-button
          ></template
        ></el-table-column
      >
    </el-table>
  </ContentWrap>
  <DeviceForm ref="formRef" @success="load" /><DeviceActivateForm
    ref="activateRef"
    @success="showCredential"
  />
  <Dialog
    v-model="credentialVisible"
    title="一次性设备凭据"
    width="640px"
    :close-on-click-modal="false"
    @closed="credential = undefined"
    ><DeviceCredentialResult v-if="credential" :credential="credential"
  /></Dialog>
</template>

<script lang="ts" setup>
import { DeviceApi, type DeviceCredentialResultVO, type DeviceVO } from '@/api/device/device'
import { useUserStore } from '@/store/modules/user'
import DeviceActivateForm from './DeviceActivateForm.vue'
import DeviceCredentialResult from './DeviceCredentialResult.vue'
import DeviceForm from './DeviceForm.vue'
import { canMaintainInventory } from './devicePermissions'

defineOptions({ name: 'RobotDevice' })
const loading = ref(false)
const devices = ref<DeviceVO[]>([])
const formRef = ref()
const activateRef = ref()
const credential = ref<DeviceCredentialResultVO>()
const credentialVisible = ref(false)
const user = useUserStore()
const isPlatformAdmin = computed(() => canMaintainInventory(user.getRoles))
const load = async () => {
  loading.value = true
  try {
    devices.value = await DeviceApi.list()
  } finally {
    loading.value = false
  }
}
const showCredential = async (result: DeviceCredentialResultVO) => {
  credential.value = result
  credentialVisible.value = true
  await load()
}
const rotate = async (id: number) => {
  try {
    await useMessage().confirm('轮换后旧凭据将立即失效，是否继续？')
    const result = await DeviceApi.rotateCredentials(id)
    await showCredential(result)
  } catch {}
}
const unbind = async (id: number) => {
  try {
    await useMessage().confirm('解绑后机器人将不再可访问，是否继续？')
    await DeviceApi.unbind(id)
    await load()
  } catch {}
}
onMounted(load)
</script>
