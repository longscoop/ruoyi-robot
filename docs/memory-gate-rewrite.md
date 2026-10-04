# 长期记忆检索前门控与轻量改写

基于主干 `ef0377d`。不增加依赖、数据库字段或 LLM 请求；不改变保存、删除和 provider 的 namespace/filter。

## 调用链

`MemoryContextBuilder → MemoryRecallPlanner → MemoryProviderRegistry → provider → MemorySelection → Top K context`

- 空输入、完整的寒暄/致谢、明确通用知识/算术问题、显式忘记：不调用 provider。
- 涉及本人、历史偏好、过去决定的问题：召回。个人线索优先于通用问题规则，例如“我的工作是什么”仍召回。
- “它叫什么”等指代：仅使用本会话紧邻的已完成用户轮次，且该轮有个人线索、不是另一条指代、没有记忆管理指令。没有可靠上文则跳过长期检索，由已有短期对话处理；不猜测姓名或实体。
- 不确定的问题保留原召回行为，避免仅靠关键词白名单漏掉家庭设备、地点等问题。这是保守规则门控，不是完整意图分类器。
- 改写只去掉已知礼貌/回忆前缀、整理空白，或拼接上述可信用户上文；保留否定、实体和原问题。候选召回与 `MemorySelection` 使用同一条改写查询。
- 当前问题上限 2048 个 Java 字符，上文上限 256；超限不截断语义，直接跳过。仅在当前 session 保存一条上文，不使用助手回答，不共享跨用户缓存。

原候选量 `min(100, max(16, limit * 5))`、默认 Top 3、时间有效性及租户/机器人/成员范围保持不变。直接 registry 调用与旧 retriever fallback 都经过门控。namespace 校验在 registry 跳过查询前仍执行。

## 通信偏好与忘记

空输入不再隐式触发背景检索；显式使用 `queryBackground` / `buildBackgroundContext`。背景仅保留既有 `communicationPreference` 规则识别的记忆（例如回答简短、用中文），不加载宠物/职业等话题事实。

实时会话启动时读取一次通信偏好，跳过检索的轮次复用会话快照，不为寒暄或事实问答每轮发起远程查询。有非空主题召回时仍使用原二次过滤结果。显式忘记会立即清除本会话通信快照与指代上文；实际持久化删除继续走原 MemoryPipeline，门控不声称删除已成功。

快照不会主动轮询外部偏好变更，新会话重新加载。明确的新沟通要求仍在当前用户消息/短期对话中。未开启逐轮 instructions 更新的原生实时模型，仍只有启动背景路径；本改动不假设其具备动态门控能力。

## 延迟预算（目标，不是设备实测保证）

| 阶段 | 预算 | 说明 |
| --- | --- | --- |
| Gate + Rewrite | 暖机后 P95 ≤ 2 ms | 本地有界规则，0 次 LLM/embedding/网络请求；需在 RK3588/JVM 上复测 |
| 跳过的轮次 | 0 次 provider 请求 | 通信偏好直接读 session 快照 |
| 需要记忆的轮次 | 默认等待上限 300 ms | 沿用 query-deadline-millis；包括排队与 provider 调用，超时取消并降级为空 |
| 二次过滤与组装 | P95 ≤ 3 ms 目标 | 最多 100 条候选；受内容长度与 JVM 调度影响 |
| 会话启动背景 | 默认等待上限 300 ms，一次 | CASCADE 新增一次背景加载，移出正常每轮关键路径 |

常规单轮记忆阶段目标为约 305 ms 内完成；这不是硬实时保证。原配置仍将 provider 等待钳制在 50–2000 ms。线程取消不保证远端停止执行，既有有界线程池与队列继续负责隔离。

debug 日志只记录 gate 的 `retrieve` 与 `reason`；已有 recall timing 记录 provider 耗时，不新增原文、改写文本或记忆内容日志。上线比较跳过率、错误跳过样本和端到端首音 P50/P95，不能把 ASR/TTS 改善归因于此改动。

## 验证

```sh
mvn -pl robot-platform-module-ai -am test \
  -Dtest='*Memory*Test,*Realtime*Test,*Cascade*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false
```

新增用例覆盖闲聊、事实问答与个人问答对照、否定保留、指代改写/无上文、显式忘记、通信偏好、provider 零调用、namespace 校验、二次过滤及 session 隔离。原 provider 超时/异常、远端 filter 与本地隔离测试继续保留。

限制：仅内置少量中英文规则，不对所有事实问答保证零召回；`MemoryDirectiveParser` 原有宽匹配语义未改变。新增表达应通过误判样本与测试扩展，而非为每轮引入一次分类 LLM 调用。
