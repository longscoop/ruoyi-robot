#!/usr/bin/env bash
# Run the broker-dependent mission-loop verification without ever echoing credentials.
set -euo pipefail

required=(MYSQL_PASSWORD MYSQL_ROOT_PASSWORD EMQX_NODE_COOKIE EMQX_DASHBOARD_PASSWORD MQTT_CALLBACK_TOKEN
          MQTT_CLOUD_USERNAME MQTT_CLOUD_PASSWORD MQTT_USERNAME MQTT_SECRET)
for name in "${required[@]}"; do
  if [[ -z "${!name:-}" ]]; then
    printf 'required environment variable is missing: %s\n' "$name" >&2
    exit 2
  fi
done

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$root"

# Do not use `set -x`: this script deliberately keeps all secrets out of output and process logs.
docker compose up -d --wait mysql redis emqx

python_bin="${PYTHON_BIN:-python3}"
"$python_bin" -m pip install -e "./robot-simulator[test]" >/dev/null
(
  cd robot-simulator
  "$python_bin" -m pytest -q
)

# This integration test is opt-in because it connects to the live compose broker and starts a
# separate Python process. The test itself creates domain fixtures and verifies broker-only flow.
RUN_MQTT_MISSION_LOOP_IT=true \
MQTT_HOST="${MQTT_HOST:-127.0.0.1}" MQTT_PORT="${MQTT_PORT:-1883}" \
MQTT_PYTHON="${MQTT_PYTHON:-$python_bin}" \
mvn -pl robot-platform-server -am -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=MqttMissionLoopIT test

# The API portion intentionally uses pre-provisioned fixture identities. Provisioning users and
# device credentials is a product workflow, not a shell-script responsibility; keeping values in
# environment variables prevents credentials from being committed or printed. It is opt-in because
# this script does not start the application server itself.
if [[ "${RUN_CORE_PLATFORM_API_E2E:-false}" != "true" ]]; then
  printf '%s\n' 'API audience E2E skipped (set RUN_CORE_PLATFORM_API_E2E=true with fixture variables to run it).'
  exit 0
fi

api_required=(CORE_PLATFORM_API_BASE_URL ADMIN_ACCESS_TOKEN TENANT_B_ADMIN_ACCESS_TOKEN APP_ACCESS_TOKEN
              APP_LOGIN_BODY APP_TENANT_ID DEVICE_ACCESS_TOKEN TENANT_A_ROBOT_ID DEVICE_MISSION_ID
              DEVICE_HEARTBEAT_BODY DEVICE_ACK_BODY DEVICE_EVENT_BODY)
for name in "${api_required[@]}"; do
  if [[ -z "${!name:-}" ]]; then
    printf 'required API E2E environment variable is missing: %s\n' "$name" >&2
    exit 2
  fi
done

api_base="${CORE_PLATFORM_API_BASE_URL%/}"
secret_config="$(mktemp)"
secret_body="$(mktemp)"
chmod 600 "$secret_config" "$secret_body"
trap 'rm -f "$secret_config" "$secret_body"' EXIT

# Do not inherit fixture secrets into curl/Python child environments. Headers and request bodies
# are mode-600 files so neither bearer tokens nor member passwords appear in process arguments.
without_api_secrets() {
  env -u ADMIN_ACCESS_TOKEN -u TENANT_B_ADMIN_ACCESS_TOKEN -u APP_ACCESS_TOKEN \
    -u APP_LOGIN_BODY -u DEVICE_ACCESS_TOKEN -u DEVICE_HEARTBEAT_BODY \
    -u DEVICE_ACK_BODY -u DEVICE_EVENT_BODY "$@"
}

api_request() {
  local token="$1" method="$2" url="$3" body="${4:-}" tenant_id="${5:-}"
  : > "$secret_config"
  if [[ -n "$token" ]]; then
    printf 'header = "Authorization: Bearer %s"\n' "$token" >> "$secret_config"
  fi
  if [[ -n "$tenant_id" ]]; then
    printf 'header = "tenant-id: %s"\n' "$tenant_id" >> "$secret_config"
  fi
  if [[ -n "$body" ]]; then
    printf '%s' "$body" > "$secret_body"
    without_api_secrets curl --silent --show-error --max-time 20 --config "$secret_config" \
      --request "$method" --header 'Content-Type: application/json' --data-binary "@$secret_body" "$url"
  else
    without_api_secrets curl --silent --show-error --max-time 20 --config "$secret_config" \
      --request "$method" "$url"
  fi
}

