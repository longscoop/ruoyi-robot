<template>
  <section class="device-credential-result" aria-live="polite">
    <el-alert
      :closable="false"
      show-icon
      title="凭据仅显示一次，请立即保存到安全的密钥管理系统"
      type="warning"
    />
    <el-descriptions :column="1" border class="mt-16px">
      <el-descriptions-item label="设备 ID">{{ credential.deviceId }}</el-descriptions-item>
      <el-descriptions-item label="机器人 ID">{{ credential.robotId }}</el-descriptions-item>
      <el-descriptions-item label="MQTT 用户名">{{ credential.mqttUsername }}</el-descriptions-item>
      <el-descriptions-item label="MQTT 密钥"
        ><code>{{ credential.mqttSecret }}</code></el-descriptions-item
      >
      <el-descriptions-item label="HTTP 密钥"
        ><code>{{ credential.httpSecret }}</code></el-descriptions-item
      >
      <el-descriptions-item label="凭据版本">{{
        credential.credentialVersion
      }}</el-descriptions-item>
    </el-descriptions>
  </section>
</template>

<script lang="ts" setup>
import type { DeviceCredentialResultVO } from '@/api/device/device'

defineOptions({ name: 'DeviceCredentialResult' })
defineProps<{ credential: DeviceCredentialResultVO }>()
</script>
