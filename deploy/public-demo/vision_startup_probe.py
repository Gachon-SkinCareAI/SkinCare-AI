"""Exercise live HTTP inference with generated pixels or an approved validation photo."""
import argparse
import io
import json
from pathlib import Path
import sys
import time
import urllib.request

import numpy as np
from PIL import Image


def inputs(photo=None, photo_bytes=None):
    if photo is not None or photo_bytes is not None:
        raw = Path(photo).read_bytes() if photo is not None else photo_bytes
        if not 0 < len(raw) <= 8 * 1024 * 1024 or not raw.startswith(b'\xff\xd8'):
            raise RuntimeError('Validation photo must be a JPEG within the API size limit')
        yield raw
        return
    for shape in ((1200, 1600, 3), (1600, 1200, 3)):
        pixels = np.random.default_rng(20261107).integers(40, 210, shape, dtype=np.uint8)
        encoded = io.BytesIO()
        Image.fromarray(pixels).save(encoded, format='JPEG', quality=88)
        yield encoded.getvalue()


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    source = parser.add_mutually_exclusive_group()
    source.add_argument('--photo')
    source.add_argument('--stdin-photo', action='store_true')
    args = parser.parse_args(argv)
    raw_photo = sys.stdin.buffer.read(8 * 1024 * 1024 + 1) if args.stdin_photo else None
    validation_photo = bool(args.photo) or args.stdin_photo
    token = Path('/run/secrets/ai_token').read_text().strip()
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    checks = []
    for raw in inputs(args.photo, raw_photo):
        boundary = 'skincare-startup-synthetic'
        body = (('--' + boundary + '\r\nContent-Disposition: form-data; name="file"; filename="startup.jpg"\r\n'
                 'Content-Type: image/jpeg\r\n\r\n').encode() + raw
                + ('\r\n--' + boundary + '--\r\n').encode())
        request = urllib.request.Request('http://127.0.0.1:8000/api/v1/ai/predict', data=body,
            headers={'Authorization': 'Bearer ' + token, 'Content-Type': 'multipart/form-data; boundary=' + boundary})
        started = time.monotonic()
        with opener.open(request, timeout=45) as response:
            result = json.load(response)
            if response.status != 200 or result.get('quality', {}).get('ok') is not True:
                raise RuntimeError('Live vision startup check failed')
        seconds = round(time.monotonic() - started, 3)
        checks.append({'status': 200, 'seconds': seconds, 'qualityPassed': True,
                       'classifierReached': 'energy' in (result.get('classification') or {})})
        if seconds > 30:
            raise RuntimeError('Vision startup exceeds the first-upload latency budget')
    print(json.dumps({'passed': True, 'syntheticInputOnly': not validation_photo,
                      'validationPhoto': validation_photo,
                      'patientRecordsCreated': False, 'checks': checks}), flush=True)


if __name__ == '__main__':
    main()
