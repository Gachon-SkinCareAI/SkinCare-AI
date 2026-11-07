"""Update only AI and retention on the current public URL; preserve rollback."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import time

from develop_rollback import load_rollback_record
from public_web import validate_active, public_overlay, http_get
from publish_active_app import verify_snapshot
from run_free_demo import HERE, HIDDEN, checksum, compose_command, instance_lock, json_write
from upload_recovery_release import validate, validate_compose
from upload_recovery_check import check_isolated, capture_diagnostics


def read(path):
    return json.loads(Path(path).read_text(encoding='utf-8-sig'))


def apply(args, execute=subprocess.run, get=http_get):
    config = read(args.config)
    if config.get('format') != 'skincare-free-demo-v1':
        raise RuntimeError('Unexpected runtime')
    root = Path(config['runtime']).resolve()
    bundle = root/'bundle'
    out = Path(args.output).resolve()
    out.mkdir(parents=True, exist_ok=True)
    manifest = read(bundle/'bundle-manifest.json')
    source = Path(args.release).resolve()
    candidate = validate(source)
    validation_photo = getattr(args, 'validation_photo', None)
    if validation_photo:
        validation_photo = Path(validation_photo).resolve()
        with validation_photo.open('rb') as stream:
            validation_bytes = stream.read(8 * 1024 * 1024 + 1)
        if not 0 < len(validation_bytes) <= 8 * 1024 * 1024 or not validation_bytes.startswith(b'\xff\xd8'):
            raise RuntimeError('Validation photo must be a JPEG within the API size limit')
        photo_hash = hashlib.sha256(validation_bytes).hexdigest()
    status = read(args.public_status)
    origin = status['origin']
    report = {'passed': False, 'origin': origin, 'urlChanged': False, 'apkChanged': False,
              'databaseReset': False, 'modelWeightsChanged': False, 'rollbackPreserved': True}

    def step(name, **values):
        report.update(step=name, updatedAt=time.time(), **values)
        json_write(out/'recovery-status.json', report)
        print(name, flush=True)

    def current():
        status = read(args.public_status)
        marker = read(bundle/'public-web-active.json')
        if (not status.get('active') or not status.get('passed') or status.get('origin') != origin
            or marker.get('origin') != origin or marker.get('release') != receipt['release']
            or (root/'public-web-stop.request').exists() or (root/'stop.request').exists()):
            raise RuntimeError('Public server changed or is stopping; leave its restore process in control')

    def command(argv, name, timeout=180, persist=True, input_data=None):
        current()
        step(name)
        try:
            result = execute(argv, cwd=bundle, capture_output=True, timeout=timeout,
                             creationflags=HIDDEN, **({'input':input_data} if input_data is not None else {}))
        except subprocess.TimeoutExpired as error:
            if persist:
                (out/(name+'.log')).write_bytes((error.stdout or b'')+(error.stderr or b''))
            raise
        if persist:
            (out/(name+'.log')).write_bytes(result.stdout+result.stderr)
        if result.returncode:
            raise RuntimeError(name+' failed; see its log')
        return result.stdout.decode('utf-8', errors='replace')

    def identities(services=('db','llm','backend','edge')):
        raw = command(['docker', 'inspect']+[manifest['project']+'-'+s+'-1' for s in services],
                      'read-protected-service-identities', 30, False)
        return {x['Config']['Labels']['com.docker.compose.service']: x['Id'] for x in json.loads(raw)}

    if args.check_only:
        receipt = validate_active(bundle, manifest)
        current()
        verify_snapshot(bundle/'public-web'/receipt['release'], receipt)
        load_rollback_record(bundle, root/'develop-upgrade')
        step('preflight-complete', passed=True, checkOnly=True)
        return report
    operation = root/'public-upload-recovery'
    operation.mkdir(exist_ok=True)
    with instance_lock(operation):
        receipt = validate_active(bundle, manifest)
        folder = (bundle/'public-web'/receipt['release']).resolve()
        if not folder.is_relative_to((bundle/'public-web').resolve()):
            raise RuntimeError('Public release escaped its folder')
        current()
        original = verify_snapshot(folder, receipt)
        load_rollback_record(bundle, root/'develop-upgrade')
        target = folder/'upload-recovery'
        if target.exists():
            validate(target, allow_previous=True)
        compose = compose_command(bundle, manifest, allow_public_web=True)+['-f', str(folder/'compose.public-web.json')]
        protected = identities()
        baseline = json.loads(command(compose+['config','--format','json'], 'read-existing-compose', 30, False))
        initial_ids = identities(('db','llm','backend','edge','ai','maintenance'))
        ai_info = json.loads(command(['docker', 'inspect', manifest['project']+'-ai-1'],
                                     'read-existing-ai-image', 30, False))[0]
        if (ai_info['Config']['Labels'].get('com.docker.compose.project') != manifest['project']
                or ai_info['Config']['Labels'].get('com.docker.compose.service') != 'ai'):
            raise RuntimeError('Unexpected AI container identity')
        backup = operation/('before-'+str(time.time_ns()))
        backup.mkdir()
        for name in ('compose.public-web.json', 'release.json'):
            shutil.copy2(folder/name, backup/name)
        shutil.copy2(bundle/'public-web-active.json', backup/'public-web-active.json')
        report['backup'] = str(backup)
        changed = False
        added = not target.exists()
        previous_patch = backup/'upload-recovery'
        moved_previous = False
        # Directory moves are confined to this verified public release and backup.
        if not target.resolve().is_relative_to(folder) or not previous_patch.resolve().is_relative_to(backup):
            raise RuntimeError('Recovery package path escaped its verified parent')
        try:
            probe = (HERE/'vision_startup_probe.py').read_text(encoding='utf-8')
            question_probe = (HERE/'questionnaire_startup_probe.py').read_text(encoding='utf-8')
            check_isolated(baseline, source, ai_info['Image'], operation, manifest['project'],
                           bundle, out, execute, current, step, probe, validation_photo=validation_photo,
                           question_probe=question_probe)
            if identities(('db','llm','backend','edge','ai','maintenance')) != initial_ids:
                raise RuntimeError('Production services changed during isolated validation')
            if validate(source) != candidate:
                raise RuntimeError('Candidate changed during isolated validation')
            if validation_photo and checksum(validation_photo) != photo_hash:
                raise RuntimeError('Validation photo changed during isolated validation')
            report['isolatedPhotoCheckPassed'] = True
            changed = True
            if not added:
                target.rename(previous_patch)
                moved_previous = True
            shutil.copytree(source, target)
            current()
            json_write(folder/'compose.public-web.json', public_overlay(folder, origin))
            merged = json.loads(command(compose+['config','--format','json'], 'validate-recovery-compose', 30, False))
            validate_compose(baseline, merged)
            updated = dict(original)
            updated['files'] = {p.relative_to(folder).as_posix(): checksum(p)
                                for p in folder.rglob('*') if p.is_file() and p != folder/'release.json'}
            json_write(folder/'release.json', updated)
            receipt['releaseSha256'] = checksum(folder/'release.json')
            json_write(bundle/'public-web-active.json', receipt)
            verify_snapshot(folder, receipt)
            for service, timeout in (('maintenance', 180), ('ai', 180)):
                command(compose+['up','-d','--no-deps','--no-build','--pull','never','--force-recreate',
                                '--wait','--wait-timeout',str(timeout),service], 'apply-'+service, timeout+60)
            # The running container keeps a read-only root filesystem. Stream
            # the approved JPEG to the probe; do not use docker cp or add mounts.
            photo_args = ['--stdin-photo'] if validation_photo else []
            # Existing image may not contain this probe; execute the verified host source directly.
            for phase in ('first-photo-probe', 'after-idle-photo-probe'):
                if phase.startswith('after'):
                    step('checking-public-routes-during-idle')
                    for _ in range(13):
                        current()
                        if get(origin, '/api/wc/auth/social/providers')[0] != 200:
                            raise RuntimeError('Public HTTP route failed during maintenance interval')
                        time.sleep(5)
                raw = command(compose+['exec','-T','ai','python','-B','-c',probe], phase, 100)
                result = json.loads(raw.strip().splitlines()[-1])
                if result.get('passed') is not True:
                    raise RuntimeError('Live photo inference probe failed')
                report[phase] = result
                if photo_args:
                    raw = command(compose+['exec','-T','--interactive','ai','python','-B','-c',probe]+photo_args,
                                  phase+'-stored-photo', 100, input_data=validation_bytes)
                    result = json.loads(raw.strip().splitlines()[-1])
                    if result.get('passed') is not True or result.get('validationPhoto') is not True:
                        raise RuntimeError('Stored validation photo inference failed')
                    report[phase+'-stored-photo'] = result
            raw = command(compose+['exec','-T','ai','python','-B','-c',question_probe],
                          'check-live-questionnaire', 55)
            report['questionnaire-probe'] = json.loads(raw.strip().splitlines()[-1])
            if report['questionnaire-probe'].get('passed') is not True:
                raise RuntimeError('Live questionnaire probe failed')
            if identities() != protected:
                raise RuntimeError('Unrelated service restarted')
            for path, expected in (('/download/release.json',200),('/api/wc/analyses',401),('/api/v1/ai/health',404)):
                if get(origin, path)[0] != expected:
                    raise RuntimeError('Public route verification failed')
            load_rollback_record(bundle, root/'develop-upgrade')
            step('complete-upload-recovery', passed=True, liveSyntheticPhotosVerified=True,
                 liveStoredValidationPhotoVerified=bool(photo_args),
                 nativeUserPhotoVerified=False)
            return report
        except Exception as error:
            report['failedStep'] = getattr(error, 'step', report.get('step'))
            report['errorType'] = type(error).__name__
            report['error'] = str(error) if isinstance(error, RuntimeError) else 'Operation failed; see step log'
            if changed:
                capture_diagnostics(execute, bundle, compose, out, 'failed-production-ai')
                try:
                    current()
                    for name in ('compose.public-web.json', 'release.json'):
                        shutil.copy2(backup/name, folder/name)
                    shutil.copy2(backup/'public-web-active.json', bundle/'public-web-active.json')
                    if (added or moved_previous) and target.exists():
                        failed_patch = folder/('upload-recovery-failed-'+str(time.time_ns()))
                        if not failed_patch.resolve().is_relative_to(folder):
                            raise RuntimeError('Failed package path escaped public release')
                        target.rename(failed_patch)
                    if moved_previous:
                        previous_patch.rename(target)
                    verify_snapshot(folder, read(bundle/'public-web-active.json'))
                    command(compose+['up','-d','--no-deps','--no-build','--pull','never','--force-recreate',
                                    '--wait','--wait-timeout','600','maintenance','ai'], 'restore-prior-runtime', 660)
                    report['priorRuntimeRestored'] = True
                except Exception as rollback_error:
                    report['restoreErrorType'] = type(rollback_error).__name__
            step('failed', passed=False)
            raise


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    for key in ('config', 'public-status', 'release', 'output'):
        parser.add_argument('--'+key, required=True)
    parser.add_argument('--check-only', action='store_true')
    parser.add_argument('--validation-photo')
    apply(parser.parse_args())
