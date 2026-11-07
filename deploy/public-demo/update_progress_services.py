"""Add capture metadata to the existing demo, preserving the tunnel and baseline AI.

After migration begins a failure closes public access; no automatic DB restore.
Run with the existing launcher open, or with --stopped after it has been closed.
"""
import argparse
from contextlib import nullcontext
import json
from pathlib import Path
import shutil
import subprocess
import time
from prepare_bundle import protect
from run_free_demo import HIDDEN, HERE, checksum, compose_command, instance_lock, json_write
from update_analysis_speed import resolve_restore_image
from update_guest_demo import BACKUP, last_json
from upgrade_free_demo import validate_existing_volumes

FILES = {'backend/app.jar', 'migrations/V008__capture_date_and_symptoms.sql'}

def validate_release(source, bundle):
    data = json.loads((source / 'release.json').read_text(encoding='utf-8'))
    if data.get('format') != 'skincare-progress-services-v1' or set(data.get('files', {})) != FILES or set(data.get('baseFiles', {})) != FILES:
        raise RuntimeError('Unexpected release allowlist')
    for name, expected in data['files'].items():
        if checksum(source / name) != expected:
            raise RuntimeError('Candidate checksum mismatch')
    for name, expected in data['protectedFiles'].items():
        path = (bundle / name).resolve()
        if not path.is_relative_to(bundle.resolve()) or checksum(path) != expected:
            raise RuntimeError('Protected deployment file changed')
    return data

def verify_base(bundle, release):
    for name, expected in release['baseFiles'].items():
        path = bundle / name
        if (checksum(path) if path.exists() else None) != expected:
            raise RuntimeError('Deployment changed since packaging')

