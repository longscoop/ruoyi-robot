from types import SimpleNamespace

import pytest

from ruoyi_robot_simulator.envelope import Envelope
from ruoyi_robot_simulator.mqtt_transport import PahoMqttTransport


class FakePublishInfo:
    def __init__(self, *, published: bool = True, error: Exception | None = None):
        self.published = published
        self.error = error
        self.wait_timeouts: list[float] = []

    def wait_for_publish(self, timeout: float) -> None:
        self.wait_timeouts.append(timeout)
        if self.error:
            raise self.error

    def is_published(self) -> bool:
        return self.published


class FakeClient:
    def __init__(self, result: FakePublishInfo, rc: int = 0):
        self.result = result
        self.rc = rc
        self.publishes: list[tuple[str, bytes, int]] = []

    def publish(self, topic: str, payload: bytes, qos: int):
        self.publishes.append((topic, payload, qos))
        self.result.rc = self.rc
        return self.result


class RecordingExecutor:
    def __init__(self):
        self.calls: list[tuple[object, tuple[object, ...]]] = []

    def submit(self, callback, *args):
        self.calls.append((callback, args))
        return ImmediateFuture()


class ImmediateFuture:
    def add_done_callback(self, callback):
        callback(self)

    def result(self):
        return None


class FailingFuture(ImmediateFuture):
    def result(self):
        raise RuntimeError("password=untrusted-secret")


class ShutdownExecutor:
    def __init__(self):
        self.arguments: tuple[bool, bool] | None = None

    def shutdown(self, *, wait: bool, cancel_futures: bool):
        self.arguments = (wait, cancel_futures)


class StopClient:
    def __init__(self):
        self.calls: list[str] = []

    def loop_stop(self):
        self.calls.append("loop_stop")

    def disconnect(self):
        self.calls.append("disconnect")


def transport(result: FakePublishInfo, *, rc: int = 0) -> tuple[PahoMqttTransport, FakeClient]:
    client = FakeClient(result, rc)
    instance = PahoMqttTransport.__new__(PahoMqttTransport)
    instance._mqtt = SimpleNamespace(MQTT_ERR_SUCCESS=0)
    instance.client = client
    instance.state_topic = "robot/t-1/product/SN-1/state"
    instance.publish_timeout_seconds = 1.25
    return instance, client


def state() -> Envelope:
    return Envelope.robot(request_id="request-1", message_type="HEARTBEAT", data={"robotId": 1})


def test_qos1_publish_waits_for_the_broker_acknowledgement():
    result = FakePublishInfo()
    publisher, client = transport(result)

    publisher.publish_state(state())

    assert client.publishes[0][0] == "robot/t-1/product/SN-1/state"
    assert client.publishes[0][2] == 1
    assert result.wait_timeouts == [1.25]


def test_qos1_publish_raises_when_broker_acknowledgement_times_out():
    result = FakePublishInfo(published=False)
    publisher, _ = transport(result)

    with pytest.raises(RuntimeError, match="timed out"):
        publisher.publish_state(state())

    assert result.wait_timeouts == [1.25]


def test_qos1_publish_converts_client_errors_to_transport_failure():
    publisher, _ = transport(FakePublishInfo(error=ValueError("broker rejected message")))

    with pytest.raises(RuntimeError, match="MQTT state publish failed"):
        publisher.publish_state(state())


def test_command_callback_is_dispatched_off_the_paho_network_thread():
    publisher, _ = transport(FakePublishInfo())
    executor = RecordingExecutor()
    publisher.command_topic = "robot/t-1/product/SN-1/command"
    publisher.command_executor = executor
    publisher.on_command = lambda envelope: None
    command = Envelope.command(message_id="01K0A1B2C3D4E5F6G7H8J9K0MN", message_type="MISSION_START",
                               mission_id=1, payload={"actions": []})

    publisher._message(None, None, SimpleNamespace(topic=publisher.command_topic, payload=command.to_bytes()))

    assert len(executor.calls) == 1
    assert executor.calls[0][1] == (command,)


def test_worker_failure_is_observed_without_logging_untrusted_error_details(caplog):
    publisher, _ = transport(FakePublishInfo())
    publisher.command_topic = "robot/t-1/product/SN-1/command"
    publisher.command_executor = SimpleNamespace(submit=lambda *_: FailingFuture())
    publisher.on_command = lambda envelope: None
    command = Envelope.command(message_id="01K0A1B2C3D4E5F6G7H8J9K0MN", message_type="MISSION_START",
                               mission_id=1, payload={"actions": []})

    publisher._message(None, None, SimpleNamespace(topic=publisher.command_topic, payload=command.to_bytes()))

    assert "command handler failed" in caplog.text
    assert "untrusted-secret" not in caplog.text


def test_stop_cancels_queued_commands_without_waiting_for_the_worker():
    publisher, _ = transport(FakePublishInfo())
    executor = ShutdownExecutor()
    client = StopClient()
    publisher.command_executor = executor
    publisher.client = client

    publisher.stop()

    assert executor.arguments == (False, True)
    assert client.calls == ["loop_stop", "disconnect"]
