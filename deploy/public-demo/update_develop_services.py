"""Replace the reviewed backend/AI files while retaining the live tunnel, APK and DB.

This release makes no schema changes. Every candidate and existing runtime file
is checked before any service is replaced; previous images remain available for
rollback. The existing public launcher must remain running.
"""
import argparse
import json
from pathlib import Path
import shutil
import subprocess
import time
import urllib.request

from prepare_bundle import protect
from run_free_demo import HIDDEN, checksum, compose_command, instance_lock, json_write
from update_analysis_speed import resolve_restore_image
from update_guest_demo import BACKUP, last_json
from upgrade_free_demo import validate_existing_volumes
from develop_rollback import load_rollback_record, restore_services, save_rollback_record, upgraded_manifest

FILES = frozenset({'backend/app.jar', 'ai/app/routers/analyze.py', 'ai/app/schemas/qa.py'})
MODULES = ('api', 'audit', 'constants', 'dashboard', 'hospitals', 'inquiries',
           'operations', 'reviews', 'state', 'ui')


def bounded(root, name):
    path = (root / name).resolve()
    if not path.is_relative_to(root.resolve()) or path == root.resolve():
        raise RuntimeError('Release path is outside its directory')
    return path


def validate_release(source, bundle, applied=False):
    data = json.loads((source / 'release.json').read_text(encoding='utf-8'))
    if (data.get('format') != 'skincare-develop-services-v1'
            or set(data.get('files', {})) != FILES or set(data.get('baseFiles', {})) != FILES):
        raise RuntimeError('Unexpected release file set')
    for name, expected in data['files'].items():
        if checksum(bounded(source, name)) != expected:
            raise RuntimeError('Candidate checksum mismatch: ' + name)
    current = data['files'] if applied else data['baseFiles']
    for name, expected in current.items():
        if checksum(bounded(bundle, name)) != expected:
            raise RuntimeError('Deployment changed since packaging: ' + name)
    for name, expected in data.get('protectedFiles', {}).items():
        if name in FILES or checksum(bounded(bundle, name)) != expected:
            raise RuntimeError('Protected deployment file changed')
    required = {'compose.yaml', 'migrations/V008__capture_date_and_symptoms.sql'}
    if not required.issubset(data.get('protectedFiles', {})):
        raise RuntimeError('Missing protected configuration identities')
    return data


