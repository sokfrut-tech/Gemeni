import os, sys, urllib.request, json
from urllib.parse import urlsplit

workers = {
    'qwen': os.getenv('QWEN_ENDPOINT', 'http://localhost:8001'),
    'deepseek': os.getenv('DEEPSEEK_ENDPOINT', 'http://localhost:8002'),
}
failed = False
for name, base in workers.items():
    try:
        parsed = urlsplit(base)
        if parsed.scheme not in {"http", "https"} or not parsed.hostname or parsed.username or parsed.password or parsed.query or parsed.fragment:
            raise ValueError("Worker endpoint must be HTTP(S) without embedded credentials")
        # nosemgrep
        req = urllib.request.Request(base.rstrip('/') + '/health')
        # nosemgrep
        with urllib.request.urlopen(req, timeout=10) as r:
            print(name, r.status, r.read().decode()[:500])
        # nosemgrep
        req = urllib.request.Request(base.rstrip('/') + '/v1/models')
        # nosemgrep
        with urllib.request.urlopen(req, timeout=10) as r:
            data = json.load(r)
            print(name, 'models=', [x.get('id') for x in data.get('data', [])])
    except Exception as exc:
        failed = True
        print(name, 'UNAVAILABLE:', exc)
if failed:
    sys.exit(1)
