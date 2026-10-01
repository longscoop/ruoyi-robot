#!/usr/bin/env python3
"""Xiaozhi OTA/WebSocket adapter for RuoYi Robot's authenticated realtime API.

USB is used only for diagnostics. The unmodified device sends Opus over Wi-Fi;
this process translates it to the platform's PCM/control protocol.
"""
import argparse
import asyncio
import base64
from collections import deque
from contextlib import suppress
from dataclasses import dataclass
import hashlib
import hmac
import json
import logging
import math
import struct
from pathlib import Path
import re
import secrets
import time
import wave
from urllib.parse import urlsplit

import aiohttp
from aiohttp import web, WSMsgType
import opuslib
import webrtcvad

LOG = logging.getLogger("xiaozhi_bridge")
MAC = re.compile(r"(?:[0-9a-f]{2}:){5}[0-9a-f]{2}")
INPUT_RATE = 16000
OUTPUT_RATE = 24000
FRAME_MS = 60
OUTPUT_FRAME_BYTES = OUTPUT_RATE * FRAME_MS // 1000 * 2


@dataclass(frozen=True)
class Device:
    mac: str
    device_sn: str
    http_secret: str
    agent_code: str
    bootstrap_token: str
    websocket_token: str


@dataclass(frozen=True)
class Config:
    host: str
    port: int
    public_url: str
    backend_url: str
    devices: dict[str, Device]
    idle_timeout_seconds: float = 30
    max_utterance_seconds: float = 30
    end_silence_ms: int = 550

    @classmethod
    def load(cls, path):
        raw = json.loads(Path(path).read_text())
        devices = {}
        for item in raw["devices"]:
            device = Device(**{**item, "mac": item["mac"].lower()})
            if not MAC.fullmatch(device.mac) or device.mac in devices:
                raise ValueError("Each device must have a unique valid MAC address")
            for name in ("device_sn", "http_secret", "agent_code", "bootstrap_token", "websocket_token"):
                value = getattr(device, name)
                if not value or value.startswith("REPLACE_"):
                    raise ValueError(f"Configure device field: {name}")
            for name in ("bootstrap_token", "websocket_token"):
                if not re.fullmatch(r"[A-Za-z0-9_-]{32,128}", getattr(device, name)):
                    raise ValueError(f"{name} must be a random URL-safe token of at least 32 characters")
            devices[device.mac] = device
        if not devices:
            raise ValueError("Configure at least one device")
        for name in ("public_url", "backend_url"):
            url = urlsplit(raw[name])
            if url.scheme not in ("http", "https") or not url.hostname or url.username or url.query or url.fragment:
                raise ValueError(f"{name} must be an HTTP(S) base URL without credentials/query")
        idle = float(raw.get("idle_timeout_seconds", 30))
        if not 2 <= idle <= 300:
            raise ValueError("idle_timeout_seconds must be between 2 and 300")
        max_utterance = float(raw.get("max_utterance_seconds", 30))
        if not 3 <= max_utterance <= 60:
            raise ValueError("max_utterance_seconds must be between 3 and 60")
        end_silence = int(raw.get("end_silence_ms", 550))
        if not 300 <= end_silence <= 1500:
            raise ValueError("end_silence_ms must be between 300 and 1500")
        return cls(raw.get("host", "127.0.0.1"), int(raw.get("port", 8003)),
                   raw["public_url"].rstrip("/"), raw["backend_url"].rstrip("/"), devices, idle, max_utterance, end_silence)


def token_request(device, timestamp=None, nonce=None):
    timestamp = int(time.time()) if timestamp is None else timestamp
    nonce = secrets.token_hex(16) if nonce is None else nonce
    canonical = f"POST\n/device-api/auth/token\n{device.device_sn}\n{timestamp}\n{nonce}"
    digest = hmac.new(device.http_secret.encode(), canonical.encode(), hashlib.sha256).digest()
    return {"deviceSn": device.device_sn, "timestamp": timestamp, "nonce": nonce,
            "signature": base64.urlsafe_b64encode(digest).rstrip(b"=").decode()}


def websocket_url(base, path):
    return ("wss" + base[5:] if base.startswith("https:") else "ws" + base[4:]) + path


