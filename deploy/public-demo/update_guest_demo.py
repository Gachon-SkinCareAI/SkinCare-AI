"""Back up the existing demo, apply guest schema/API changes, then restart HTTPS.

Never creates/adopts missing data volumes, replaces models, or restores a database
over live data. After schema migration starts, failures leave the public service
stopped for inspection; the verified pre-migration backup is retained.
"""
import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys
import time

from prepare_bundle import protect
from run_free_demo import HIDDEN, HERE, AlreadyRunning, checksum, compose_command, instance_lock, json_write, start_verified_services
from update_analysis_speed import resolve_restore_image
from upgrade_free_demo import validate_existing_volumes

FILES = {'backend/app.jar', 'mysql/manage.py', 'mysql/retention.py',
         'migrations/V006__guest_analysis_location.sql', 'migrations/V007__local_module_compatibility.sql'}


def validate_release(source):
    data = json.loads((source / 'release.json').read_text(encoding='utf-8'))
    if data.get('format') != 'skincare-guest-public-v1' or set(data.get('files', {})) != FILES:
        raise RuntimeError('Unexpected release file set')
    if set(data.get('baseFiles', {})) != FILES:
        raise RuntimeError('Missing original file identities')
    for name, expected in data['files'].items():
        if checksum(source / name) != expected:
            raise RuntimeError('Release checksum mismatch: ' + name)
    return data


def verify_base(bundle, data):
    for name, expected in data['baseFiles'].items():
        path = bundle / name
        if (checksum(path) if path.exists() else None) != expected:
            raise RuntimeError('Existing deployment changed: ' + name)
    for name, expected in data['protectedFiles'].items():
        path = (bundle / name).resolve()
        if not path.is_relative_to(bundle.resolve()) or checksum(path) != expected:
            raise RuntimeError('Protected deployment file changed')


def last_json(text):
    for line in reversed(text.splitlines()):
        try:
            value = json.loads(line)
            if isinstance(value, dict):
                return value
        except ValueError:
            pass
    raise RuntimeError('No structured verification result')


BACKUP = """
import json
from db_runtime import ContainerInstance
from manage import digest
db=ContainerInstance();db.check_identity()
counts={t:int(db.sql('SELECT COUNT(*) FROM '+t+';', 'skincare_dev')) for t in ['users','analysis_sessions','analysis_answers','diagnoses']}
path,meta=db.backup('skincare_dev')
assert path.stat().st_size>0 and digest(path)==meta['sha256']
print(json.dumps({'backupVerified':True,'backupFile':path.name,'bytes':meta['bytes'],'sha256':meta['sha256'],'counts':counts}))
"""

