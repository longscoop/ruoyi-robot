# Realtime Agent live provider contracts
Live tests are opt-in and never contain credentials. Set `RUN_AI_PROVIDER_LIVE_TESTS=true` plus the provider variables before running tests.

Required variables: `QWEN_API_KEY`; `DOUBAO_ACCESS_TOKEN`, `DOUBAO_APP_ID`, `DOUBAO_RESOURCE_ID`; `DEEPSEEK_API_KEY`. Optional endpoint/model overrides are `QWEN_REALTIME_URL`, `QWEN_REALTIME_MODEL`, `DEEPSEEK_BASE_URL`, and `DEEPSEEK_MODEL`.

Run: `mvn -pl robot-platform-module-ai -Dtest='com.robot.platform.ai.live.*' test`. Without the opt-in flag these tests are skipped.
