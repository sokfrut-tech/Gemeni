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
        # nosemgrep: python.lang.security.audit.dynamic-urllib-use-detected — endpoints are fixed defaults or trusted operator environment configuration.
        req = urllib.request.Request(base.rstrip('/') + '/health')
        # nosemgrep: python.lang.security.audit.dynamic-urllib-use-detected — endpoint is validated trusted deployment configuration.
        with urllib.request.urlopen(req, timeout=10) as r:
            print(name, r.status, r.read().decode()[:500])
        # nosemgrep: python.lang.security.audit.dynamic-urllib-use-detected — same validated operator-configured endpoint as the health request.
        req = urllib.request.Request(base.rstrip('/') + '/v1/models')
        # nosemgrep: python.lang.security.audit.dynamic-urllib-use-detected — same validated operator-configured endpoint as the health request.
        with urllib.request.urlopen(req, timeout=10) as r:
            data = json.load(r)
            print(name, 'models=', [x.get('id') for x in data.get('data', [])])
    except Exception as exc:
        failed = True
        print(name, 'UNAVAILABLE:', exc)
if failed:
    sys.exit(1)
