import asyncio
import base64
from contextlib import asynccontextmanager
import hashlib
import hmac
import json
from pathlib import Path
import struct
import sys

import aiohttp
from aiohttp import web, WSMsgType
from aiohttp.test_utils import TestServer
import opuslib
import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from bridge import AudioCodec, Config, Conversation, Device, VoiceActivity, create_app, token_request, is_exit_command


DEVICE = Device("aa:bb:cc:dd:ee:ff", "test-board", "http-secret", "test-agent", "a" * 43, "b" * 43)
HELLO = {"type": "hello", "version": 1, "transport": "websocket",
         "audio_params": {"format": "opus", "sample_rate": 16000, "channels": 1, "frame_duration": 60}}
HEADERS = {"Device-Id": DEVICE.mac, "Authorization": "Bearer " + DEVICE.websocket_token}


def test_hmac_matches_java_device_token_canonical_format():
    request = token_request(DEVICE, 1234567890, "test-nonce")
    expected = hmac.new(b"http-secret", b"POST\n/device-api/auth/token\ntest-board\n1234567890\ntest-nonce", hashlib.sha256).digest()
    assert request["signature"] == base64.urlsafe_b64encode(expected).decode().rstrip("=")
    assert len(request["signature"]) == 43


def test_real_opus_roundtrip_and_partial_output_frames():
    audio = AudioCodec()
    pcm = struct.pack("<1440h", *[2000 if i % 80 < 40 else -2000 for i in range(1440)])
    assert audio.encode(pcm[:100]) == []
    packets = audio.encode(pcm[100:])
    assert len(packets) == 1
    decoded = opuslib.Decoder(24000, 1).decode(packets[0], 2880)
    assert len(decoded) == len(pcm)
    assert any(decoded)
    assert len(audio.encode(pcm[:200], final=True)) == 1
    audio.reset_output()
    assert audio.encode(final=True) == []


def test_vad_handles_unaligned_packets_and_silence():
    vad = VoiceActivity()
    assert not vad.feed(b"\0" * 100)
    assert not vad.feed(b"\0" * 63900)
    assert vad.duration_ms == 2000
    assert vad.voiced_ms == 0


def test_config_rejects_example_credentials(tmp_path):
    path = tmp_path / "config.json"
    example = Path(__file__).resolve().parents[1] / "config.example.json"
    path.write_text(example.read_text())
    with pytest.raises(ValueError, match="Configure device field"):
        Config.load(path)


@asynccontextmanager
async def stack(idle_timeout=30, transcript="你好", closed_reason=None, fail_first=False):
    received = []
    submitted = asyncio.Event()
    turns = 0
    transcripts = iter(transcript) if isinstance(transcript, list) else None

    async def token(request):
        body = await request.json()
        assert body == token_request(DEVICE, body["timestamp"], body["nonce"])
        return web.json_response({"code": 0, "data": {"accessToken": "trusted-device-token"}})

    async def realtime(request):
        nonlocal turns
        assert request.headers["Authorization"] == "Bearer trusted-device-token"
        ws = web.WebSocketResponse()
        await ws.prepare(request)
        async for message in ws:
            if message.type == WSMsgType.BINARY:
                received.append(message.data)
                continue
            event = json.loads(message.data)
            received.append(event)
            if event["type"] == "session.start":
                assert event["agentCode"] == DEVICE.agent_code
                assert event["audio"] == {"codec": "PCM_S16LE", "sampleRate": 16000, "channels": 1}
                await ws.send_json({"type": "session.created", "sessionId": "backend-session", "mode": "NATIVE"})
            elif event["type"] == "input.speech_stopped":
                submitted.set()
                turns += 1
                if fail_first and turns == 1:
                    await ws.send_json({"type": "assistant.text.done", "text": "未能播放的回答"})
                    await ws.send_json({"type": "assistant.failed", "code": "tts_connect_failed", "turnId": "turn-1"})
                    await ws.send_json({"type": "playback.stop", "reason": "TTS_FAILED"})
                    await ws.send_json({"type": "assistant.done"})
                    continue
                if closed_reason is not None:
                    await ws.send_json({"type": "session.closed", "reason": closed_reason})
                    continue
                await ws.send_json({"type": "input.transcript.done", "text": next(transcripts) if transcripts else transcript})
                await ws.send_json({"type": "assistant.text.done", "text": "你好，我是小智。"})
                await ws.send_json({"type": "assistant.audio.started", "audio": {"codec": "PCM_S16LE", "sampleRate": 24000, "channels": 1}})
                await ws.send_bytes(b"\0" * 2880)
                await ws.send_json({"type": "assistant.audio.done"})
                await ws.send_json({"type": "assistant.done"})
        return ws

    backend = web.Application()
    backend.router.add_post("/device-api/auth/token", token)
    backend.router.add_get("/device-api/ai/realtime", realtime)
    async with TestServer(backend) as upstream:
        config = Config("127.0.0.1", 8003, "http://192.168.1.2:8003", str(upstream.make_url("")).rstrip("/"), {DEVICE.mac: DEVICE}, idle_timeout)
        async with TestServer(create_app(config)) as server:
            async with aiohttp.ClientSession() as client:
                yield server, client, received, submitted


