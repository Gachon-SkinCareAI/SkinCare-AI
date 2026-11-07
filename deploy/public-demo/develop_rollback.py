"""Durable, verified rollback records for the bounded develop service release."""
import copy
import json
import re
import shutil
import urllib.request
from pathlib import Path

from run_free_demo import checksum, json_write
from update_analysis_speed import same_image_content

FILES = frozenset({'backend/app.jar', 'ai/app/routers/analyze.py', 'ai/app/schemas/qa.py'})


def bounded(root, name):
    path = (root / name).resolve()
    if not path.is_relative_to(root.resolve()) or path == root.resolve():
        raise RuntimeError('Rollback path is outside its directory')
    return path


def upgraded_manifest(original, release, backup_name):
    result = copy.deepcopy(original)
    result['developRelease'] = release['id']
    result['developRollback'] = backup_name
    result['backendSha256'] = release['files']['backend/app.jar']
    result['sources'].update(release['files'])
    return result


def save_rollback_record(backup, release, images, private_snapshot):
    if not re.fullmatch(r'before-[0-9]+', backup.name):
        raise RuntimeError('Unexpected backup name')
    for name, expected in release['baseFiles'].items():
        if checksum(bounded(backup, name)) != expected:
            raise RuntimeError('Backup file identity differs')
    original = json.loads((backup / 'bundle-manifest.json').read_text(encoding='utf-8'))
    protected = dict(release['protectedFiles'])
    protected.update(private_snapshot)
    data = {'format': 'skincare-develop-rollback-v1', 'release': release['id'],
            'project': release['project'], 'origin': release['origin'], 'backupName': backup.name,
            'oldFiles': release['baseFiles'], 'newFiles': release['files'], 'protectedFiles': protected,
            'oldManifestSha256': checksum(backup / 'bundle-manifest.json'),
            'newManifest': upgraded_manifest(original, release, backup.name), 'images': images}
    receipt = backup / 'rollback.json'
    if receipt.exists():
        raise RuntimeError('Never overwrite an existing rollback record')
    json_write(receipt, data)
    json_write(backup.parent / 'latest-rollback.json', {
        'format': 'skincare-develop-rollback-pointer-v1', 'backupName': backup.name,
        'receiptSha256': checksum(receipt)})
    return data


def verify_protected(bundle, record):
    for name, expected in record['protectedFiles'].items():
        if name in FILES or checksum(bounded(bundle, name)) != expected:
            raise RuntimeError('Protected configuration, model or APK changed; rollback stopped')


def load_rollback_record(bundle, operation):
    pointer_file = operation / 'latest-rollback.json'
    if not pointer_file.is_file():
        raise RuntimeError('No verified pre-update backup exists yet; run the updated deployment tool first')
    pointer = json.loads(pointer_file.read_text(encoding='utf-8'))
    if (pointer.get('format') != 'skincare-develop-rollback-pointer-v1'
            or not re.fullmatch(r'before-[0-9]+', pointer.get('backupName', ''))):
        raise RuntimeError('Unexpected rollback pointer')
    backup = bounded(operation, pointer['backupName'])
    if checksum(backup / 'rollback.json') != pointer.get('receiptSha256'):
        raise RuntimeError('Rollback record checksum mismatch')
    record = json.loads((backup / 'rollback.json').read_text(encoding='utf-8'))
    if (record.get('format') != 'skincare-develop-rollback-v1' or record.get('backupName') != backup.name
            or set(record.get('oldFiles', {})) != FILES or set(record.get('newFiles', {})) != FILES
            or set(record.get('images', {})) != {'ai', 'backend'}):
        raise RuntimeError('Unexpected rollback record')
    if checksum(backup / 'bundle-manifest.json') != record['oldManifestSha256']:
        raise RuntimeError('Saved deployment manifest was changed')
    original = json.loads((backup / 'bundle-manifest.json').read_text(encoding='utf-8'))
    current = json.loads((bundle / 'bundle-manifest.json').read_text(encoding='utf-8'))
    project = record['project']
    if not re.fullmatch(r'skincare-free-demo-[0-9a-f]{8}', project) or original.get('project') != project:
        raise RuntimeError('Unexpected deployment project')
    if current not in (original, record['newManifest']):
        raise RuntimeError('Another deployment changed the manifest; rollback stopped')
    for name in FILES:
        if checksum(bounded(backup, name)) != record['oldFiles'][name]:
            raise RuntimeError('Saved source file was changed')
        if checksum(bounded(bundle, name)) not in (record['oldFiles'][name], record['newFiles'][name]):
            raise RuntimeError('Another deployment changed the service files; rollback stopped')
    for service, image in record['images'].items():
        if (image.get('tag') not in (project + '-' + service, project + '-' + service + ':latest')
                or not re.fullmatch(re.escape(project + '-' + service + ':before-develop-') + r'[0-9]+', image.get('backupTag', ''))
                or not re.fullmatch(r'sha256:[0-9a-f]{64}', image.get('id', ''))):
            raise RuntimeError('Invalid saved image identity')
    if not {'compose.yaml', 'migrations/V008__capture_date_and_symptoms.sql'}.issubset(record['protectedFiles']):
        raise RuntimeError('Missing protected configuration identities')
    verify_protected(bundle, record)
    return backup, record, original


