<template>
  <ContentWrap
    ><div class="mb-12px flex gap-8px"
      ><el-button
        v-hasPermi="['robot:mission:create']"
        plain
        type="primary"
        @click="createRef.open()"
        ><Icon icon="ep:plus" class="mr-5px" />创建任务</el-button
      ><el-button @click="load">刷新</el-button></div
    ><el-table v-loading="loading" :data="missions" empty-text="暂无任务" stripe
      ><el-table-column label="任务号" prop="missionNo" min-width="170" /><el-table-column
        label="机器人"
        prop="robotId"
        width="100"
      /><el-table-column label="类型" prop="missionType" min-width="120" /><el-table-column
        label="状态"
        prop="status"
        min-width="110"
      /><el-table-column label="优先级" prop="priority" width="90" /><el-table-column
        label="操作"
        width="160"
        ><template #default="{ row }"
          ><el-button
            v-hasPermi="['robot:mission:query']"
            link
            type="primary"
            @click="detailRef.open(row.id)"
            >详情</el-button
          ><el-button :disabled="!canCancel(row.status)" link type="danger" @click="cancel(row.id)"
            >取消</el-button
          ></template
        ></el-table-column
      ></el-table
    ><Pagination
      :total="total"
      v-model:page="query.pageNo"
      v-model:limit="query.pageSize"
      @pagination="load" /></ContentWrap
  ><MissionCreateForm ref="createRef" @success="load" /><MissionDetail ref="detailRef" />
</template>

<script lang="ts" setup>
import { MissionApi, type MissionVO } from '@/api/robot/mission'
import { useUserStore } from '@/store/modules/user'
import MissionCreateForm from './MissionCreateForm.vue'
import MissionDetail from './MissionDetail.vue'
import { canCancelMission } from './missionPermissions'

defineOptions({ name: 'RobotMission' })
const loading = ref(false)
const missions = ref<MissionVO[]>([])
const total = ref(0)
const createRef = ref()
const detailRef = ref()
const user = useUserStore()
const query = reactive({ pageNo: 1, pageSize: 20 })
const canCancel = (status: string) => canCancelMission(status, user.getPermissions)
const load = async () => {
  loading.value = true
  try {
    const page = (await MissionApi.page(query)) as { list: MissionVO[]; total: number }
    missions.value = page.list
    total.value = page.total
  } finally {
    loading.value = false
  }
}
const cancel = async (id: number) => {
  try {
    const { value } = await useMessage().prompt('请输入取消原因（可选）', '取消任务')
    await MissionApi.cancel(id, value)
    await load()
  } catch {}
}
onMounted(load)
</script>