@pytest.mark.asyncio
async def test_ota_requires_device_and_bootstrap_token_and_never_flashes():
    async with stack() as (server, client, _, _):
        path = f"/xiaozhi/ota/{DEVICE.bootstrap_token}/"
        async with client.post(server.make_url(path), json={}) as response:
            assert response.status == 401
        async with client.post(server.make_url(path), headers={"Device-Id": DEVICE.mac},
                               json={"application": {"version": "1.9.2"}}) as response:
            data = await response.json()
            assert data["firmware"] == {"version": "1.9.2", "url": ""}
            assert data["websocket"]["version"] == 1
            assert data["websocket"]["url"] == "ws://192.168.1.2:8003/xiaozhi/v1/"
            assert data["websocket"]["token"] == DEVICE.websocket_token


@pytest.mark.asyncio
async def test_unknown_device_and_wrong_ws_token_are_rejected():
    async with stack() as (server, client, _, _):
        for headers in ({}, {**HEADERS, "Authorization": "Bearer wrong"}, {**HEADERS, "Device-Id": "11:22:33:44:55:66"}):
            with pytest.raises(aiohttp.WSServerHandshakeError) as error:
                await client.ws_connect(server.make_url("/xiaozhi/v1/"), headers=headers)
            assert error.value.status == 401


async def connect(server, client):
    ws = await client.ws_connect(server.make_url("/xiaozhi/v1/"), headers=HEADERS)
    await ws.send_json(HELLO)
    hello = await ws.receive_json(timeout=3)
    assert hello["type"] == "hello"
    assert hello["audio_params"]["sample_rate"] == 24000
    return ws


async def receive_turn(ws):
    messages = []
    while True:
        msg = await ws.receive(timeout=3)
        if msg.type == WSMsgType.BINARY:
            assert len(opuslib.Decoder(24000, 1).decode(msg.data, 2880)) == 2880
            messages.append("audio")
        else:
            data = json.loads(msg.data)
            messages.append(data)
            if data.get("type") == "tts" and data.get("state") == "stop":
                break
    assert "audio" in messages
    assert any(isinstance(m, dict) and m.get("text") == "你好" for m in messages)
    assert any(isinstance(m, dict) and m.get("text") == "你好，我是小智。" for m in messages)
    return messages


@pytest.mark.asyncio
async def test_two_manual_turns_translate_auth_control_and_real_audio():
    async with stack() as (server, client, received, _):
        ws = await connect(server, client)
        encoder = opuslib.Encoder(16000, 1, opuslib.APPLICATION_VOIP)
        for _ in range(2):
            await ws.send_json({"type": "listen", "state": "start", "mode": "manual"})
            await ws.send_bytes(encoder.encode(b"\0" * 1920, 960))
            await ws.send_json({"type": "listen", "state": "stop"})
            await receive_turn(ws)
        assert len([r for r in received if isinstance(r, bytes) and len(r) == 1920]) == 2
        assert [r["type"] for r in received if isinstance(r, dict)] == [
            "session.start", "input.speech_started", "input.speech_stopped",
            "input.speech_started", "input.speech_stopped"]
        await ws.close()