class AudioCodec:
    def __init__(self):
        self.decoder = opuslib.Decoder(INPUT_RATE, 1)
        self.encoder = opuslib.Encoder(OUTPUT_RATE, 1, opuslib.APPLICATION_VOIP)
        self.encoder.bitrate = 24000
        self.pending = bytearray()

    def decode(self, packet):
        if not packet or len(packet) > 4096:
            raise ValueError("Invalid Opus packet size")
        return self.decoder.decode(packet, INPUT_RATE * 120 // 1000)

    def encode(self, pcm=b"", final=False):
        self.pending.extend(pcm)
        packets = []
        while len(self.pending) >= OUTPUT_FRAME_BYTES:
            frame = bytes(self.pending[:OUTPUT_FRAME_BYTES])
            del self.pending[:OUTPUT_FRAME_BYTES]
            packets.append(self.encoder.encode(frame, OUTPUT_FRAME_BYTES // 2))
        if final and self.pending:
            frame = bytes(self.pending).ljust(OUTPUT_FRAME_BYTES, b"\0")
            self.pending.clear()
            packets.append(self.encoder.encode(frame, OUTPUT_FRAME_BYTES // 2))
        return packets

    def reset_output(self):
        self.pending.clear()
        self.encoder.reset_state()


class VoiceActivity:
    """20 ms WebRTC VAD with pre-roll; packet boundaries need not align to frames."""
    def __init__(self):
        self.vad = webrtcvad.Vad(2)
        self.reset()

    def reset(self):
        self.pending = bytearray()
        self.voiced_ms = 0
        self.silence_ms = 0
        self.duration_ms = 0

    def feed(self, pcm):
        self.pending.extend(pcm)
        voice = False
        while len(self.pending) >= 640:
            frame = bytes(self.pending[:640])
            del self.pending[:640]
            samples = struct.unpack("<320h", frame)
            rms = math.sqrt(sum(sample * sample for sample in samples) / len(samples))
            active = self.vad.is_speech(frame, INPUT_RATE) and rms >= 400
            self.duration_ms += 20
            if active:
                self.voiced_ms += 20
                self.silence_ms = 0
                voice = True
            else:
                self.silence_ms += 20
        return voice


def is_exit_command(text):
    if not text or not text.strip():
        return False
    first = re.split(r"[。.!！?？;；\n]", text.strip(), maxsplit=1)[0]
    if text.strip().startswith((first + "？", first + "?")):
        return False
    normalized = re.sub(r"[\W_]+", "", first).lower()
    return re.fullmatch(
        r"(?:(?:好了|好的|好|那|现在|请|麻烦|你|您|小智|小志|小七))*"
        r"(?:再见|拜拜|退出(?:对话|会话|聊天)?|结束(?:对话|会话|聊天)|停止(?:对话|会话|聊天)|不聊了|不说了|退一下|退下|不用(?:回复|回答)了|不要再说话了|别再说话了|goodbye)"
        r"(?:(?:一下|吧|啊|呀|啦|了|哦|呢|小智|小志|小七))*"
        r"(?:不要再说话了|别再说话了|不用回复了)?", normalized) is not None


class Conversation:
    def __init__(self, device, downstream, upstream, idle_timeout=30, max_utterance=30, end_silence_ms=550):
        self.device, self.down, self.up = device, downstream, upstream
        self.audio = AudioCodec()
        self.vad = VoiceActivity()
        self.pre_roll = deque(maxlen=5)
        self.session_id = secrets.token_hex(16)
        self.listening = False
        self.speaking = False
        self.mode = "auto"
        self.suppress_output = False
        self.tts_started = False
        self.generation = 0
        self.outgoing = asyncio.Queue(maxsize=500)
        self.writer_task = None
        self.idle_timeout = idle_timeout
        self.max_utterance_ms = max_utterance * 1000
        self.speech_started_ms = 0
        self.last_activity = time.monotonic()
        self.awaiting_response = False
        self.awaiting_voice_after_abort = False
        self.last_wake = 0.0
        self.close_reason = "CLIENT_CLOSE"
        self.closing = False
        self.submitted_at = None
        self.first_audio_at = None
        self.turn_number = 0
        self.end_silence_ms = end_silence_ms
        self.last_voiced_at = None
        self.turn_end_voiced_at = None

    async def control(self, kind):
        await self.up.send_json({"type": kind})

    async def event(self, kind, **values):
        await self.outgoing.put((self.generation, {"type": kind, "session_id": self.session_id, **values}))

    async def write_output(self):
        next_frame = 0.0
        while True:
            generation, data = await self.outgoing.get()
            if generation != self.generation:
                continue
            if isinstance(data, asyncio.Future):
                # Give the final packet time to play before disconnecting the device.
                await asyncio.sleep(max(0, next_frame - time.monotonic()) + 0.15)
                if not data.done():
                    data.set_result(None)
            elif isinstance(data, bytes):
                await asyncio.sleep(max(0, next_frame - time.monotonic()))
                if generation != self.generation:
                    continue
                await self.down.send_bytes(data)
                self.last_activity = time.monotonic()
                if self.submitted_at is not None and self.first_audio_at is None:
                    self.first_audio_at = self.last_activity
                    LOG.info("Turn %s first audio: %.0f ms (%s)", self.turn_number,
                             (self.first_audio_at - self.submitted_at) * 1000, self.device.device_sn)
                    if self.turn_end_voiced_at is not None:
                        LOG.info("Turn %s speech-end to first packet: %.0f ms (%s)", self.turn_number,
                                 (self.first_audio_at - self.turn_end_voiced_at) * 1000, self.device.device_sn)
                next_frame = max(next_frame, time.monotonic()) + FRAME_MS / 1000
            else:
                # Do not tell the device playback is complete before its final frame duration.
                if data.get("type") == "tts" and data.get("state") == "stop":
                    await asyncio.sleep(max(0, next_frame - time.monotonic()))
                if generation == self.generation:
                    await self.down.send_json(data)
                    if data.get("type") == "tts" and data.get("state") == "stop":
                        if self.submitted_at is not None:
                            LOG.info("Turn %s playback complete: %.0f ms (%s)", self.turn_number,
                                     (time.monotonic() - self.submitted_at) * 1000, self.device.device_sn)
                            self.submitted_at = None
                        self.awaiting_response = False
                        self.last_activity = time.monotonic()

    async def discard_output(self, notify=True):
        had_playback = self.tts_started or not self.outgoing.empty()
        self.generation += 1
        while not self.outgoing.empty():
            self.outgoing.get_nowait()
        self.audio.reset_output()
        self.tts_started = False
        if had_playback and notify:
            await self.event("tts", state="stop")

    async def begin_speech(self):
        if self.speaking:
            return
        self.suppress_output = True
        self.submitted_at = None
        await self.discard_output()
        await self.control("input.speech_started")
        self.speaking = True
        self.speech_started_ms = self.vad.duration_ms

    async def end_speech(self):
        if not self.speaking:
            return
        self.speaking = False
        self.listening = False
        self.suppress_output = False
        self.awaiting_response = True
        self.last_activity = time.monotonic()
        self.submitted_at = self.last_activity
        self.turn_end_voiced_at = self.last_voiced_at
        self.first_audio_at = None
        self.turn_number += 1
        await self.control("input.speech_stopped")
        LOG.info("Utterance submitted: %s", self.device.device_sn)

    async def device_messages(self):
        async for msg in self.down:
            if self.closing:
                continue  # Do not accept a new turn while the farewell is playing.
            if msg.type == WSMsgType.TEXT:
                event = json.loads(msg.data)
                kind = event.get("type")
                if kind == "listen":
                    state = event.get("state")
                    if state == "start":
                        mode = event.get("mode", "auto")
                        if mode not in ("auto", "manual", "realtime"):
                            raise ValueError("Unsupported listen mode")
                        self.mode = mode
                        self.listening = True
                        self.vad.reset()
                        self.speech_started_ms = 0
                        self.pre_roll.clear()
                    elif state == "stop":
                        await self.end_speech()
                        self.listening = False
                    elif state == "detect":
                        await self.wake_acknowledgement()
                    else:
                        raise ValueError("Unsupported listen state")
                elif kind == "abort":
                    # The platform models interruption as the start of the next speech turn.
                    await self.begin_speech()
                    self.awaiting_voice_after_abort = True
                    self.listening = False
                    self.vad.reset()
                    self.speech_started_ms = 0
                    self.pre_roll.clear()
                elif kind in ("iot", "mcp"):
                    pass  # Voice testing does not invoke hardware tools.
                elif kind != "goodbye":
                    raise ValueError("Unsupported Xiaozhi message")
                else:
                    break
            elif msg.type == WSMsgType.BINARY:
                if not self.listening:
                    continue  # Wake-word preamble is not an utterance.
                pcm = self.audio.decode(msg.data)
                voiced = self.vad.feed(pcm)
                if not self.speaking or self.awaiting_voice_after_abort:
                    self.pre_roll.append(pcm)
                    if self.mode != "manual" and not voiced:
                        if self.vad.silence_ms >= 300:
                            self.vad.voiced_ms = 0
                        continue
                    if self.mode != "manual" and self.vad.voiced_ms < 180:
                        continue
                    self.last_activity = time.monotonic()
                    await self.begin_speech()
                    self.awaiting_voice_after_abort = False
                    self.speech_started_ms = self.vad.duration_ms
                    for buffered in self.pre_roll:
                        await self.up.send_bytes(buffered)
                    self.pre_roll.clear()
                else:
                    await self.up.send_bytes(pcm)
                if voiced:
                    self.last_activity = time.monotonic()
                    self.last_voiced_at = self.last_activity
                if self.vad.duration_ms - self.speech_started_ms >= self.max_utterance_ms or (self.mode != "manual" and
                        self.vad.voiced_ms >= 100 and self.vad.silence_ms >= self.end_silence_ms):
                    await self.end_speech()
            elif msg.type == WSMsgType.ERROR:
                raise ConnectionError("Device WebSocket failed")

    async def start_tts(self):
        if not self.tts_started:
            self.tts_started = True
            await self.event("tts", state="start")

    async def backend_messages(self):
        async for msg in self.up:
            if msg.type == WSMsgType.BINARY:
                if not self.suppress_output:
                    await self.start_tts()
                    for packet in self.audio.encode(msg.data):
                        await self.outgoing.put((self.generation, packet))
            elif msg.type == WSMsgType.TEXT:
                event = json.loads(msg.data)
                kind = event.get("type")
                if kind == "session.error":
                    raise RuntimeError("Backend reported session.error; check backend model configuration/logs")
                if kind == "session.closed":
                    await self.finish(event.get("reason") or "BACKEND_CLOSE")
                    return
                if kind in ("playback.stop", "assistant.interrupted"):
                    continue  # Output is already cancelled immediately on local interruption.
                if self.suppress_output:
                    continue
                if kind == "assistant.failed":
                    # Backend has cancelled this turn and is ready for the next one.
                    self.suppress_output = True
                    self.submitted_at = None
                    self.speaking = False
                    self.awaiting_voice_after_abort = False
                    await self.discard_output(notify=False)
                    self.vad.reset()
                    self.pre_roll.clear()
                    await self.event("tts", state="start")
                    await self.event("tts", state="sentence_start", text="语音暂时不可用，请再说一次。")
                    await self.event("tts", state="stop")
                    self.awaiting_response = False
                    self.last_activity = time.monotonic()
                    # Log only a bounded error identifier, never provider messages or credentials.
                    code = re.sub(r"[^a-zA-Z0-9_-]", "", str(event.get("code", "unknown")))[:64]
                    LOG.warning("Voice turn failed; listening resumes: %s (%s)", self.device.device_sn, code)
                elif kind == "input.transcript.done":
                    text = event.get("text", "")
                    normalized = re.sub(r"[\W_]+", "", text).lower()
                    if is_exit_command(text):
                        await self.finish("USER_GOODBYE")
                        return
                    if not normalized:
                        # Noise/false VAD is an empty turn, not the end of the conversation.
                        self.suppress_output = True
                        self.submitted_at = None
                        await self.discard_output()
                        await self.event("tts", state="start")
                        await self.event("tts", state="stop")
                        self.awaiting_response = False
                        self.last_activity = time.monotonic()
                        LOG.info("Empty utterance skipped; listening resumes: %s", self.device.device_sn)
                        continue
                    await self.event("stt", text=text)
                    LOG.info("Speech recognized: %s", self.device.device_sn)
                elif kind == "assistant.text.done":
                    await self.start_tts()
                    await self.event("tts", state="sentence_start", text=event.get("text", ""))
                elif kind == "assistant.audio.started":
                    if event.get("audio") != {"codec": "PCM_S16LE", "sampleRate": OUTPUT_RATE, "channels": 1}:
                        raise ValueError("Backend must output mono PCM_S16LE at 24000 Hz")
                    await self.start_tts()
                elif kind in ("assistant.audio.done", "assistant.done"):
                    for packet in self.audio.encode(final=True):
                        await self.outgoing.put((self.generation, packet))
                    if kind == "assistant.done":
                        await self.event("tts", state="stop")
                        self.tts_started = False
                        LOG.info("Assistant response complete: %s", self.device.device_sn)
            elif msg.type == WSMsgType.ERROR:
                raise ConnectionError("Backend WebSocket failed")

    async def wake_acknowledgement(self):
        now = time.monotonic()
        if now - self.last_wake < 1:
            return
        self.last_wake = now
        self.last_activity = now
        LOG.info("Wake word received: %s", self.device.device_sn)
        await self.discard_output()
        await self.spoken_acknowledgement("我在，请说。", "wake_ack.wav")

    async def spoken_acknowledgement(self, text, filename):
        await self.event("tts", state="start")
        await self.event("tts", state="sentence_start", text=text)
        # Bundled clips use the configured Cherry voice and require no live inference.
        with wave.open(str(Path(__file__).with_name(filename)), "rb") as wav:
            if (wav.getnchannels(), wav.getsampwidth(), wav.getframerate()) != (1, 2, OUTPUT_RATE):
                raise ValueError("Acknowledgement must be mono 24 kHz PCM16")
            pcm = wav.readframes(wav.getnframes())
        for packet in self.audio.encode(pcm, final=True):
            await self.outgoing.put((self.generation, packet))
        await self.event("tts", state="stop")

    async def finish(self, reason):
        if self.closing:
            return
        self.closing = True
        self.close_reason = reason
        self.listening = False
        self.suppress_output = True
        self.speaking = False
        await self.discard_output()
        if reason == "USER_GOODBYE":
            await self.spoken_acknowledgement("好的，再见。", "goodbye_ack.wav")
            drained = asyncio.get_running_loop().create_future()
            await self.outgoing.put((self.generation, drained))
            try:
                await asyncio.wait_for(drained, timeout=10)
            except asyncio.TimeoutError:
                LOG.warning("Farewell playback timed out; closing device session")
        LOG.info("Ending session: %s (%s)", self.device.device_sn, reason)
        with suppress(Exception):
            await self.up.send_json({"type": "session.close", "reason": reason})
        await self.down.close(code=1000, message=reason.encode())

    async def idle_watchdog(self):
        while True:
            await asyncio.sleep(min(1, self.idle_timeout / 2))
            if self.closing or self.speaking:
                continue  # Speech is bounded separately by max_utterance_ms, never idle timeout.
            timeout = 90 if self.awaiting_response or self.tts_started or not self.outgoing.empty() else self.idle_timeout
            if time.monotonic() - self.last_activity >= timeout:
                await self.finish("RESPONSE_TIMEOUT" if self.awaiting_response else "IDLE_TIMEOUT")
                return

    async def run(self):
        tasks = [asyncio.create_task(fn()) for fn in
                 (self.device_messages, self.backend_messages, self.write_output, self.idle_watchdog)]
        try:
            done, _ = await asyncio.wait(tasks, return_when=asyncio.FIRST_COMPLETED)
            for task in done:
                task.result()
        finally:
            for task in tasks:
                task.cancel()
            await asyncio.gather(*tasks, return_exceptions=True)
            if not self.closing:
                with suppress(Exception):
                    await self.up.send_json({"type": "session.close", "reason": self.close_reason})


CONFIG = web.AppKey("config", Config)
CLIENT = web.AppKey("client", aiohttp.ClientSession)
ACTIVE = web.AppKey("active", set)
CONNECTIONS = web.AppKey("connections", set)


async def ota(request):
    config = request.app[CONFIG]
    device = config.devices.get(request.headers.get("Device-Id", "").lower())
    if device is None or not secrets.compare_digest(request.match_info["token"], device.bootstrap_token):
        raise web.HTTPUnauthorized()
    body = await request.json()
    if not isinstance(body, dict):
        raise web.HTTPBadRequest()
    version = body.get("application", {}).get("version", "1.0.0")
    LOG.info("OTA configuration requested: %s, firmware %s", device.device_sn, str(version)[:40])
    return web.json_response({
        "server_time": {"timestamp": int(time.time() * 1000), "timezone_offset": 480},
        "firmware": {"version": version, "url": ""},
        "websocket": {"url": websocket_url(config.public_url, "/xiaozhi/v1/"),
                      "token": device.websocket_token, "version": 1},
    })


async def device_token(client, config, device):
    async with client.post(config.backend_url + "/device-api/auth/token", json=token_request(device)) as response:
        if response.status != 200:
            raise RuntimeError(f"Device authentication failed (HTTP {response.status})")
        body = await response.json()
        if body.get("code") != 0 or not body.get("data", {}).get("accessToken"):
            raise RuntimeError("Device authentication failed; check device activation and HTTP secret")
        return body["data"]["accessToken"]


async def websocket(request):
    config = request.app[CONFIG]
    device = config.devices.get(request.headers.get("Device-Id", "").lower())
    authorization = request.headers.get("Authorization", "")
    if device is None or not secrets.compare_digest(authorization, "Bearer " + device.websocket_token):
        raise web.HTTPUnauthorized()
    if request.headers.get("Protocol-Version", "1") != "1":
        raise web.HTTPBadRequest(text="Use protocol version 1 from the OTA response")
    active = request.app[ACTIVE]
    if device.mac in active:
        raise web.HTTPConflict(text="Device is already connected")
    active.add(device.mac)
    downstream = web.WebSocketResponse(heartbeat=30, max_msg_size=16384)
    try:
        await downstream.prepare(request)
        request.app[CONNECTIONS].add(downstream)
        hello = await downstream.receive_json(timeout=10)
        params = hello.get("audio_params", {})
        if (hello.get("type") != "hello" or hello.get("version", 1) != 1 or
                hello.get("transport") != "websocket" or params.get("format") != "opus" or
                params.get("sample_rate") != INPUT_RATE or params.get("channels") != 1):
            raise ValueError("Expected Xiaozhi v1 hello with mono 16000 Hz Opus")
        client = request.app[CLIENT]
        token = await device_token(client, config, device)
        async with client.ws_connect(websocket_url(config.backend_url, "/device-api/ai/realtime"),
                                     headers={"Authorization": "Bearer " + token},
                                     heartbeat=30, max_msg_size=4 * 1024 * 1024) as upstream:
            await upstream.send_json({"type": "session.start", "agentCode": device.agent_code,
                                      "audio": {"codec": "PCM_S16LE", "sampleRate": INPUT_RATE, "channels": 1}})
            ready = await upstream.receive_json(timeout=15)
            if ready.get("type") != "session.created":
                raise RuntimeError("Backend did not create a session; check Agent binding and model configuration")
            conversation = Conversation(device, downstream, upstream, config.idle_timeout_seconds, config.max_utterance_seconds, config.end_silence_ms)
            await downstream.send_json({"type": "hello", "version": 1, "transport": "websocket",
                                        "session_id": conversation.session_id,
                                        "audio_params": {"format": "opus", "sample_rate": OUTPUT_RATE,
                                                         "channels": 1, "frame_duration": FRAME_MS}})
            LOG.info("Connected: %s -> Agent %s", device.device_sn, device.agent_code)
            await conversation.run()
    except asyncio.CancelledError:
        raise
    except Exception as error:
        # Never log response bodies, bearer headers or configuration credentials.
        LOG.warning("Session failed for %s (%s)", device.device_sn, type(error).__name__)
        if not downstream.closed and downstream.prepared:
            with suppress(Exception):
                await downstream.send_json({"type": "alert", "status": "连接失败", "emotion": "sad",
                                            "message": "请检查 RuoYi Robot 设备鉴权、Agent 绑定及模型配置"})
            await downstream.close(code=1011, message=b"Check RuoYi Robot configuration")
    finally:
        active.discard(device.mac)
        request.app[CONNECTIONS].discard(downstream)
        if downstream.prepared:
            await downstream.close()
    return downstream


async def health(request):
    return web.json_response({"status": "ok", "connected_devices": len(request.app[ACTIVE])})


def create_app(config):
    app = web.Application(client_max_size=65536)
    app[CONFIG], app[ACTIVE], app[CONNECTIONS] = config, set(), set()

    async def shutdown(app):
        await asyncio.gather(*(ws.close(code=1001, message=b"Server shutdown")
                               for ws in list(app[CONNECTIONS])), return_exceptions=True)

    app.on_shutdown.append(shutdown)

    async def clients(app):
        async with aiohttp.ClientSession(timeout=aiohttp.ClientTimeout(total=20)) as client:
            app[CLIENT] = client
            yield

    app.cleanup_ctx.append(clients)
    app.router.add_get("/health", health)
    app.router.add_post("/xiaozhi/ota/{token}/", ota)
    app.router.add_get("/xiaozhi/v1/", websocket)
    return app


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", required=True, help="Private JSON configuration file")
    args = parser.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
    config = Config.load(args.config)
    # Fail early if the system Opus library is missing.
    AudioCodec()
    LOG.info("Starting adapter at %s; backend %s", config.public_url, config.backend_url)
    web.run_app(create_app(config), host=config.host, port=config.port, access_log=None, shutdown_timeout=5)


if __name__ == "__main__":
    main()