# Validate the standard CommonResult envelope without printing its potentially sensitive body.
assert_api_success() {
  local check_name="$1" token="$2" method="$3" url="$4" body="${5:-}" tenant_id="${6:-}"
  local response
  if ! response="$(api_request "$token" "$method" "$url" "$body" "$tenant_id")"; then
    printf 'API E2E request failed: %s\n' "$check_name" >&2
    exit 1
  fi
  if ! printf '%s' "$response" | without_api_secrets "$python_bin" -c '
import json, sys
try:
    payload = json.load(sys.stdin)
except json.JSONDecodeError:
    sys.exit(1)
sys.exit(0 if payload.get("code") == 0 else 1)
'; then
    printf 'API E2E response was not successful: %s\n' "$check_name" >&2
    exit 1
  fi
}

# A tenant-B admin must not be able to read a tenant-A robot. It can be an HTTP denial or a
# business-level CommonResult error; either way, code zero is a security failure.
assert_api_rejected() {
  local check_name="$1" token="$2" method="$3" url="$4"
  local response
  response="$(api_request "$token" "$method" "$url")" || true
  if ! printf '%s' "$response" | without_api_secrets "$python_bin" -c '
import json, sys
try:
    payload = json.load(sys.stdin)
except json.JSONDecodeError:
    sys.exit(0)
sys.exit(1 if payload.get("code") == 0 else 0)
'; then
    printf 'API E2E access was unexpectedly accepted: %s\n' "$check_name" >&2
    exit 1
  fi
}

# Management audience: tenant-scoped dashboard must be populated only from tenant-A fixtures.
assert_api_success 'admin dashboard' "$ADMIN_ACCESS_TOKEN" GET "$api_base/admin-api/robot/dashboard"

# APP/H5 audience: exercise member onboarding/login, binding lookup, and bound robot status.
assert_api_success 'app member login' '' POST "$api_base/app-api/auth/login" "$APP_LOGIN_BODY" "$APP_TENANT_ID"
assert_api_success 'app member profile' "$APP_ACCESS_TOKEN" GET "$api_base/app-api/member/profile"
assert_api_success 'app robot bindings' "$APP_ACCESS_TOKEN" GET "$api_base/app-api/member/robot-bindings"
assert_api_success 'app bound robot status' "$APP_ACCESS_TOKEN" GET "$api_base/app-api/robots/$TENANT_A_ROBOT_ID/status"

# Device audience: heartbeat/config plus mission ACK and terminal event. Repeating the exact ACK
# and event verifies the duplicate-message/idempotency path against the real server.
assert_api_success 'device config' "$DEVICE_ACCESS_TOKEN" GET "$api_base/device-api/device/config"
assert_api_success 'device heartbeat' "$DEVICE_ACCESS_TOKEN" POST "$api_base/device-api/device/heartbeat" "$DEVICE_HEARTBEAT_BODY"
assert_api_success 'device mission ack' "$DEVICE_ACCESS_TOKEN" POST "$api_base/device-api/missions/$DEVICE_MISSION_ID/ack" "$DEVICE_ACK_BODY"
assert_api_success 'device duplicate mission ack' "$DEVICE_ACCESS_TOKEN" POST "$api_base/device-api/missions/$DEVICE_MISSION_ID/ack" "$DEVICE_ACK_BODY"
assert_api_success 'device mission terminal event' "$DEVICE_ACCESS_TOKEN" POST "$api_base/device-api/missions/$DEVICE_MISSION_ID/events" "$DEVICE_EVENT_BODY"
assert_api_success 'device duplicate terminal event' "$DEVICE_ACCESS_TOKEN" POST "$api_base/device-api/missions/$DEVICE_MISSION_ID/events" "$DEVICE_EVENT_BODY"

assert_api_rejected 'tenant B reads tenant A robot' "$TENANT_B_ADMIN_ACCESS_TOKEN" GET \
  "$api_base/admin-api/robot/robots/$TENANT_A_ROBOT_ID"
printf '%s\n' 'Core platform API audience E2E passed.'