@pytest.mark.asyncio
@pytest.mark.parametrize("silent_packets", [0, 200])
async def test_auto_mode_commits_after_speech_and_silence(monkeypatch, silent_packets):
    # A deterministic voiced frame makes the test independent of VAD machine heuristics.
    def feed(self, pcm):
        self.duration_ms += 60
        if 0 < self.duration_ms - silent_packets * 60 <= 180:
            self.voiced_ms += 60
            self.silence_ms = 0
            return True
        self.silence_ms += 60
        return False
    monkeypatch.setattr(VoiceActivity, "feed", feed)
    async with stack() as (server, client, received, submitted):
        ws = await connect(server, client)
        await ws.send_json({"type": "listen", "state": "start", "mode": "auto"})
        encoder = opuslib.Encoder(16000, 1, opuslib.APPLICATION_VOIP)
        for _ in range(20 + silent_packets):
            await ws.send_bytes(encoder.encode(b"\0" * 1920, 960))
        await asyncio.wait_for(submitted.wait(), 3)
        await receive_turn(ws)
        assert sum(isinstance(r, dict) and r["type"] == "input.speech_stopped" for r in received) == 1
        assert 13 <= sum(isinstance(r, bytes) for r in received) <= 17
        await ws.close()


@pytest.mark.asyncio
async def test_invalid_audio_negotiation_closes_without_backend_session():
    async with stack() as (server, client, received, _):
        async with client.ws_connect(server.make_url("/xiaozhi/v1/"), headers=HEADERS) as ws:
            await ws.send_json({**HELLO, "audio_params": {"format": "pcm", "sample_rate": 8000, "channels": 1}})
            assert (await ws.receive_json(timeout=3))["type"] == "alert"
            await ws.receive(timeout=3)
            assert not received


@pytest.mark.asyncio
async def test_duplicate_connection_is_rejected_and_disconnect_releases_device():
    async with stack() as (server, client, _, _):
        first = await connect(server, client)
        with pytest.raises(aiohttp.WSServerHandshakeError) as error:
            await client.ws_connect(server.make_url("/xiaozhi/v1/"), headers=HEADERS)
        assert error.value.status == 409
        await first.close()
        async with asyncio.timeout(3):
            while True:
                async with client.get(server.make_url("/health")) as response:
                    if (await response.json())["connected_devices"] == 0:
                        break
                await asyncio.sleep(0.01)
        second = await connect(server, client)
        await second.close()


@pytest.mark.asyncio
async def test_interruption_discards_pending_audio_and_starts_only_one_new_turn():
    sent = []

    class Upstream:
        async def send_json(self, data):
            sent.append(data)

    conversation = Conversation(DEVICE, None, Upstream())
    conversation.tts_started = True
    conversation.audio.encode(b"\0" * 100)
    await conversation.outgoing.put((0, b"old-opus-packet"))
    await conversation.begin_speech()
    await conversation.begin_speech()  # abort followed by listen/audio must not create two turns
    assert sent == [{"type": "input.speech_started"}]
    assert conversation.suppress_output
    assert conversation.audio.encode(final=True) == []
    generation, message = conversation.outgoing.get_nowait()
    assert generation == 1
    assert message["type"] == "tts" and message["state"] == "stop"
    assert conversation.outgoing.empty()
    await conversation.end_speech()
    assert sent[-1] == {"type": "input.speech_stopped"}
    assert not conversation.suppress_output


@pytest.mark.asyncio
async def test_idle_without_any_audio_closes_cleanly_and_records_reason():
    async with stack(idle_timeout=0.05) as (server, client, received, _):
        ws = await connect(server, client)
        await ws.send_json({"type": "listen", "state": "start", "mode": "auto"})
        assert (await ws.receive(timeout=2)).type == WSMsgType.CLOSE
        assert any(isinstance(r, dict) and r.get("reason") == "IDLE_TIMEOUT" for r in received)


@pytest.mark.asyncio
async def test_silent_packets_do_not_keep_session_alive_or_submit_model_turn():
    async with stack(idle_timeout=0.08) as (server, client, received, _):
        ws = await connect(server, client)
        await ws.send_json({"type": "listen", "state": "start", "mode": "auto"})
        encoder = opuslib.Encoder(16000, 1, opuslib.APPLICATION_VOIP)
        for _ in range(15):
            await ws.send_bytes(encoder.encode(b"\0" * 1920, 960))
        assert (await ws.receive(timeout=2)).type == WSMsgType.CLOSE
        assert not any(isinstance(r, dict) and r["type"] == "input.speech_started" for r in received)


