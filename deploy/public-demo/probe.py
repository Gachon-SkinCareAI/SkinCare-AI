"""Private health probes. Never print credentials or response bodies."""
import json
from pathlib import Path
import sys
import urllib.request
try:
    kind = sys.argv[1]
    if kind == 'maintenance':
        request = urllib.request.Request('http://127.0.0.1:9100/ready')
    elif kind == 'ai':
        token = Path('/run/secrets/ai_token').read_text().strip()
        request = urllib.request.Request('http://127.0.0.1:8000/api/v1/ai/health', headers={'Authorization': 'Bearer ' + token})
    else:
        raise ValueError('Unknown probe')
    with urllib.request.urlopen(request, timeout=3) as response:
        result = json.load(response)
    if result.get('status') != 'ok':
        raise ValueError('Not ready')
except Exception:
    sys.exit(1)
