"""Load the packaged baseline with the existing inference contract unchanged."""
import hashlib
import json
import logging
import os
from pathlib import Path
import sys

sys.path.insert(0, '/app')
token = Path('/run/secrets/ai_token').read_text().strip()
if len(token) < 32:
    raise SystemExit('Missing AI service credential')
os.environ['AI_SERVICE_TOKEN'] = token
os.environ['AI_VISION_WARMUP'] = '1'
# Explicitly enable only the application's metadata-only timing loggers. Do not
# enable global HTTP/client debug logs, which may contain credentials or text.
for name in ('skincare.access', 'skincare.performance'):
    logger = logging.getLogger(name)
    logger.handlers = [logging.StreamHandler()]
    logger.setLevel(logging.INFO)
    logger.propagate = False
manifest = json.loads(Path('/models/manifest.json').read_text())
for filename in ('detector.pt', 'classifier.pt', 'energy.json'):
    checksum = hashlib.sha256()
    with (Path('/models') / filename).open('rb') as stream:
        for part in iter(lambda: stream.read(1024 * 1024), b''):
            checksum.update(part)
    if checksum.hexdigest() != manifest['files'][filename]['sha256']:
        raise SystemExit('Packaged model checksum mismatch')
import uvicorn
uvicorn.run('app.main:app', host='0.0.0.0', port=8000, workers=1, access_log=False)