@pytest.mark.asyncio
@pytest.mark.parametrize("text", ["小七，再见！", "小智，再见！", "小志，再见。", "好了，不聊了。", "你退出吧。", "你退一下。", "小志，你退下，不要再说话了。后面是背景声"])
async def test_goodbye_plays_fixed_ack_before_closing(text):
    async with stack(transcript=text) as (server, client, received, _):
        ws = await connect(server, client)
        await ws.send_json({"type": "listen", "state": "start", "mode": "manual"})
        encoder = opuslib.Encoder(16000, 1, opuslib.APPLICATION_VOIP)
        await ws.send_bytes(encoder.encode(b"\0" * 1920, 960))
        await ws.send_json({"type": "listen", "state": "stop"})
        events=[];audio=[]
        async with asyncio.timeout(5):
            while True:
                msg=await ws.receive()
                if msg.type == WSMsgType.CLOSE: break
                if msg.type == WSMsgType.BINARY: audio.append(msg.data)
                else: events.append(json.loads(msg.data))
        assert audio
        assert [e.get("text") for e in events if e.get("state")=="sentence_start"] == ["好的，再见。"]
        assert [e.get("state") for e in events if e.get("type")=="tts"] == ["start","sentence_start","stop"]
        assert any(isinstance(r, dict) and r.get("reason") == "USER_GOODBYE" for r in received)


@pytest.mark.asyncio
async def test_wake_has_local_audible_ack_without_fabricated_user_turn():
    async with stack() as (server, client, received, _):
        ws = await connect(server, client)
        await ws.send_json({"type": "listen", "state": "detect", "text": "你好小智"})
        audio = []
        events = []
        while True:
            msg = await ws.receive(timeout=2)
            if msg.type == WSMsgType.BINARY:
                audio.append(msg.data)
            else:
                event = json.loads(msg.data)
                events.append(event)
                if event.get("state") == "stop":
                    break
        assert audio
        assert any(event.get("text") == "我在，请说。" for event in events)
        assert not any(isinstance(r, dict) and r["type"] == "input.speech_started" for r in received)
        await ws.close()


@pytest.mark.asyncio
async def test_empty_recognition_keeps_connection_and_next_turn_works():
    async with stack(transcript=["", "你好"]) as (server, client, received, _):
        ws = await connect(server, client)
        encoder = opuslib.Encoder(16000, 1, opuslib.APPLICATION_VOIP)
        for turn in range(2):
            await ws.send_json({"type": "listen", "state": "start", "mode": "manual"})
            await ws.send_bytes(encoder.encode(b"\0" * 1920, 960))
            await ws.send_json({"type": "listen", "state": "stop"})
            if turn == 0:
                async with asyncio.timeout(3):
                    while True:
                        msg = await ws.receive()
                        assert msg.type == WSMsgType.TEXT
                        if json.loads(msg.data).get("state") == "stop": break
                assert not ws.closed
            else:
                await receive_turn(ws)
        assert not any(isinstance(r, dict) and r["type"] == "session.close" for r in received)
        await ws.close()


@pytest.mark.asyncio
async def test_idle_watchdog_does_not_end_active_speech():
    class Upstream:
        async def send_json(self, data): pass
    conversation = Conversation(DEVICE, None, Upstream(), idle_timeout=.02)
    conversation.speaking = True
    conversation.last_activity = 0
    task = asyncio.create_task(conversation.idle_watchdog())
    await asyncio.sleep(.06)
    assert not task.done()
    assert not conversation.closing
    task.cancel()
    await asyncio.gather(task, return_exceptions=True)

@pytest.mark.parametrize("text", ["怎么退出？", "你退出了吗？", "不要退出", "我退出公司了", "说一下退出的功能", "播放一首再见", "你退出吧？", "如果我说你退出吧会怎么样"])
def test_exit_does_not_match_questions_negations_or_quoted_examples(text):
    assert not is_exit_command(text)


