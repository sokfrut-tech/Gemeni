#!/usr/bin/env python3
"""Multi-model router for Jarvis Factory.

Supports any OpenAI-compatible endpoint (Qwen, DeepSeek, local vLLM/Ollama gateways,
etc.). Credentials are environment variables only; never commit keys.
"""
import json, os, sys, urllib.request

PROVIDERS = [
    ("qwen", "JARVIS_QWEN_BASE_URL", "JARVIS_QWEN_API_KEY", "JARVIS_QWEN_MODEL"),
    ("deepseek", "JARVIS_DEEPSEEK_BASE_URL", "JARVIS_DEEPSEEK_API_KEY", "JARVIS_DEEPSEEK_MODEL"),
    ("generic", "JARVIS_LLM_BASE_URL", "JARVIS_LLM_API_KEY", "JARVIS_LLM_MODEL"),
]

def call(provider, prompt, system):
    name, base_var, key_var, model_var = provider
    base = os.getenv(base_var)
    key = os.getenv(key_var, "")
    model = os.getenv(model_var) or ("qwen3.5" if name == "qwen" else "deepseek" if name == "deepseek" else "qwen3.5")
    if not base:
        return None
    req = urllib.request.Request(
        base.rstrip("/") + "/chat/completions",
        data=json.dumps({"model": model, "temperature": 0, "messages": [
            {"role": "system", "content": system},
            {"role": "user", "content": prompt},
        ]}).encode(),
        headers={"Content-Type": "application/json", **({"Authorization": "Bearer " + key} if key else {})},
    )
    with urllib.request.urlopen(req, timeout=300) as response:
        data = json.loads(response.read())
    return {"provider": name, "model": model, "content": data["choices"][0]["message"]["content"]}

def run(prompt, system="You are a strict Jarvis Factory engineering reviewer. Never claim a feature works without runtime evidence. Return blockers, fixes and tests."):
    results=[]
    for p in PROVIDERS:
        try:
            result=call(p, prompt, system)
            if result: results.append(result)
        except Exception as exc:
            results.append({"provider": p[0], "error": str(exc)})
    if not results:
        raise RuntimeError("No model endpoint configured. Configure at least one JARVIS_*_BASE_URL/API_KEY pair.")
    return results

if __name__ == "__main__":
    prompt=sys.stdin.read()
    print(json.dumps(run(prompt), ensure_ascii=False, indent=2))