SCHEMA = """
import json
from db_runtime import ContainerInstance
db=ContainerInstance();db.check_identity()
rows=[r.split('\\t') for r in db.sql('SELECT version,status FROM schema_migrations ORDER BY version;', 'skincare_dev').splitlines()]
assert len(rows)==8 and all(r[1]=='APPLIED' for r in rows)
counts={t:int(db.sql('SELECT COUNT(*) FROM '+t+';', 'skincare_dev')) for t in ['users','analysis_sessions','analysis_answers','diagnoses']}
columns=db.sql("SELECT column_name FROM information_schema.columns WHERE table_schema='skincare_dev' AND table_name='analysis_sessions' AND column_name IN ('captured_on','swelling','exudate');").splitlines()
assert set(columns)=={'captured_on','swelling','exudate'}
print(json.dumps({'migrations':len(rows),'counts':counts,'newColumns':columns}))
"""

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config', required=True); parser.add_argument('--release', required=True)
    parser.add_argument('--stopped', action='store_true')
    args = parser.parse_args()
    config = json.loads(Path(args.config).read_text(encoding='utf-8-sig'))
    root = Path(config['runtime']); out = Path(config['output']); bundle = root / 'bundle'
    source = Path(args.release); release = validate_release(source, bundle)
    marker = bundle / 'bundle-manifest.json'; manifest = json.loads(marker.read_text())
    project = manifest['project']; compose = compose_command(bundle, manifest)
    if release['project'] != project: raise RuntimeError('Wrong project')
    report = {'passed': False, 'databaseRestoreAttempted': False, 'modelsChanged': False, 'tunnelRestarted': False}
    operation = root / 'progress-upgrade'; operation.mkdir(exist_ok=True); protect(operation)
    def status(step):
        report['step'] = step; json_write(out / 'progress-upgrade.json', report); print(step, flush=True)
    def command(args, name, timeout=180, persist=True):
        status(name)
        result = subprocess.run(args, cwd=bundle, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=timeout, creationflags=HIDDEN)
        output = result.stdout.decode('utf-8', errors='replace')
        if persist: (operation / (name + '.log')).write_text(output, encoding='utf-8')
        if result.returncode: raise RuntimeError(name + ' failed')
        return output
    connection = json.loads((out / 'public-connection.json').read_text())
    origin = connection.get('origin')
    def require_launcher():
        if args.stopped: return
        current = json.loads((out / 'public-connection.json').read_text())
        if not current.get('active') or current.get('origin') != origin or (root / 'stop.request').exists():
            raise RuntimeError('Public launcher was stopped or changed')
    with instance_lock(operation), (instance_lock(root) if args.stopped else nullcontext()):
        command(['docker','version','--format','{{.Server.Version}}'], 'docker-engine', 30)
        volumes = json.loads(command(['docker','volume','inspect', project+'_database', project+'_maintenance'], 'verify-existing-data-volumes', 30, False))
        validate_existing_volumes(volumes, project)
        applied = manifest.get('progressRelease') == release['id']
        if applied:
            if any(checksum(bundle/n) != h for n,h in release['files'].items()): raise RuntimeError('Applied release changed')
            report.update(passed=True, alreadyApplied=True); status('complete'); return
        verify_base(bundle, release); require_launcher()
        backup = operation / ('before-' + str(time.time_ns())); backup.mkdir(); images = {}; changed = False; migration = False
        shutil.copy2(marker, backup / marker.name)
        try:
            for service in ('backend','maintenance'):
                info = json.loads(command(['docker','inspect',project+'-'+service+'-1'], 'inspect-'+service, 30, False))[0]
                labels = info['Config'].get('Labels') or {}
                if labels.get('com.docker.compose.project') != project or labels.get('com.docker.compose.service') != service:
                    raise RuntimeError('Wrong service owner')
                image = resolve_restore_image(info, project, service, command, lambda value: None)
                tag = project+'-'+service+':before-progress-'+str(time.time_ns())
                command(['docker','tag',image['tag'],tag], 'preserve-'+service, 30)
                saved = json.loads(command(['docker','image','inspect',tag], 'verify-'+service+'-backup', 30, False))[0]
                if saved['Id'] != image['id']: raise RuntimeError('Image backup differs')
                images[service] = {'tag':image['tag'], 'backupTag':tag}
            report['preservedImages'] = images
            for name in FILES:
                target = bundle/name
                if target.exists():
                    old = backup/name; old.parent.mkdir(parents=True, exist_ok=True); shutil.copy2(target,old)
            changed = True
            for name in FILES:
                target=bundle/name; target.parent.mkdir(parents=True,exist_ok=True); shutil.copy2(source/name,target)
            command(compose+['build','backend','maintenance'], 'build-services', 1800)
            require_launcher()
            command(compose+['up','-d','--no-deps','--wait','--wait-timeout','180','db'], 'existing-database-ready', 220)
            report['migrationTest'] = last_json(command(compose+['run','--rm','--no-deps','-T','--entrypoint','python3','maintenance','-B','-c',(HERE/'progress_db_smoke.py').read_text()], 'isolated-mysql-upgrade-test', 300))
            if report['migrationTest'].get('passed') is not True: raise RuntimeError('Migration QA failed')
            command(compose+['stop','edge','backend','maintenance'], 'close-public-access-for-migration', 150)
            report['backup'] = last_json(command(compose+['run','--rm','--no-deps','-T','--entrypoint','python3','maintenance','-B','-c',BACKUP], 'verified-database-backup', 600))
            if report['backup'].get('backupVerified') is not True: raise RuntimeError('Backup unverified')
            migration = True; report['migrationStarted'] = True
            command(compose+['up','-d','--no-deps','--force-recreate','--wait','--wait-timeout','240','maintenance'], 'apply-eight-migrations', 300)
            report['schema'] = last_json(command(compose+['exec','-T','maintenance','python3','-B','-c',SCHEMA], 'verify-schema-and-record-counts', 60))
            if report['schema']['counts'] != report['backup']['counts']: raise RuntimeError('Record counts changed; inspect before publishing')
            require_launcher()
            command(compose+['up','-d','--no-deps','--force-recreate','--wait','--wait-timeout','180','backend'], 'verify-backend-startup', 230)
            policy = json.loads(command(compose+['exec','-T','backend','curl','-fsS','http://127.0.0.1:9800/api/wc/auth/guest/policy'], 'guest-api-regression', 30))
            if policy.get('version') != '2026-11-04' or policy.get('trainingExportEnabled') is not False: raise RuntimeError('Guest API contract changed')
            admin = command(compose+['exec','-T','backend','curl','-fsS','http://127.0.0.1:9800/admin/'], 'admin-web-regression', 30)
            if 'id="login-form"' not in admin: raise RuntimeError('Admin missing')
            validate_release(source,bundle)
            manifest['progressRelease']=release['id']; manifest['backendSha256']=checksum(bundle/'backend/app.jar')
            manifest['sources'].update(release['files']); json_write(marker,manifest)
            if not args.stopped:
                require_launcher(); command(compose+['up','-d','--no-deps','--wait','--wait-timeout','60','edge'], 'reopen-same-public-address', 90)
            report.update(passed=True, backupDirectory=str(backup), publicOrigin=origin if not args.stopped else None)
            status('complete')
        except Exception as error:
            report.update(failedStep=report.get('step'),errorType=type(error).__name__,requiresSchemaReview=migration)
            try: command(compose+['logs','--no-color','--tail','100','backend','maintenance'], 'capture-failure', 30)
            except Exception: pass
            if migration:
                command(compose+['stop','edge','backend','maintenance'], 'close-failed-release', 150)
            elif changed:
                for name in FILES:
                    old=backup/name; target=bundle/name
                    if old.exists(): shutil.copy2(old,target)
                    elif target.exists() and checksum(target)==release['files'][name]: target.unlink()
                for item in images.values(): command(['docker','tag',item['backupTag'],item['tag']], 'restore-previous-image',30)
                report['sourceRestored']=True
            status('failed'); raise

if __name__ == '__main__':
    try: main()
    except Exception as error:
        print('Service update stopped: '+type(error).__name__+'. Inspect saved private status/logs. No database restore is automatic.',flush=True)
        raise SystemExit(1)