@pytest.mark.asyncio
async def test_backend_close_without_transcript_also_speaks_farewell():
    async with stack(closed_reason="USER_GOODBYE") as (server, client, received, _):
        ws=await connect(server,client)
        await ws.send_json({"type":"listen","state":"start","mode":"manual"})
        encoder=opuslib.Encoder(16000,1,opuslib.APPLICATION_VOIP)
        await ws.send_bytes(encoder.encode(b"\0"*1920,960))
        await ws.send_json({"type":"listen","state":"stop"})
        replies=[];packets=0
        async with asyncio.timeout(5):
            while True:
                msg=await ws.receive()
                if msg.type==WSMsgType.CLOSE:break
                if msg.type==WSMsgType.BINARY:packets+=1
                elif json.loads(msg.data).get("state")=="sentence_start":replies.append(json.loads(msg.data)["text"])
        assert packets>0;assert replies==["好的，再见。"]

@pytest.mark.asyncio
async def test_abort_waits_for_real_new_speech_instead_of_submitting_noise(monkeypatch):
    # A short noise burst after abort must not submit an empty turn before the next sentence.
    frame = 0
    def feed(self, pcm):
        nonlocal frame
        frame += 1
        self.duration_ms += 60
        if frame in (1, 2, 23, 24, 25):
            self.voiced_ms += 60
            self.silence_ms = 0
            return True
        self.silence_ms += 60
        return False
    monkeypatch.setattr(VoiceActivity, "feed", feed)
    encoder = opuslib.Encoder(16000, 1, opuslib.APPLICATION_VOIP)
    packet = encoder.encode(b"\0" * 1920, 960)
    def text(value): return aiohttp.WSMessage(WSMsgType.TEXT, json.dumps(value), "")
    def audio(): return aiohttp.WSMessage(WSMsgType.BINARY, packet, "")
    class Downstream:
        def __init__(self, messages): self.messages = iter(messages)
        def __aiter__(self): return self
        async def __anext__(self):
            try: return next(self.messages)
            except StopIteration: raise StopAsyncIteration
    class Upstream:
        def __init__(self): self.controls, self.audio = [], []
        async def send_json(self, data): self.controls.append(data)
        async def send_bytes(self, data): self.audio.append(data)
    upstream = Upstream()
    conversation = Conversation(DEVICE, Downstream([
        text({"type": "abort"}), text({"type": "listen", "state": "start", "mode": "auto"}),
        *[audio() for _ in range(22)]]), upstream)
    await conversation.device_messages()
    assert conversation.awaiting_voice_after_abort
    assert upstream.controls == [{"type": "input.speech_started"}]
    assert not upstream.audio
    conversation.down = Downstream([audio() for _ in range(13)])
    await conversation.device_messages()
    assert upstream.controls == [{"type": "input.speech_started"}, {"type": "input.speech_stopped"}]
    assert upstream.audio
    assert not conversation.awaiting_voice_after_abort


@pytest.mark.asyncio
async def test_failed_synthesis_returns_to_listening_and_next_turn_uses_same_connection():
    async with stack(fail_first=True) as (server, client, received, _):
        ws = await connect(server, client)
        encoder = opuslib.Encoder(16000, 1, opuslib.APPLICATION_VOIP)
        await ws.send_json({"type": "listen", "state": "start", "mode": "manual"})
        await ws.send_bytes(encoder.encode(b"\0" * 1920, 960))
        await ws.send_json({"type": "listen", "state": "stop"})
        events = []
        async with asyncio.timeout(3):
            while True:
                msg = await ws.receive()
                assert msg.type == WSMsgType.TEXT
                event = json.loads(msg.data); events.append(event)
                if event.get("type") == "tts" and event.get("state") == "stop":
                    # Discarding an existing text-only playback can yield an initial stop.
                    if any(e.get("text") == "语音暂时不可用，请再说一次。" for e in events):
                        break
        assert not ws.closed
        assert not any(e.get("type") == "alert" for e in events)
        await ws.send_json({"type": "listen", "state": "start", "mode": "manual"})
        await ws.send_bytes(encoder.encode(b"\0" * 1920, 960))
        await ws.send_json({"type": "listen", "state": "stop"})
        await receive_turn(ws)
        assert sum(isinstance(r, dict) and r["type"] == "session.start" for r in received) == 1
        assert sum(isinstance(r, dict) and r["type"] == "input.speech_stopped" for r in received) == 2
        await ws.close()
