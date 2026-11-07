"""Exact, read-only AI/retention overlay for an active public release."""
import json
from pathlib import Path
import shutil
from run_free_demo import checksum, json_write

FILES = {
    'ai/qa/orchestrator.py': ('ai', '/app/qa/orchestrator.py'),
    'ai/eval_classify.py': ('ai', '/app/eval_classify.py'),
    'ai/app/main.py': ('ai', '/app/app/main.py'),
    'ai/app/vision_worker.py': ('ai', '/app/app/vision_worker.py'),
    'ai/app/routers/health.py': ('ai', '/app/app/routers/health.py'),
    'ai/vision/pipeline.py': ('ai', '/app/vision/pipeline.py'),
    'deploy/mysql/retention.py': ('maintenance', '/opt/mysql-tools/retention.py'),
}
ENVIRONMENT = {'AI_VISION_TIMEOUT_SECONDS': '40', 'AI_VISION_WATCHDOG_EXIT': '1',
               'AI_STARTUP_TIMEOUT_SECONDS': '120'}

def package(source, destination):
    source, destination = Path(source).resolve(), Path(destination).resolve()
    destination.mkdir(parents=True, exist_ok=False)
    for name in FILES:
        target = destination/name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source/name, target)
    json_write(destination/'release.json', {'format': 'skincare-upload-recovery-v1',
        'files': {name: checksum(destination/name) for name in FILES}})
    return validate(destination)

def validate(folder, *, allow_previous=False):
    folder = Path(folder).resolve()
    release = json.loads((folder/'release.json').read_text(encoding='utf-8'))
    expected = set(FILES)
    known_previous = expected - {'ai/qa/orchestrator.py'}
    actual = set(release.get('files', {}))
    if (release.get('format') != 'skincare-upload-recovery-v1'
            or not (actual == expected or allow_previous and actual == known_previous)):
        raise RuntimeError('Unexpected upload recovery release')
    for name, digest in release['files'].items():
        path = (folder/name).resolve()
        if not path.is_relative_to(folder) or path.is_symlink() or checksum(path) != digest:
            raise RuntimeError('Upload recovery source changed')
    return release

def extend(overlay, folder):
    folder = Path(folder).resolve()/'upload-recovery'
    if not folder.exists():
        return overlay
    release = validate(folder, allow_previous=True)
    for name in release['files']:
        service, target = FILES[name]
        overlay['services'].setdefault(service, {}).setdefault('volumes', []).append({
            'type': 'bind', 'source': str(folder/name), 'target': target,
            'read_only': True, 'bind': {'create_host_path': False}})
    overlay['services']['ai'].setdefault('environment', {}).update(ENVIRONMENT)
    return overlay

def validate_compose(before, after):
    if {k:v for k,v in before.items() if k != 'services'} != {k:v for k,v in after.items() if k != 'services'}:
        raise RuntimeError('Networks or volumes changed')
    if set(before['services']) != set(after['services']):
        raise RuntimeError('Service identity changed')
    for name, service in before['services'].items():
        actual = after['services'][name]
        if name not in ('ai', 'maintenance'):
            if actual != service:
                raise RuntimeError('Unrelated service changed: '+name)
            continue
        allowed = {'volumes', 'environment'} if name == 'ai' else {'volumes'}
        if {k:v for k,v in actual.items() if k not in allowed} != {k:v for k,v in service.items() if k not in allowed}:
            raise RuntimeError('Protected runtime settings changed')
        changed_targets = {target for owner,target in FILES.values() if owner == name}
        previous_mounts = {m['target']: m for m in service.get('volumes', [])}
        next_mounts = {m['target']: m for m in actual.get('volumes', [])}
        for target, mount in previous_mounts.items():
            if target not in changed_targets and next_mounts.get(target) != mount:
                raise RuntimeError('Unrelated volume changed')
        if set(next_mounts)-set(previous_mounts)-changed_targets:
            raise RuntimeError('Unexpected additional mount')
        for target in changed_targets:
            mount = next_mounts.get(target, {})
            if mount.get('type') != 'bind' or mount.get('read_only') is not True:
                raise RuntimeError('Recovery source must be read-only')
        if name == 'ai':
            if actual.get('restart') != 'unless-stopped':
                raise RuntimeError('AI watchdog requires the existing supervisor')
            expected = dict(service.get('environment', {}), **ENVIRONMENT)
            if actual.get('environment') != expected:
                raise RuntimeError('Unexpected environment change')
