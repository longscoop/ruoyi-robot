# Digital Human Verification

日期：2026-09-20

## 完成范围

Task 1-12 的代码、测试契约、管理端、Realtime 协议、STATIC_2D renderer、菜单权限、模拟器和端侧协议均已提交。

## 静态自检

- DigitalHuman -> Agent/TTS Model 查询显式 tenant scoped。
- Admin DTO/preview snapshot 不包含 Provider secret/base credential。
- session.start 对 digitalHumanCode/clientCapabilities 向后兼容。
- runtime 已解析 digitalHumanCode，校验 Agent 一致性并发送 config/state。
- playback/tool/audio/error 有统一 Digital Human state 映射。
- 前端 API 不定义 secret 字段。
- STATIC_2D renderer 不直接访问 Agent/MQTT/ROS。
- 菜单权限与 Controller permission 一致。
- Android 工程不存在，因此按计划只提供 renderer protocol 文档，没有虚构客户端模块。

## 未声称通过的动态验证

当前 GitHub connector 没有可启动的新 CI workflow run；当前提交也没有 combined status checks。因而 Maven、Vitest/type-check、真实 MySQL DDL 和 mock-provider E2E 尚未在本次远程编辑会话中实际执行。不得将它们记录为 PASS。

合并门禁建议：

```bash
mvn -pl robot-platform-module-ai -am test
cd robot-platform-ui-admin && pnpm type-check && pnpm test
./scripts/e2e/realtime-agent.sh
```

E2E 数字人场景增加 `DIGITAL_HUMAN_CODE=<tenant digital human code>`。
