#!/usr/bin/env python3
import argparse, json, os, pathlib, re, subprocess, urllib.request

ROLES = [
    ("android_coloros", "VoiceInteractionService, VoiceInteractionSession, ColorOS integration, permissions, lifecycle"),
    ("ui_ux", "Compose UI, rendered states, real clickable behavior, accessibility semantics, no placeholder-only screen"),
    ("ndk_gles", "JNI, EGL/GLES2, Surface lifecycle, native crashes, ABI"),
    ("speech", "AudioRecord, VAD, KWS, Sherpa Russian ASR, Piper/VITS TTS, offline model lifecycle"),
    ("functional", "AIDL and real side effects for navigation/search/phone/tools"),
    ("security", "exported components, permissions, secrets, downloads, native loading"),
    ("qa", "runtime/e2e testing, OPPO device acceptance, release evidence"),
]


def files(repo):
    out=[]
    for p in pathlib.Path(repo).rglob('*'):
        if p.is_file() and '.git' not in p.parts and 'build' not in p.parts:
            out.append(str(p.relative_to(repo)))
    return out


def source(repo, paths):
    chunks=[]
    for rel in paths:
        if rel.endswith(('.kt','.java','.cpp','.h','.xml','.kts','.gradle','.yml','.yaml','.md')):
            try:
                text=(pathlib.Path(repo)/rel).read_text(errors='replace')
                if len(text)>18000: text=text[:18000]+'\n...[truncated]'
                chunks.append(f'FILE: {rel}\n{text}')
            except Exception: pass
    return '\n\n'.join(chunks)


def deterministic(repo, out):
    all_files=files(repo)
    text=source(repo, all_files)
    checks=[]
    placeholders=['TODO','FIXME','NotImplementedException','return Unit','return null','stub','placeholder','fake','dummy']
    for x in placeholders:
        n=len(re.findall(re.escape(x), text, re.I))
        if n: checks.append({'check':f'placeholder:{x}','count':n,'status':'WARN'})
    for needle in ['VoiceInteractionService','VoiceInteractionSession','AccessibilityService','AudioRecord','Sherpa','Piper','Silero','native','Intent']:
        checks.append({'check':f'architecture:{needle}','count':len(re.findall(re.escape(needle), text, re.I)),'status':'PASS' if re.search(re.escape(needle),text,re.I) else 'FAIL'})
    tests=[x for x in all_files if '/androidTest/' in '/'+x or '/test/' in '/'+x or x.startswith('script/') or x.startswith('tools/')]
    report={'files':len(all_files),'checks':checks,'test_and_tool_files':tests}
    pathlib.Path(out,'deterministic.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
    return report


def llm_call(prompt):
    base=os.environ.get('JARVIS_LLM_BASE_URL')
    if not base: return None
    url=base.rstrip('/')+'/chat/completions'
    body={'model':os.environ.get('JARVIS_LLM_MODEL','qwen3.5'),'temperature':0,'messages':[{'role':'system','content':'You are an independent release auditor. Never claim success without concrete evidence. Return JSON with verdict, blockers, required_fixes, evidence_queries.'},{'role':'user','content':prompt}]}
    req=urllib.request.Request(url,data=json.dumps(body).encode(),headers={'Content-Type':'application/json','Authorization':'Bearer '+os.environ.get('JARVIS_LLM_API_KEY','')})
    with urllib.request.urlopen(req,timeout=180) as r:
        data=json.loads(r.read())
    return data['choices'][0]['message']['content']


def main():
    ap=argparse.ArgumentParser(); ap.add_argument('--repo',default='.'); ap.add_argument('--out',default='build/agent-review'); ap.add_argument('--llm',action='store_true'); a=ap.parse_args()
    pathlib.Path(a.out).mkdir(parents=True,exist_ok=True)
    det=deterministic(a.repo,a.out)
    src=source(a.repo, files(a.repo))
    reviews={}
    for role,focus in ROLES:
        prompt=f'Project Gemeni Jarvis. Role: {role}. Audit focus: {focus}.\n\nEvidence from repository:\n{src}'
        if a.llm:
            try: reviews[role]=llm_call(prompt)
            except Exception as e: reviews[role]=f'LLM unavailable: {e}'
        else: reviews[role]='LLM not configured; deterministic evidence only.'
    pathlib.Path(a.out,'reviews.json').write_text(json.dumps(reviews,ensure_ascii=False,indent=2))
    hard_fail=[x for x in det['checks'] if x['status']=='FAIL']
    verdict='BLOCKED' if hard_fail else 'REQUIRES_RUNTIME_EVIDENCE'
    pathlib.Path(a.out,'RELEASE_GATE.md').write_text('# Jarvis release gate\n\n**Verdict: '+verdict+'**\n\nCompilation alone is not acceptance. Required evidence: emulator runtime tests, real tool side effects, speech pipeline tests, VoiceInteraction invocation, and OPPO/ColorOS device validation.\n')
    print(verdict)

if __name__=='__main__': main()
