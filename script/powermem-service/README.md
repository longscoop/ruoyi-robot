# PowerMem 本机适配服务

Java 平台通过 `/query`、`/save`、`/forget` 调用真实 PowerMem 0.5.3 UserMemory SDK。支持分类用户画像、向量检索和 SDK 遗忘/衰减策略；所有请求要求 `Authorization: Bearer <POWER_MEM_SERVICE_TOKEN>`，namespace 必须由平台从可信租户、智能体、机器人及已验证身份生成。

平台端设置 `ROBOT_MEMORY_POWERMEM_ENABLED=true`、`ROBOT_MEMORY_POWERMEM_BASE_URL=http://127.0.0.1:8010`，并通过 `POWER_MEM_SERVICE_TOKEN` 为平台和适配服务配置相同 token，然后在智能体配置中选择 PowerMem。

`config.example.json` 仅含环境变量占位符。实际 `config.json`、`.venv` 和 `data` 不入库。默认使用 SQLite、通义 LLM 和 Embedding，云端推理仍需要对应凭据。数据存放本地与完全离线推理是不同配置。

```sh
python3.11 -m venv .venv
.venv/bin/pip install -r requirements.txt
cp config.example.json config.json
# 通过环境配置服务 token、LLM/Embedding API Key
.venv/bin/python service.py --config config.json
.venv/bin/python -m pytest -q test_service.py
```

禁止通过 ASSISTANT 生成用户事实；服务只接收 USER 陈述，画像使用身份、偏好、情景、工作、关系主题。语音遗忘只删除授权范围内规范化后完全匹配的事实；删除时重置画像，避免残留已删除事实。

查询只返回逐条检索事实，不返回完整画像。查询遇到 SDK 正忙时立即使用同问题的有效缓存或返回空结果，不等待后台总结；缓存按 namespace 和问题隔离，保存或遗忘时失效。
