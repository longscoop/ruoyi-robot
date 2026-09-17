"""Canonical MQTT topic construction, kept identical to RobotTopic.value()."""

from __future__ import annotations

import re


_COMPONENT = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")


def robot_topic(tenant_namespace: str, product_key: str, device_sn: str, channel: str) -> str:
    values = (tenant_namespace, product_key, device_sn)
    if not all(_COMPONENT.fullmatch(value or "") for value in values):
        raise ValueError("invalid canonical robot topic component")
    normalised = channel.upper()
    if normalised not in {"COMMAND", "OTA", "STATE", "EVENT"}:
        raise ValueError("invalid canonical robot topic channel")
    return f"robot/{tenant_namespace}/{product_key}/{device_sn}/{normalised.lower()}"
