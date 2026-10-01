#!/usr/bin/env python3
"""Send a 16 kHz mono s16le PCM utterance through the real Xiaozhi adapter."""
import argparse
import asyncio
import json
from pathlib import Path
import wave

import aiohttp
import opuslib

from bridge import Config, websocket_url


async def run(args):
    config = Config.load(args.config)
    device = config.devices[args.mac.lower()] if args.mac else next(iter(config.devices.values()))
    pcm = Path(args.pcm).read_bytes()
    if not pcm or len(pcm) % 2:
        raise ValueError("Input must be nonempty 16 kHz mono signed 16-bit little-endian PCM")
    encoder = opuslib.Encoder(16000, 1, opuslib.APPLICATION_VOIP)
    decoder = opuslib.Decoder(24000, 1)
    response = bytearray()
    headers = {"Device-Id": device.mac, "Authorization": "Bearer " + device.websocket_token,
               "Protocol-Version": "1", "Client-Id": "ruoyi-xiaozhi-probe"}
    async with aiohttp.ClientSession() as client:
        async with client.ws_connect(websocket_url(args.base or config.public_url, "/xiaozhi/v1/"), headers=headers) as ws:
            await ws.send_json({"type": "hello", "version": 1, "transport": "websocket",
                                "audio_params": {"format": "opus", "sample_rate": 16000,
                                                 "channels": 1, "frame_duration": 60}})
            hello = await ws.receive_json(timeout=20)
            if hello.get("type") != "hello":
                raise RuntimeError(f"Session was rejected: {hello.get('message', hello.get('type'))}")
            print("Connected to the platform Agent through the Xiaozhi protocol")
            await ws.send_json({"type": "listen", "state": "start", "mode": "manual"})
            for i in range(0, len(pcm), 1920):
                await ws.send_bytes(encoder.encode(pcm[i:i + 1920].ljust(1920, b"\0"), 960))
                await asyncio.sleep(0.06)
            await ws.send_json({"type": "listen", "state": "stop"})
            async with asyncio.timeout(45):
                async for msg in ws:
                    if msg.type == aiohttp.WSMsgType.BINARY:
                        response.extend(decoder.decode(msg.data, 2880))
                    elif msg.type == aiohttp.WSMsgType.TEXT:
                        event = json.loads(msg.data)
                        if event.get("text"):
                            print(event["type"], event["text"])
                        if event.get("type") == "alert":
                            raise RuntimeError(event.get("message"))
                        if event.get("type") == "tts" and event.get("state") == "stop":
                            if not response:
                                raise RuntimeError("No model audio was returned")
                            with wave.open(str(args.output), "wb") as wav:
                                wav.setnchannels(1)
                                wav.setsampwidth(2)
                                wav.setframerate(24000)
                                wav.writeframes(response)
                            print(f"PASS: received {len(response) / 48000:.2f}s of model speech")
                            return
            raise RuntimeError("Session closed without a complete audio reply")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", required=True)
    parser.add_argument("--pcm", required=True)
    parser.add_argument("--output", required=True, help="Reply WAV path")
    parser.add_argument("--base", help="Override adapter URL, e.g. http://127.0.0.1:8003")
    parser.add_argument("--mac", help="Configured device MAC; defaults to the first device")
    asyncio.run(run(parser.parse_args()))