SCHEMA_CHECK = """
import json
from db_runtime import ContainerInstance
db=ContainerInstance();db.check_identity()
rows=[r.split('\\t') for r in db.sql('SELECT version,status FROM schema_migrations ORDER BY version;', 'skincare_dev').splitlines()]
assert len(rows)==7 and all(r[1]=='APPLIED' for r in rows)
counts={t:int(db.sql('SELECT COUNT(*) FROM '+t+';', 'skincare_dev')) for t in ['users','analysis_sessions','analysis_answers','diagnoses']}
print(json.dumps({'migrations':[r[0] for r in rows],'counts':counts}))
"""


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config', required=True)
    parser.add_argument('--release', required=True)
    args = parser.parse_args()
    source = Path(args.release).resolve(); release = validate_release(source)
    config = json.loads(Path(args.config).read_text(encoding='utf-8-sig'))
    if config.get('format') != 'skincare-free-demo-v1':
        raise RuntimeError('Unexpected launcher config')
    root = Path(config['runtime']); out = Path(config['output']); bundle = root / 'bundle'
    marker = bundle / 'bundle-manifest.json'; manifest = json.loads(marker.read_text())
    compose = compose_command(bundle, manifest); project = manifest['project']
    if project != release['project']:
        raise RuntimeError('Release belongs to a different demo')
    operation = root / 'guest-upgrade'; operation.mkdir(exist_ok=True); protect(operation)
    report = {'release': release['id'], 'passed': False, 'modelChanged': False, 'volumesPreserved': True}
    def status(step):
        report['step'] = step; json_write(out / 'guest-upgrade.json', report); print(step, flush=True)
    def command(command, name, timeout=180, persist=True):
        status(name)
        result = subprocess.run(command, cwd=bundle, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                timeout=timeout, creationflags=HIDDEN)
        text = result.stdout.decode('utf-8', errors='replace')
        if persist:
            (operation / (name + '.log')).write_text(text, encoding='utf-8')
        if result.returncode:
            raise RuntimeError(name + ' failed; inspect the private log')
        return text
    applied = manifest.get('guestRelease') == release['id']
    if applied:
        if any(checksum(bundle / name) != expected for name, expected in release['files'].items()):
            raise RuntimeError('Previously verified guest release has changed')
    else:
        verify_base(bundle, release)
    command(['docker', 'version', '--format', '{{.Server.Version}}'], 'docker-engine', 30)
    volumes = json.loads(command(['docker', 'volume', 'inspect', project + '_database', project + '_maintenance'],
                                 'verify-existing-data-volumes', 30, persist=False))
    validate_existing_volumes(volumes, project)
    command([sys.executable, '-B', str(HERE / 'run_free_demo.py'), '--config', args.config, '--stop'],
            'stop-existing-demo-preserve-data', 150)
    deadline = time.monotonic() + 160
    while True:
        try:
            with instance_lock(root):
                break
        except AlreadyRunning:
            if time.monotonic() > deadline:
                raise RuntimeError('Existing launcher did not finish stopping')
            time.sleep(1)
    with instance_lock(root):
        # The old launcher releases this lock only after stopping its containers.
        # Holding it prevents a second launcher from publishing a half-migrated API.
        command(compose + ['stop'], 'ensure-private-maintenance-window', 150)
        if not applied:
            images = {}; backup = operation / ('before-' + str(time.time_ns()))
            backup.mkdir(); shutil.copy2(marker, backup / marker.name)
            changed = False; migration_started = False
            try:
                for service in ('backend', 'maintenance'):
                    container = json.loads(command(['docker', 'inspect', project + '-' + service + '-1'],
                                           'inspect-' + service, 30, persist=False))[0]
                    labels = container['Config'].get('Labels') or {}
                    if labels.get('com.docker.compose.project') != project or labels.get('com.docker.compose.service') != service:
                        raise RuntimeError('Unexpected service ownership')
                    identity = resolve_restore_image(container, project, service, command,
                                lambda value, s=service: json_write(operation / (s + '-identity.json'), value))
                    tag = project + '-' + service + ':before-guest-' + str(time.time_ns())
                    command(['docker', 'tag', identity['tag'], tag], 'preserve-' + service, 30)
                    saved = json.loads(command(['docker', 'image', 'inspect', tag], 'verify-' + service + '-backup', 30, persist=False))[0]
                    if saved['Id'] != identity['id']:
                        raise RuntimeError('Image backup identity differs')
                    images[service] = {'tag': identity['tag'], 'backupTag': tag}
                report['preservedImages'] = images
                for name in FILES:
                    target = bundle / name
                    if target.exists():
                        saved = backup / name; saved.parent.mkdir(parents=True, exist_ok=True); shutil.copy2(target, saved)
                changed = True
                for name in FILES:
                    target = bundle / name; target.parent.mkdir(parents=True, exist_ok=True); shutil.copy2(source / name, target)
                command(compose + ['build', 'backend', 'maintenance'], 'build-guest-services', 1800)
                command(compose + ['up', '-d', '--no-deps', '--wait', '--wait-timeout', '180', 'db'], 'start-existing-database', 220)
                result = command(compose + ['run', '--rm', '--no-deps', '-T', '--entrypoint', 'python3', 'maintenance',
                                            '-B', '-c', BACKUP], 'verify-pre-migration-backup', 600)
                report['backup'] = last_json(result)
                if report['backup'].get('backupVerified') is not True:
                    raise RuntimeError('A verified database backup is required')
                migration_started = True; report['migrationStarted'] = True
                command(compose + ['up', '-d', '--no-deps', '--force-recreate', '--wait', '--wait-timeout', '240', 'maintenance'],
                        'migrate-and-check-retention', 300)
                schema = last_json(command(compose + ['exec', '-T', 'maintenance', 'python3', '-B', '-c', SCHEMA_CHECK],
                                           'verify-seven-migrations', 60))
                report['schema'] = schema
                if schema['counts'] != report['backup']['counts']:
                    raise RuntimeError('Record totals changed during maintenance; inspect before publication')
                # Catch container-only startup errors before spending time warming AI.
                # HTTP startup and anonymous policy checks do not call the AI service.
                command(compose + ['up', '-d', '--no-deps', '--force-recreate', '--wait', '--wait-timeout', '180', 'backend'],
                        'verify-backend-startup', 230)
                policy = json.loads(command(compose + ['exec', '-T', 'backend', 'curl', '-fsS',
                                'http://127.0.0.1:9800/api/wc/auth/guest/policy'], 'verify-guest-api', 30))
                if policy.get('version') != '2026-11-04' or policy.get('trainingExportEnabled') is not False:
                    raise RuntimeError('Guest policy contract mismatch')
                admin = command(compose + ['exec', '-T', 'backend', 'curl', '-fsS',
                                'http://127.0.0.1:9800/admin/'], 'verify-admin-login', 30)
                if 'id="login-form"' not in admin:
                    raise RuntimeError('Operational administrator login is missing')
                start_verified_services(compose, manifest, command, out)
                for name, expected in release['protectedFiles'].items():
                    if checksum(bundle / name) != expected:
                        raise RuntimeError('Protected deployment file changed')
                manifest['guestRelease'] = release['id']; manifest['backendSha256'] = checksum(bundle / 'backend/app.jar')
                for name in FILES - {'backend/app.jar'}:
                    manifest['sources'][name] = checksum(bundle / name)
                json_write(marker, manifest)
                report['passed'] = True; status('guest-release-verified')
            except Exception as error:
                report['errorType'] = type(error).__name__
                report['failedStep'] = report.get('step')
                try:
                    command(compose + ['logs', '--no-color', '--tail', '100', 'backend', 'maintenance'], 'capture-failure', 30)
                except Exception:
                    pass
                command(compose + ['stop'], 'stop-failed-release', 150)
                if changed and not migration_started:
                    for name in FILES:
                        saved = backup / name; target = bundle / name
                        if saved.exists():
                            shutil.copy2(saved, target)
                        elif release['baseFiles'][name] is None and target.is_file() and checksum(target) == release['files'][name]:
                            target.unlink()  # Only a newly staged, checksum-verified release file.
                    for value in images.values():
                        command(['docker', 'tag', value['backupTag'], value['tag']], 'restore-original-image-tag', 30)
                    report['originalFilesRestored'] = True
                report['requiresSchemaReview'] = migration_started
                status('failed-public-service-stopped')
                raise
        command(compose + ['stop'], 'stop-private-check', 150)
    report['passed'] = True; status('ready-for-public-restart')
    print('Guest update verified. Starting HTTPS. Keep this window open.', flush=True)
    return subprocess.call([sys.executable, '-B', str(HERE / 'run_free_demo.py'), '--config', args.config])


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except Exception as error:
        print('Guest update stopped: ' + type(error).__name__ + '. Inspect the saved status and private logs.', flush=True)
        raise SystemExit(1)