def verify_public(origin):
    for path in ('/api/wc/auth/guest/policy', '/admin/', '/admin/app.js', '/download/',
                 *('/admin/src/' + module + '.js' for module in MODULES)):
        with urllib.request.urlopen(origin + path, timeout=30) as response:
            data = response.read()
            if response.status != 200 or not data:
                raise RuntimeError('Public asset verification failed')
            if path.endswith('.js') and 'javascript' not in response.headers.get('Content-Type', ''):
                raise RuntimeError('Public module has an invalid content type')
            if path == '/admin/' and b'type="module"' not in data:
                raise RuntimeError('The new administrator application is not being served')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config', required=True)
    parser.add_argument('--release', required=True)
    parser.add_argument('--check', action='store_true', help='Validate identities and Docker access without updating services')
    args = parser.parse_args()
    config = json.loads(Path(args.config).read_text(encoding='utf-8-sig'))
    if config.get('format') != 'skincare-free-demo-v1':
        raise RuntimeError('Unexpected launcher configuration')
    root = Path(config['runtime']).resolve()
    out = Path(config['output']).resolve()
    bundle = root / 'bundle'
    marker = bundle / 'bundle-manifest.json'
    manifest = json.loads(marker.read_text(encoding='utf-8'))
    source = Path(args.release).resolve()
    candidate = json.loads((source / 'release.json').read_text(encoding='utf-8'))
    applied = manifest.get('developRelease') == candidate.get('id')
    release = validate_release(source, bundle, applied)
    project = manifest['project']
    if release['project'] != project:
        raise RuntimeError('Release belongs to another deployment')
    compose = compose_command(bundle, manifest)
    origin = release['origin']

    def require_launcher():
        connection = json.loads((out / 'public-connection.json').read_text(encoding='utf-8'))
        if (connection.get('active') is not True or connection.get('origin') != origin
                or (root / 'stop.request').exists()):
            raise RuntimeError('Keep the original public launcher open; its address must match this release')

    require_launcher()
    operation = root / 'develop-upgrade'
    operation.mkdir(exist_ok=True)
    protect(operation)
    report = {'passed': False, 'release': release['id'], 'databaseSchemaChanged': False,
              'modelsChanged': False, 'apkChanged': False, 'tunnelRestarted': False}

    def status(step):
        report.update(step=step, updatedAt=time.time())
        json_write(out / ('develop-update-check.json' if args.check else 'develop-upgrade.json'), report)
        print(step, flush=True)

    def command(argv, name, timeout=180, persist=True):
        status(name)
        result = subprocess.run(argv, cwd=bundle, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                timeout=timeout, creationflags=HIDDEN)
        content = result.stdout.decode('utf-8', errors='replace')
        if persist:
            (operation / (name + '.log')).write_text(content, encoding='utf-8')
        if result.returncode:
            raise RuntimeError(name + ' failed; inspect the private step log')
        return content

    with instance_lock(operation):
        command(['docker', 'version', '--format', '{{.Server.Version}}'], 'docker-engine', 30)
        volumes = json.loads(command(['docker', 'volume', 'inspect', project + '_database', project + '_maintenance'],
                                     'verify-existing-data-volumes', 30, False))
        validate_existing_volumes(volumes, project)
        if args.check:
            report.update(passed=True, checkOnly=True)
            status('preflight-complete')
            return
        if applied:
            load_rollback_record(bundle, operation)
            verify_public(origin)
            report.update(passed=True, alreadyApplied=True)
            status('already-applied')
            return
        backup = operation / ('before-' + str(time.time_ns()))
        backup.mkdir()
        shutil.copy2(marker, backup / marker.name)
        private_snapshot = {str(p.relative_to(bundle)): checksum(p)
                            for directory in ('private', 'models') for p in (bundle / directory).rglob('*') if p.is_file()}
        if (bundle / '.env').is_file():
            private_snapshot['.env'] = checksum(bundle / '.env')
        images = {}
        changed = False
        try:
            for service in ('ai', 'backend'):
                container = json.loads(command(['docker', 'inspect', project + '-' + service + '-1'],
                                               'inspect-' + service, 30, False))[0]
                labels = container['Config'].get('Labels') or {}
                if (labels.get('com.docker.compose.project') != project
                        or labels.get('com.docker.compose.service') != service or not container['State']['Running']):
                    raise RuntimeError('Expected running service was not found')
                identity = resolve_restore_image(container, project, service, command, lambda value: None)
                tag = project + '-' + service + ':before-develop-' + str(time.time_ns())
                command(['docker', 'tag', identity['tag'], tag], 'preserve-' + service, 30)
                saved = json.loads(command(['docker', 'image', 'inspect', tag], 'verify-' + service + '-backup', 30, False))[0]
                if saved['Id'] != identity['id']:
                    raise RuntimeError('Backup image does not match')
                images[service] = dict(identity, backupTag=tag)
            report['preservedImages'] = images
            report['backupDirectory'] = str(backup)
            validate_release(source, bundle)
            for name in FILES:
                saved = backup / name
                saved.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(bundle / name, saved)
            save_rollback_record(backup, release, images, private_snapshot)
            changed = True
            for name in FILES:
                shutil.copy2(source / name, bundle / name)
            command(compose + ['build', 'ai', 'backend'], 'build-services', 1800)
            require_launcher()
            command(compose + ['stop', 'edge', 'backend'], 'close-access-for-service-update', 150)
            report['backup'] = last_json(command(compose + ['exec', '-T', 'maintenance', 'python3', '-B', '-c', BACKUP],
                                                'verify-database-backup', 600))
            if report['backup'].get('backupVerified') is not True:
                raise RuntimeError('Database backup was not verified')
            for service in ('ai', 'backend'):
                require_launcher()
                command(compose + ['up', '-d', '--no-deps', '--force-recreate', '--wait', '--wait-timeout', '300', service],
                        'start-' + service, 350)
            command(compose + ['exec', '-T', 'ai', 'python', '/opt/demo/probe.py', 'ai'], 'ai-health', 60)
            for module in MODULES:
                command(compose + ['exec', '-T', 'backend', 'curl', '-fsS',
                                  'http://127.0.0.1:9800/admin/src/' + module + '.js'], 'verify-module-' + module, 30, False)
            validate_release(source, bundle, applied=True)
            if any(checksum(bundle / name) != digest for name, digest in private_snapshot.items()):
                raise RuntimeError('Private configuration or models changed')
            require_launcher()
            command(compose + ['up', '-d', '--no-deps', '--wait', '--wait-timeout', '90', 'edge'], 'reopen-same-address', 120)
            verify_public(origin)
            manifest = upgraded_manifest(manifest, release, backup.name)
            json_write(marker, manifest)
            report.update(passed=True, publicOrigin=origin)
            status('complete')
        except Exception as error:
            report.update(failedStep=report.get('step'), errorType=type(error).__name__)
            if changed:
                try:
                    restore_backup, record, _ = load_rollback_record(bundle, operation)
                    restore_services(bundle, restore_backup, record, compose, command, require_launcher)
                    report['rollbackPassed'] = True
                except Exception as rollback_error:
                    report['rollbackPassed'] = False
                    report['rollbackErrorType'] = type(rollback_error).__name__
                    try:
                        command(compose + ['stop', 'edge'], 'keep-failed-service-private', 90)
                    except Exception:
                        pass
            status('failed')
            raise


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print('Develop update stopped: ' + type(error).__name__ + '. Inspect develop-upgrade.json and private logs.', flush=True)
        raise SystemExit(1)