def verify_images(record, command):
    for service, image in record['images'].items():
        saved = json.loads(command(['docker', 'image', 'inspect', image['backupTag']],
                                   'verify-saved-' + service, 30, False))[0]
        if saved.get('Id') != image['id']:
            raise RuntimeError('Saved image was removed or changed; services were not stopped')


def verify_previous_public(origin):
    for path in ('/api/wc/auth/guest/policy', '/admin/', '/admin/app.js', '/admin/style.css', '/download/'):
        with urllib.request.urlopen(origin + path, timeout=30) as response:
            data = response.read()
            if response.status != 200 or not data:
                raise RuntimeError('Restored public endpoint failed')
            if path == '/admin/' and b'id="login-form"' not in data:
                raise RuntimeError('Restored administrator login is missing')
            if path.endswith('.js') and 'javascript' not in response.headers.get('Content-Type', ''):
                raise RuntimeError('Restored script has an invalid content type')


def restore_services(bundle, backup, record, compose, command, require_launcher, public_check=verify_previous_public):
    """Restore only known files/images. Database volumes and the tunnel are untouched."""
    if (bundle / 'startup-stability.json').exists():
        raise RuntimeError('Roll back the startup stability overlay before restoring the earlier service release')
    verify_images(record, command)
    require_launcher()
    try:
        command(compose + ['stop', 'edge', 'backend', 'ai'], 'close-access-for-rollback', 150)
        for name in FILES:
            shutil.copy2(backup / name, bundle / name)
        shutil.copy2(backup / 'bundle-manifest.json', bundle / 'bundle-manifest.json')
        for service, image in record['images'].items():
            # Tag the immutable verified ID, never an unverified mutable tag.
            command(['docker', 'tag', image['id'], image['tag']], 'restore-' + service + '-image', 30)
        for service in ('ai', 'backend'):
            require_launcher()
            command(compose + ['up', '-d', '--no-deps', '--no-build', '--pull', 'never', '--force-recreate',
                               '--wait', '--wait-timeout', '300', service], 'restore-' + service, 350)
            container = json.loads(command(['docker', 'inspect', record['project'] + '-' + service + '-1'],
                                           'verify-restored-' + service, 30, False))[0]
            labels = container['Config'].get('Labels') or {}
            if (not container['State']['Running'] or labels.get('com.docker.compose.project') != record['project']
                    or labels.get('com.docker.compose.service') != service):
                raise RuntimeError('Restored container ownership differs')
            image = record['images'][service]
            expected = {'Image': image.get('containerImageDigest'),
                        'ImageManifestDescriptor': {'digest': image.get('containerManifestDigest')},
                        'Config': {'Labels': {'com.docker.compose.image': image.get('composeRecordedImage')}}}
            same_image_content(expected, container, image['id'])
        command(compose + ['exec', '-T', 'ai', 'python', '/opt/demo/probe.py', 'ai'], 'restored-ai-health', 60)
        for path in ('/api/wc/auth/guest/policy', '/admin/', '/admin/app.js'):
            command(compose + ['exec', '-T', 'backend', 'curl', '-fsS', 'http://127.0.0.1:9800' + path],
                    'restored-backend-health', 30, False)
        verify_protected(bundle, record)
        require_launcher()
        command(compose + ['up', '-d', '--no-deps', '--no-build', '--pull', 'never', '--wait', '--wait-timeout', '90', 'edge'],
                'reopen-previous-server', 120)
        public_check(record['origin'])
    except Exception:
        try:
            command(compose + ['stop', 'edge'], 'keep-failed-rollback-private', 90)
        except Exception:
            pass
        raise
