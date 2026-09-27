# Realtime Agent E2E
The RK3588 simulator speaks only the platform WebSocket endpoint `/device-api/ai/realtime`; it never implements vendor protocols and never prints tokens.

Install Python `websockets`, export `DEVICE_TOKEN`, choose `AGENT_CODE=qwen-native`, `doubao-native`, or `deepseek-cascade`, then run `scripts/e2e/realtime-agent.sh`. Use `--interrupt` for interruption and `--member-id <id>` for the identified-member memory scenario. Repeat without member ID to verify anonymous sessions do not create MEMBER memory. Verify persistence through the tenant-scoped Conversation, Realtime Session, and Memory admin APIs.

Release checks: `mvn -pl robot-platform-server -am test`; then `cd robot-platform-ui-admin && pnpm install --frozen-lockfile && pnpm type-check && pnpm test --run`. Paid-provider tests remain opt-in.
