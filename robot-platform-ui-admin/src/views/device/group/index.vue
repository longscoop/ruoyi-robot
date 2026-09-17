<template>
  <ContentWrap
    ><div class="mb-12px flex gap-8px"
      ><el-button v-hasPermi="['device:group:create']" plain type="primary" @click="formRef.open()"
        ><Icon icon="ep:plus" class="mr-5px" />新增设备组</el-button
      ><el-button @click="load">刷新</el-button></div
    ><el-table v-loading="loading" :data="groups" empty-text="暂无设备组" stripe
      ><el-table-column label="名称" prop="name" min-width="180" /><el-table-column
        label="备注"
        prop="remark"
        min-width="220"
      /><el-table-column label="创建时间" prop="createTime" min-width="180" /><el-table-column
        label="操作"
        width="240"
        ><template #default="{ row }"
          ><el-button
            v-hasPermi="['device:group:update']"
            link
            type="primary"
            @click="formRef.open(row)"
            >编辑</el-button
          ><el-button
            v-hasPermi="['device:group:update']"
            link
            type="primary"
            @click="addMember(row.id)"
            >添加设备</el-button
          ><el-button
            v-hasPermi="['device:group:update']"
            link
            type="warning"
            @click="removeMember(row.id)"
            >移除设备</el-button
          ><el-button
            v-hasPermi="['device:group:delete']"
            link
            type="danger"
            @click="remove(row.id)"
            >删除</el-button
          ></template
        ></el-table-column
      ></el-table
    ></ContentWrap
  ><DeviceGroupForm ref="formRef" @success="load" />
</template>

<script lang="ts" setup>
import { DeviceGroupApi, type DeviceGroupVO } from '@/api/device/group'
import DeviceGroupForm from './DeviceGroupForm.vue'

defineOptions({ name: 'RobotDeviceGroup' })
const groups = ref<DeviceGroupVO[]>([])
const loading = ref(false)
const formRef = ref()
const load = async () => {
  loading.value = true
  try {
    groups.value = await DeviceGroupApi.list()
  } finally {
    loading.value = false
  }
}
const remove = async (id: number) => {
  try {
    await useMessage().delConfirm()
    await DeviceGroupApi.delete(id)
    await load()
  } catch {}
}
const memberId = async (title: string) => {
  const { value } = await useMessage().prompt('请输入设备 ID', title, {
    inputPattern: /^[1-9]\d*$/,
    inputErrorMessage: '请输入正整数设备 ID'
  })
  return value ? Number(value) : undefined
}
const addMember = async (groupId: number) => {
  try {
    const deviceId = await memberId('添加设备组成员')
    if (deviceId) await DeviceGroupApi.addDevice(groupId, deviceId)
  } catch {}
}
const removeMember = async (groupId: number) => {
  try {
    const deviceId = await memberId('移除设备组成员')
    if (deviceId) await DeviceGroupApi.removeDevice(groupId, deviceId)
  } catch {}
}
onMounted(load)
</script>
