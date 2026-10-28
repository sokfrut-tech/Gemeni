import os, sys, urllib.request, json

workers = {
    'qwen': os.getenv('QWEN_ENDPOINT', 'http://localhost:8001'),
    'deepseek': os.getenv('DEEPSEEK_ENDPOINT', 'http://localhost:8002'),
}
failed = False
for name, base in workers.items():
    try:
        req = urllib.request.Request(base.rstrip('/') + '/health')
        with urllib.request.urlopen(req, timeout=10) as r:
            print(name, r.status, r.read().decode()[:500])
        req = urllib.request.Request(base.rstrip('/') + '/v1/models')
        with urllib.request.urlopen(req, timeout=10) as r:
            data = json.load(r)
            print(name, 'models=', [x.get('id') for x in data.get('data', [])])
    except Exception as exc:
        failed = True
        print(name, 'UNAVAILABLE:', exc)
if failed:
    sys.exit(1)
