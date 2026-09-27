#!/usr/bin/env bash
set -euo pipefail
: "${DEVICE_TOKEN:?DEVICE_TOKEN is required}"
AGENT_CODE="${AGENT_CODE:-qwen-native}"
case "$AGENT_CODE" in qwen-native|doubao-native|deepseek-cascade);; *) echo "unsupported AGENT_CODE" >&2; exit 2;; esac
python3 "$(dirname "$0")/realtime-agent-client.py" --agent-code "$AGENT_CODE" "$@"
