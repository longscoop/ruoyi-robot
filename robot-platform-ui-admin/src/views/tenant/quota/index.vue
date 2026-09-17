<template>
  <ContentWrap>
    <el-skeleton :loading="loading" animated>
      <el-empty v-if="!quota" description="暂无配额数据" />
      <el-descriptions v-else :column="1" border title="当前租户机器人配额">
        <el-descriptions-item label="租户 ID">{{ quota.tenantId }}</el-descriptions-item>
        <el-descriptions-item label="机器人上限">{{ quota.robotLimit }}</el-descriptions-item>
        <el-descriptions-item label="当前用量"
          ><el-progress
            :percentage="usagePercent"
            :status="quota.robotUsed > quota.robotLimit ? 'exception' : undefined"
          />
          {{ quota.robotUsed }} / {{ quota.robotLimit }}</el-descriptions-item
        >
      </el-descriptions>
    </el-skeleton>
    <el-button class="mt-16px" @click="load">刷新</el-button>
    <el-button
      v-if="quota"
      v-hasPermi="['tenant:quota:update']"
      class="mt-16px"
      type="primary"
      @click="edit"
      >调整配额</el-button
    >
  </ContentWrap>
</template>

<script lang="ts" setup>
import { TenantQuotaApi, type TenantQuotaVO } from '@/api/tenant/quota'

defineOptions({ name: 'RobotTenantQuota' })
const loading = ref(false)
const quota = ref<TenantQuotaVO>()
const usagePercent = computed(() =>
  !quota.value?.robotLimit
    ? 0
    : Math.min(100, Math.round((quota.value.robotUsed / quota.value.robotLimit) * 100))
)
const load = async () => {
  loading.value = true
  try {
    quota.value = await TenantQuotaApi.getCurrent()
  } finally {
    loading.value = false
  }
}
const edit = async () => {
  if (!quota.value) return
  const { value } = await useMessage().prompt('请输入机器人上限', '调整租户配额', {
    inputValue: String(quota.value.robotLimit),
    inputPattern: /^\d+$/,
    inputErrorMessage: '请输入非负整数'
  })
  if (value != null) {
    await TenantQuotaApi.update(quota.value.tenantId, Number(value))
    await load()
  }
}
onMounted(load)
</script>
