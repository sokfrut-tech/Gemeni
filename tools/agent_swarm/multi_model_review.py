#!/usr/bin/env python3
import json, pathlib, sys
from model_router import run

repo = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else '.')
evidence = []
for p in sorted(repo.rglob('*')):
    if p.is_file() and '.git' not in p.parts and 'build' not in p.parts and p.suffix in {'.kt','.cpp','.h','.xml','.kts','.yml','.md','.py'}:
        try:
            text=p.read_text(errors='replace')
            evidence.append(f'FILE {p.relative_to(repo)}\n{text[:12000]}')
        except Exception: pass
prompt = '''Audit Jarvis as a real product, not a compilation exercise. Identify every feature that is only a UI placeholder, every disconnected pipeline, and every missing runtime test. Check: Compose UI, GLES2/JNI rendering, AudioRecord, Silero VAD, hotword KWS, Sherpa Russian ASR, Piper TTS, intent/tool side effects, AIDL, VoiceInteractionService/session, Accessibility, model downloads, memory, Gemini fallback, security and OPPO/ColorOS constraints. Return strict JSON-like findings with severity, file evidence, fix and test.\n\n''' + '\n\n'.join(evidence)
results=run(prompt)
out=repo/'build'/'multi-model-review.json'
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(json.dumps(results, ensure_ascii=False, indent=2))
print(out)
