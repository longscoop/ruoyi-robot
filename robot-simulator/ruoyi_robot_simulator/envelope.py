"""The JSON envelope shared with the Java MQTT codec.

This module deliberately has no backend dependency: a simulator must exercise the public wire
contract, rather than call an application service directly.
"""

from __future__ import annotations

from dataclasses import dataclass
import json
import secrets
import time
import re
from typing import Any, Mapping


_CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
_ULID = re.compile(r"[0-7][0-9A-HJKMNP-TV-Z]{25}$")
_REQUEST_ID = re.compile(r"[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$")
_TYPES = {"HEARTBEAT", "MISSION_START", "MISSION_CANCEL", "MISSION_ACK", "MISSION_EVENT", "OTA_COMMAND"}
_SOURCES = {"CLOUD", "ROBOT"}
_EARLIEST_TIMESTAMP = 1_577_836_800_000
_MIN_SIGNED_LONG = -(2 ** 63)
_MAX_SIGNED_LONG = 2 ** 63 - 1


def _reject_duplicate_object_keys(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    """Mirror Jackson's strict duplicate detection instead of silently keeping the last key."""
    value: dict[str, Any] = {}
    for key, item in pairs:
        if key in value:
            raise ValueError("duplicate JSON object key")
        value[key] = item
    return value


def _reject_nonfinite_json_constant(value: str) -> None:
    """Jackson rejects NaN/Infinity; Python's json decoder accepts them unless explicitly told not to."""
    raise ValueError(f"non-finite JSON constant: {value}")


def is_positive_signed_long(value: Any) -> bool:
    # bool is an int subclass in Python, unlike Jackson's integral JSON nodes.
    return type(value) is int and 0 < value <= _MAX_SIGNED_LONG


def has_valid_mission_data(message_type: str, data: Mapping[str, Any]) -> bool:
    """Validate command/event identifiers which Java DTO binding treats as positive integers."""
    if message_type not in {"MISSION_START", "MISSION_CANCEL", "MISSION_ACK", "MISSION_EVENT"}:
        return True
    if not is_positive_signed_long(data.get("missionId")):
        return False
    if message_type == "MISSION_START":
        payload = data.get("payload")
        actions = payload.get("actions") if type(payload) is dict else None
        return type(actions) is list and all(
            type(action) is dict and is_positive_signed_long(action.get("id")) for action in actions
        )
    if message_type == "MISSION_EVENT":
        action_id = data.get("actionId")
        return action_id is None or is_positive_signed_long(action_id)
    return True


def new_message_id() -> str:
    """Return an uppercase, 26-character ULID compatible identifier."""
    value = int(time.time() * 1000)
    timestamp = ""
    for _ in range(10):
        timestamp = _CROCKFORD[value & 31] + timestamp
        value >>= 5
    return timestamp + "".join(secrets.choice(_CROCKFORD) for _ in range(16))


@dataclass(frozen=True)
class Envelope:
    message_id: str
    request_id: str
    timestamp: int
    version: int
    type: str
    source: str
    data: Mapping[str, Any]

    @classmethod
    def command(cls, *, message_id: str, message_type: str, mission_id: int,
                payload: Mapping[str, Any], request_id: str | None = None) -> "Envelope":
        """Make a cloud command suitable for fake transport tests."""
        data: dict[str, Any]
        if message_type == "MISSION_START":
            data = {"missionId": mission_id, "payload": dict(payload)}
        elif message_type == "MISSION_CANCEL":
            data = {"missionId": mission_id, **dict(payload)}
        else:
            raise ValueError(f"unsupported command type: {message_type}")
        if not has_valid_mission_data(message_type, data):
            raise ValueError("mission command lacks valid identifiers")
        return cls(message_id, request_id or message_id, int(time.time() * 1000), 1,
                   message_type, "CLOUD", data)

    @classmethod
    def robot(cls, *, request_id: str, message_type: str,
              data: Mapping[str, Any]) -> "Envelope":
        return cls(new_message_id(), request_id, int(time.time() * 1000), 1,
                   message_type, "ROBOT", dict(data))

    def to_bytes(self) -> bytes:
        """Serialize in Java record field names; compact output keeps broker traffic bounded."""
        if not has_valid_mission_data(self.type, self.data):
            raise ValueError("mission envelope lacks valid identifiers")
        return json.dumps({
            "messageId": self.message_id, "requestId": self.request_id,
            "timestamp": self.timestamp, "version": self.version,
            "type": self.type, "source": self.source, "data": self.data,
        }, separators=(",", ":"), ensure_ascii=False).encode("utf-8")

    @classmethod
    def from_bytes(cls, payload: bytes) -> "Envelope":
        try:
            raw = json.loads(payload.decode("utf-8"), object_pairs_hook=_reject_duplicate_object_keys,
                             parse_constant=_reject_nonfinite_json_constant)
        except (UnicodeDecodeError, json.JSONDecodeError, ValueError) as error:
            raise ValueError("malformed robot envelope") from error
        expected = {"messageId", "requestId", "timestamp", "version", "type", "source", "data"}
        now = int(time.time() * 1000)
        if (type(raw) is not dict or set(raw) != expected or type(raw["messageId"]) is not str
                or not _ULID.fullmatch(raw["messageId"]) or type(raw["requestId"]) is not str
                or not _REQUEST_ID.fullmatch(raw["requestId"]) or type(raw["timestamp"]) is not int
                or raw["timestamp"] < _MIN_SIGNED_LONG or raw["timestamp"] > _MAX_SIGNED_LONG
                or raw["timestamp"] < _EARLIEST_TIMESTAMP or raw["timestamp"] > now + 300_000
                # bool is an int subclass in Python, but a JSON boolean is not a Java int.
                or type(raw["version"]) is not int or raw["version"] != 1
                or type(raw["type"]) is not str or raw["type"] not in _TYPES
                or type(raw["source"]) is not str or raw["source"] not in _SOURCES
                or type(raw["data"]) is not dict or not has_valid_mission_data(raw["type"], raw["data"])):
            raise ValueError("invalid robot envelope")
        return cls(raw["messageId"], raw["requestId"], raw["timestamp"], raw["version"],
                   raw["type"], raw["source"], raw["data"])
