"""Authenticated local HTTP adapter for the actual PowerMem 0.5.3 SDK.
No LLM credentials or conversation content are emitted in HTTP errors/access logs.
"""
import argparse
import hmac
import json
import os
import re
import threading
import time
from collections import OrderedDict
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

NAMESPACE = re.compile(r"tenant-[1-9][0-9]*-agent-[1-9][0-9]*-(?:robot-[1-9][0-9]*|member-[1-9][0-9]*-robot-[1-9][0-9]*)\Z")
TOPICS = {"identity": {"facts": "用户自述的身份；未核实，不作为身份认证"},
          "preferences": {"facts": "用户明确表达的偏好"},
          "context": {"facts": "环境与情景"}, "work": {"facts": "职业与工作"},
          "relations": {"facts": "家庭、宠物与人际关系"}}


def expand_environment(value):
    if isinstance(value, dict):
        return {k: expand_environment(v) for k, v in value.items()}
    if isinstance(value, list):
        return [expand_environment(v) for v in value]
    if isinstance(value, str):
        def replace(match):
            result = os.environ.get(match.group(1))
            if result is None:
                raise ValueError("Missing configuration environment variable: " + match.group(1))
            return result
        return re.sub(r"\$\{([A-Z_][A-Z_0-9]*)\}", replace, value)
    return value


class MemoryService:
    def __init__(self, memory):
        self.memory = memory
        self.lock = threading.Lock()
        self.cache_lock = threading.Lock()
        self.cache = OrderedDict()

    def execute(self, path, data):
        namespace = data.get("namespace", "")
        if not isinstance(namespace, str) or not NAMESPACE.fullmatch(namespace):
            raise ValueError("Invalid namespace")
        # Never queue interactive reads behind SDK inference. Reuse only the exact
        # query snapshot (30s); writes invalidate it before touching the SDK.
        if path == "/query":
            query, limit = data.get("query", ""), data.get("limit", 8)
            if not isinstance(query, str) or len(query) > 4000 or not isinstance(limit, int) or not 1 <= limit <= 100:
                raise ValueError("Invalid query")
            key = (namespace, query, limit)
            if not self.lock.acquire(blocking=False):
                with self.cache_lock:
                    cached = self.cache.get(key)
                    return cached[1] if cached and time.monotonic() - cached[0] < 30 else {"results": [], "busy": True}
        else:
            self.lock.acquire()
        try:
            if path != "/query":
                with self.cache_lock:
                    for key in list(self.cache):
                        if key[0] == namespace:
                            del self.cache[key]
            if path == "/query":
                query = data.get("query", "")
                limit = data.get("limit", 8)
                if not isinstance(query, str) or len(query) > 4000 or not isinstance(limit, int) or not 1 <= limit <= 100:
                    raise ValueError("Invalid query")
                result = self.memory.search(query=query, user_id=namespace, limit=limit)
                # SDK decay/forgetting metadata must be honored even when a store returns a stale candidate.
                rows = result.get("results", []) if isinstance(result, dict) else result
                rows = [row for row in rows if not row.get("metadata", {}).get("memory_management", {}).get("should_forget", False)
                        and row.get("user_id", namespace) == namespace]
                result = {"results": rows[:limit]}
                with self.cache_lock:
                    self.cache[key] = (time.monotonic(), result)
                    self.cache.move_to_end(key)
                    while len(self.cache) > 256:
                        self.cache.popitem(last=False)
                return result
            if path == "/save":
                messages = data.get("messages", [])
                if not isinstance(messages, list) or not 1 <= len(messages) <= 100:
                    raise ValueError("Invalid messages")
                evidence = []
                for message in messages:
                    if not isinstance(message, dict) or message.get("role") != "user":
                        raise ValueError("Only user evidence can create memory")
                    content = message.get("content", "")
                    if not isinstance(content, str) or not content.strip() or len(content) > 30000:
                        raise ValueError("Invalid message content")
                    evidence.append({"role": "user", "content": content})
                self.memory.add(messages=evidence, user_id=namespace, infer=True, include_roles=["user"],
                                profile_type="topics", custom_topics=json.dumps(TOPICS, ensure_ascii=False), strict_mode=True,
                                native_language="Chinese")
                return {"saved": True}
            if path == "/forget":
                target = data.get("content", "")
                if not isinstance(target, str) or not target.strip() or len(target) > 4000:
                    raise ValueError("Invalid target")
                result = self.memory.search(query=target, user_id=namespace, limit=100)
                rows = result.get("results", []) if isinstance(result, dict) else result
                normalize = lambda text: re.sub(r"[\W_]", "", text).casefold()
                deleted = 0
                for row in rows:
                    if row.get("user_id", namespace) != namespace:
                        continue
                    if normalize(row.get("memory", row.get("content", ""))) == normalize(target):
                        self.memory.delete(row["id"], user_id=namespace, delete_profile=True)
                        deleted += 1
                return {"deleted": deleted}
            raise ValueError("Unknown operation")
        finally:
            self.lock.release()


def handler_for(service, token):
    if not token:
        raise ValueError("POWER_MEM_SERVICE_TOKEN is required")
    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *args):
            pass
        def respond(self, status, payload):
            body = json.dumps(payload, ensure_ascii=False, default=str).encode()
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        def do_POST(self):
            if not hmac.compare_digest(self.headers.get("Authorization", ""), "Bearer " + token):
                self.respond(401, {"error": "Unauthorized"})
                return
            try:
                length = int(self.headers.get("Content-Length", "0"))
                if not 0 < length <= 512 * 1024:
                    raise ValueError("Invalid body size")
                data = json.loads(self.rfile.read(length))
                if not isinstance(data, dict):
                    raise ValueError("Invalid JSON object")
                self.respond(200, service.execute(self.path, data))
            except (ValueError, TypeError):
                self.respond(400, {"error": "Invalid request"})
            except Exception:
                self.respond(503, {"error": "PowerMem operation unavailable"})
    return Handler


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--config", required=True)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8010)
    args = parser.parse_args()
    config = expand_environment(json.loads(Path(args.config).read_text()))
    token = os.environ.get("POWER_MEM_SERVICE_TOKEN", "")
    if not token:
        raise ValueError("POWER_MEM_SERVICE_TOKEN is required")
    from powermem import UserMemory
    service = MemoryService(UserMemory(config=config))
    server = ThreadingHTTPServer((args.host, args.port), handler_for(service, token))
    server.daemon_threads = True
    server.serve_forever()


if __name__ == "__main__":
    main()
