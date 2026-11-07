"""Bounded read-only source overlay; the original images and bundle remain restorable."""
import json
from pathlib import Path
import re

from run_free_demo import checksum

FILES = {
    'ai/app/main.py': ('ai', '/app/app/main.py'),
    'ai/app/vision_worker.py': ('ai', '/app/app/vision_worker.py'),
    'ai/app/routers/predict.py': ('ai', '/app/app/routers/predict.py'),
    'ai/app/routers/tracking.py': ('ai', '/app/app/routers/tracking.py'),
    'ai/vision/pipeline.py': ('ai', '/app/vision/pipeline.py'),
    'maintenance.py': ('maintenance', '/opt/demo/maintenance.py'),
    'mysql/retention.py': ('maintenance', '/opt/mysql-tools/retention.py'),
    'Caddyfile.tunnel': ('edge', '/etc/caddy/Caddyfile'),
}


def validate_release(folder, expected_project=None):
    folder = Path(folder).resolve()
    data = json.loads((folder / 'release.json').read_text(encoding='utf-8'))
    if (data.get('format') != 'skincare-startup-stability-v1'
            or not re.fullmatch(r'skincare-free-demo-[0-9a-f]{8}', data.get('project', ''))
            or set(data.get('files', {})) != set(FILES)
            or not re.fullmatch(r'startup-[0-9]{14}', data.get('id', ''))):
        raise RuntimeError('Unexpected startup stability release')
    if expected_project and data['project'] != expected_project:
        raise RuntimeError('Release belongs to another project')
    for name, digest in data['files'].items():
        path = (folder / name).resolve()
        if not path.is_relative_to(folder) or checksum(path) != digest:
            raise RuntimeError('Startup source checksum mismatch')
    return data


def overlay_config(folder):
    folder = Path(folder).resolve()
    services = {}
    for name, (service, destination) in FILES.items():
        services.setdefault(service, {'volumes': []})['volumes'].append({
            'type': 'bind', 'source': str(folder / name), 'target': destination,
            'read_only': True, 'bind': {'create_host_path': False}})
    services['ai']['environment'] = {'AI_CPU_THREADS': '2'}
    services['ai']['healthcheck'] = {'start_period': '600s'}
    return {'services': services}


def active_overlay(bundle, manifest):
    bundle = Path(bundle).resolve()
    record = json.loads((bundle / 'startup-stability.json').read_text(encoding='utf-8'))
    if (record.get('format') != 'skincare-startup-stability-active-v1'
            or record.get('project') != manifest['project']
            or not re.fullmatch(r'startup-[0-9]{14}', record.get('release', ''))
            or checksum(bundle / 'bundle-manifest.json') != record.get('baseManifestSha256')):
        raise RuntimeError('Startup overlay base deployment changed')
    folder = bundle / 'startup-stability' / record['release']
    release = validate_release(folder, manifest['project'])
    if checksum(folder / 'release.json') != record.get('releaseSha256'):
        raise RuntimeError('Startup release identity changed')
    overlay = folder / 'compose.startup-stability.json'
    if (checksum(overlay) != record.get('overlaySha256')
            or json.loads(overlay.read_text(encoding='utf-8')) != overlay_config(folder)
            or release['id'] != record['release']):
        raise RuntimeError('Unexpected startup overlay configuration')
    return overlay
