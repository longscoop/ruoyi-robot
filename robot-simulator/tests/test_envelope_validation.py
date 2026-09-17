import json
import time

import pytest

from ruoyi_robot_simulator.envelope import Envelope


def valid_wire() -> dict:
    return {
        "messageId": "01K0A1B2C3D4E5F6G7H8J9K0MN", "requestId": "request-1",
        "timestamp": int(time.time() * 1000), "version": 1, "type": "MISSION_START",
        "source": "CLOUD", "data": {"missionId": 42, "payload": {"actions": []}},
    }


@pytest.mark.parametrize("field,value", [
    ("messageId", "bad-id"), ("requestId", ""), ("version", 2),
    ("type", "NOT_A_TYPE"), ("source", "DEVICE"), ("data", []),
])
def test_rejects_wire_metadata_java_codec_would_reject(field, value):
    wire = valid_wire()
    wire[field] = value
    with pytest.raises(ValueError):
        Envelope.from_bytes(json.dumps(wire).encode())


@pytest.mark.parametrize("field,value", [
    # JSON booleans are not Java integral values, even though bool subclasses int in Python.
    ("version", True), ("version", False), ("timestamp", True),
    # These values originate from untrusted MQTT payloads and must not leak a TypeError.
    ("type", True), ("source", False), ("messageId", 1), ("requestId", 1), ("data", True),
])
def test_rejects_boolean_and_noncanonical_envelope_field_types(field, value):
    wire = valid_wire()
    wire[field] = value
    with pytest.raises(ValueError):
        Envelope.from_bytes(json.dumps(wire).encode())


def test_rejects_far_future_timestamp_and_unknown_fields():
    future = valid_wire()
    future["timestamp"] += 600_000
    with pytest.raises(ValueError):
        Envelope.from_bytes(json.dumps(future).encode())
    extra = valid_wire()
    extra["ignored"] = True
    with pytest.raises(ValueError):
        Envelope.from_bytes(json.dumps(extra).encode())


def test_rejects_duplicate_json_keys_and_values_outside_java_long_range():
    duplicate = b'''{"messageId":"01K0A1B2C3D4E5F6G7H8J9K0MN","messageId":"01K0A1B2C3D4E5F6G7H8J9K0MN","requestId":"request-1","timestamp":1789041600000,"version":1,"type":"MISSION_START","source":"CLOUD","data":{"missionId":42,"payload":{"actions":[]}}}'''
    too_large_timestamp = valid_wire()
    too_large_timestamp["timestamp"] = 2**63

    with pytest.raises(ValueError):
        Envelope.from_bytes(duplicate)
    with pytest.raises(ValueError):
        Envelope.from_bytes(json.dumps(too_large_timestamp).encode())


@pytest.mark.parametrize("constant", ["NaN", "Infinity", "-Infinity"])
def test_rejects_nonfinite_json_constants_anywhere_in_the_payload(constant):
    payload = ("{\"messageId\":\"01K0A1B2C3D4E5F6G7H8J9K0MN\",\"requestId\":\"request-1\","
               "\"timestamp\":1789041600000,\"version\":1,\"type\":\"MISSION_START\","
               "\"source\":\"CLOUD\",\"data\":{\"missionId\":42,\"payload\":{\"actions\":[],\"value\":"
               + constant + "}}}")

    with pytest.raises(ValueError):
        Envelope.from_bytes(payload.encode())


@pytest.mark.parametrize("data", [
    {"missionId": True, "payload": {"actions": []}},
    {"missionId": 0, "payload": {"actions": []}},
    {"missionId": 42, "payload": {"actions": [{"id": True}]}},
    {"missionId": 42, "payload": {"actions": [{"id": 0}]}},
    {"missionId": 2**63, "payload": {"actions": []}},
    {"missionId": 42, "payload": {"actions": [{"id": 2**63}]}},
])
def test_rejects_nonpositive_or_boolean_command_identifiers(data):
    wire = valid_wire()
    wire["data"] = data
    with pytest.raises(ValueError):
        Envelope.from_bytes(json.dumps(wire).encode())
