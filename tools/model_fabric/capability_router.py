#!/usr/bin/env python3
"""Jarvis Hive capability router.

Routes jobs only to explicitly configured, healthy, authorized workers.
It never discovers or harvests credentials and never bypasses provider controls.
"""
from __future__ import annotations
import json, os, sys, time
from dataclasses import dataclass
from urllib.request import Request, urlopen

@dataclass
class Worker:
    name: str
    endpoint: str
    model: str
    capabilities: set[str]
    token_env: str | None = None
    timeout: float = 8.0

    def health(self) -> bool:
        try:
            req = Request(self.endpoint.rstrip('/') + '/health')
            with urlopen(req, timeout=self.timeout) as r:
                return 200 <= r.status < 300
        except Exception:
            return False

    def chat(self, prompt: str) -> str:
        body = json.dumps({
            "model": self.model,
            "messages": [{"role": "user", "content": prompt}],
            "temperature": 0.1,
        }).encode()
        headers = {"Content-Type": "application/json"}
        if self.token_env and os.getenv(self.token_env):
            headers["Authorization"] = "Bearer " + os.environ[self.token_env]
        req = Request(self.endpoint.rstrip('/') + '/v1/chat/completions', data=body, headers=headers)
        with urlopen(req, timeout=120) as r:
            data = json.load(r)
        return data["choices"][0]["message"]["content"]


def configured_workers() -> list[Worker]:
    workers = []
    specs = [
        ("qwen", "JARVIS_QWEN_BASE_URL", "JARVIS_QWEN_MODEL", "JARVIS_QWEN_API_KEY"),
        ("deepseek", "JARVIS_DEEPSEEK_BASE_URL", "JARVIS_DEEPSEEK_MODEL", "JARVIS_DEEPSEEK_API_KEY"),
        ("generic", "JARVIS_LLM_BASE_URL", "JARVIS_LLM_MODEL", "JARVIS_LLM_API_KEY"),
    ]
    for name, url_key, model_key, token_key in specs:
        endpoint, model = os.getenv(url_key), os.getenv(model_key)
        if endpoint and model:
            workers.append(Worker(name, endpoint, model, {"reasoning", "code_review", "research"}, token_key))
    return workers


def choose(workers: list[Worker], capability: str) -> Worker | None:
    candidates = [w for w in workers if capability in w.capabilities and w.health()]
    return candidates[0] if candidates else None


def main() -> int:
    if len(sys.argv) < 3:
        print("usage: capability_router.py <capability> <prompt>")
        return 2
    capability, prompt = sys.argv[1], " ".join(sys.argv[2:])
    worker = choose(configured_workers(), capability)
    if not worker:
        print(json.dumps({"status": "no_worker", "capability": capability}))
        return 3
    started = time.time()
    try:
        answer = worker.chat(prompt)
        print(json.dumps({"status": "ok", "worker": worker.name, "model": worker.model,
                          "latency_s": round(time.time()-started, 3), "answer": answer}, ensure_ascii=False))
        return 0
    except Exception as exc:
        print(json.dumps({"status": "worker_error", "worker": worker.name, "error": str(exc)}))
        return 4

if __name__ == "__main__":
    raise SystemExit(main())
