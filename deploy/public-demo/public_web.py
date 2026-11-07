"""Publish the current develop web app over a bounded, reversible HTTPS tunnel.

The existing base manifest, APK, images, data, model cache and startup overlay are
never rewritten. A separate overlay changes backend/edge and mounts the reviewed
questionnaire client. The AI process is prepared before the first public photo.
"""
import argparse
import ctypes
import hashlib
import http.client
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
import urllib.error
import urllib.request

from develop_rollback import load_rollback_record
from local_startup_readiness import verify_local_readiness
from prepare_local_model import prepare_local_model
from run_free_demo import (HERE, HIDDEN, StopRequested, checksum, compose_command,
                           instance_lock, json_write, tunnel_origin, verify_public_endpoints)
from startup_stability_release import active_overlay
from upload_recovery_release import extend as extend_upload_recovery, package as package_upload_recovery

FORMAT = 'skincare-public-web-active-v1'
BASE_FILES = ('bundle-manifest.json', '.env', 'startup-stability.json')


def source_hashes(mobile):
    files = {}
    for directory, folders, names in os.walk(mobile):
        folders[:] = sorted(f for f in folders if f not in
                           {'node_modules', 'android', 'ios', '.git', '.expo', 'dist', 'build', '__pycache__'}
                           and not f.startswith('dist-'))
        for name in sorted(names):
            if name.startswith('.env') and name != '.env.example':
                continue
            path = Path(directory) / name
            files[path.relative_to(mobile).as_posix()] = checksum(path)
    if not {'src/AppShell.js', 'src/services/client.js', 'package-lock.json'}.issubset(files):
        raise RuntimeError('The develop mobile source is incomplete')
    return files


def public_overlay(folder, origin):
    if not re.fullmatch(r'https://[a-z0-9]+(?:-[a-z0-9]+)*\.trycloudflare\.com', origin):
        raise RuntimeError('Unexpected temporary HTTPS origin')
    folder = Path(folder).resolve()
    return extend_upload_recovery({'services': {
        'ai': {'volumes': [
            {'type': 'bind', 'source': str(folder / 'llm_client.py'), 'target': '/app/qa/llm_client.py',
             'read_only': True, 'bind': {'create_host_path': False}}]},
        'backend': {'environment': {'SOCIAL_PUBLIC_BASE_URL': origin,
                                    'SKINCARE_WEB_ALLOWED_ORIGINS': origin}},
        'edge': {'environment': {'PUBLIC_HOST': origin[8:]}, 'volumes': [
            {'type': 'bind', 'source': str(folder / 'Caddyfile'), 'target': '/etc/caddy/Caddyfile',
             'read_only': True, 'bind': {'create_host_path': False}},
            {'type': 'bind', 'source': str(folder / 'web'), 'target': '/srv/public-web',
             'read_only': True, 'bind': {'create_host_path': False}}]}}}, folder)


def validate_active(bundle, manifest):
    data = json.loads((bundle / 'public-web-active.json').read_text(encoding='utf-8'))
    if (data.get('format') != FORMAT or data.get('project') != manifest['project']
            or not re.fullmatch(r'web-[0-9]+', data.get('release', ''))
            or set(data.get('baseFiles', {})) != set(BASE_FILES)):
        raise RuntimeError('Unexpected public web recovery record')
    for name, digest in data['baseFiles'].items():
        if checksum(bundle / name) != digest:
            raise RuntimeError('Base server changed; public web recovery stopped')
    active_overlay(bundle, manifest)
    return data


def http_get(origin, path, headers=None):
    request = urllib.request.Request(origin + path, headers=headers or {})
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            return response.status, response.read()
    except urllib.error.HTTPError as error:
        return error.code, error.read()
    except http.client.HTTPException as error:
        raise OSError(type(error).__name__) from error


def prepare_public_models(compose, manifest, run, out):
    # A healthy but long-idle CPU process took 89 seconds on the first real photo.
    # Re-enter its existing lifespan after the web build: this warms quality,
    # detector AND classifier on the serving thread, before edge is opened.
    run(compose + ['up', '-d', '--no-deps', '--no-build', '--pull', 'never',
                   '--force-recreate', '--wait', '--wait-timeout', '600', 'ai'],
        'prepare-public-vision-process', 660)
    raw = run(compose + ['logs', '--no-color', '--tail', '200', 'ai'],
              'check-public-vision-warmup', 30)
    warmed = []
    for line in raw.splitlines():
        if '{' not in line:
            continue
        try:
            item = json.loads(line[line.index('{'):])
        except ValueError:
            continue
        if (item.get('event') == 'vision_warmup' and item.get('synthetic_input_only') is True
                and all(isinstance(item.get(k), (int, float)) and item[k] > 0
                        for k in ('detector_ms', 'classifier_ms', 'total_ms'))):
            warmed.append({key: item[key] for key in
                           ('event', 'device', 'detector_ms', 'classifier_ms', 'total_ms')})
    if not warmed:
        raise RuntimeError('Full serving-process detector/classifier warmup was not verified')
    result = {'passed': False, 'aiProcessRecreated': True, 'modelFilesChanged': False,
              'patientRecordsCreated': False, 'syntheticInputOnly': True, 'warmup': warmed[-1]}
    if manifest.get('localLlmEnabled'):
        result['questionnaire'] = prepare_local_model(compose, run, HERE / 'local_llm_smoke.py')
        if result['questionnaire'].get('modelOptions', {}).get('use_mmap') is not True:
            raise RuntimeError('Public questionnaire runtime did not use the reviewed loading options')
    raw = run(compose + ['exec', '-T', 'ai', 'python', '-B', '-c',
                        (HERE / 'vision_startup_probe.py').read_text(encoding='utf-8')],
              'check-public-vision-http-speed', 120)
    result['vision'] = json.loads(raw.strip().splitlines()[-1])
    checks = result['vision'].get('checks', [])
    if (result['vision'].get('passed') is not True or len(checks) != 2
            or any(c.get('status') != 200 or c.get('qualityPassed') is not True
                   or not isinstance(c.get('seconds'), (int, float))
                   or not 0 <= c['seconds'] <= 30 for c in checks)):
        raise RuntimeError('Public vision processing is not ready within its first-upload budget')
    result['passed'] = True
    json_write(Path(out) / 'public-model-readiness.json', result)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config', required=True)
    parser.add_argument('--output', required=True)
    parser.add_argument('--restore', action='store_true')
    args = parser.parse_args()
    config = json.loads(Path(args.config).read_text(encoding='utf-8-sig'))
    if config.get('format') != 'skincare-free-demo-v1' or os.name != 'nt':
        raise RuntimeError('Use the existing Windows demo configuration')
    root = Path(config['runtime']).resolve()
    bundle = root / 'bundle'
    out = Path(args.output).resolve()
    out.mkdir(parents=True, exist_ok=True)
    logs = out / 'private-logs'
    logs.mkdir(exist_ok=True)
    marker = bundle / 'public-web-active.json'
    stop = root / 'public-web-stop.request'
    legacy_stop = root / 'stop.request'
    manifest = json.loads((bundle / 'bundle-manifest.json').read_text(encoding='utf-8'))
    state = {'passed': False, 'active': False, 'temporary': True,
             'sourceRoot': str(HERE.parent.parent / 'mobile'), 'databaseChanged': False,
             'modelWeightsChanged': False, 'physicalPhoneVerified': False}
    tunnel = None
    job = None
    awake = False
    changed = False
    base = None

    def status(step, **extra):
        state.update(step=step, updatedAt=time.time(), **extra)
        json_write(out / 'public-status.json', state)
        print(step, flush=True)

    def command(argv, name, timeout=180, cwd=None, env=None):
        status(name)
        with (logs / (name + '.log')).open('wb') as log:
            result = subprocess.run(list(map(str, argv)), cwd=cwd or bundle, env=env,
                                    stdin=subprocess.DEVNULL, stdout=log, stderr=subprocess.STDOUT,
                                    timeout=timeout, creationflags=HIDDEN)
        if result.returncode:
            raise RuntimeError(name + ' failed; see private-logs/' + name + '.log')
        return (logs / (name + '.log')).read_text(encoding='utf-8', errors='replace')

    def check_stop():
        if stop.exists() or legacy_stop.exists():
            raise StopRequested()
        if tunnel is not None and tunnel.poll() is not None:
            raise RuntimeError('Public tunnel disconnected; local services will be restored')

    def restore():
        nonlocal changed
        validate_active(bundle, manifest)
        command(base + ['stop', 'edge'], 'close-public-edge', 120)
        if state.get('aiRestartRequested'):
            command(base + ['up', '-d', '--no-deps', '--no-build', '--pull', 'never',
                            '--wait', '--wait-timeout', '600', 'ai'], 'verify-local-vision-restored', 660)
        command(base + ['up', '-d', '--no-deps', '--no-build', '--pull', 'never',
                        '--force-recreate', '--wait', '--wait-timeout', '180', 'backend'], 'restore-local-backend', 210)
        command(base + ['up', '-d', '--no-deps', '--no-build', '--pull', 'never',
                        '--force-recreate', '--wait', '--wait-timeout', '120', 'edge'], 'restore-local-edge', 150)
        verify_local_readiness(lambda path: http_get('http://127.0.0.1:20880', path)[0],
                               lambda value: json_write(out / 'restore-http-checks.json', value),
                               cycles=2, interval=2, timeout=120)
        load_rollback_record(bundle, root / 'develop-upgrade')
        marker.replace(out / ('restored-' + str(time.time_ns()) + '.json'))
        changed = False
        state['localRestored'] = True

    with instance_lock(root), instance_lock(root / 'develop-upgrade'):
        try:
            load_rollback_record(bundle, root / 'develop-upgrade')
            active_overlay(bundle, manifest)
            base = compose_command(bundle, manifest, allow_public_web=True)
            command(['docker', 'version', '--format', '{{.Server.Version}}'], 'docker-engine', 30)
            if marker.exists():
                changed = True
                restore()
            if args.restore:
                verify_local_readiness(lambda path: http_get('http://127.0.0.1:20880', path)[0],
                                       lambda value: json_write(out / 'restore-http-checks.json', value),
                                       cycles=2, interval=2, timeout=120)
                state['localRestored'] = True
                status('complete-local-restored', active=False, passed=True)
                return
            # Never reuse a stale shutdown signal from a previous completed run.
            for flag in (stop, legacy_stop):
                if flag.exists():
                    flag.unlink()
            for name in ('config.yaml', 'config.yml'):
                if (Path.home() / '.cloudflared' / name).exists():
                    raise RuntimeError('Existing cloudflared configuration requires review')
            binary = Path(config['cloudflared'])
            if checksum(binary) != config['cloudflaredSha256']:
                raise RuntimeError('Tunnel binary checksum mismatch')
            mobile = HERE.parent.parent / 'mobile'
            before = source_hashes(mobile)
            status('verify-existing-local-server')
            verify_local_readiness(lambda path: http_get('http://127.0.0.1:20880', path)[0],
                                   lambda value: json_write(out / 'before-http-checks.json', value),
                                   cycles=2, interval=2, timeout=120)
            folder = bundle / 'public-web' / ('web-' + str(time.time_ns()))
            folder.mkdir(parents=True)
            receipt = {'format': FORMAT, 'project': manifest['project'], 'release': folder.name,
                       'baseFiles': {name: checksum(bundle / name) for name in BASE_FILES}}
            json_write(marker, receipt)
            changed = True
            command(base + ['stop', 'edge'], 'close-edge-before-new-tunnel', 120)
            env = os.environ.copy()
            for key in tuple(env):
                if key.startswith(('TUNNEL_', 'CLOUDFLARE_', 'CLOUDFLARED_')):
                    env.pop(key, None)
            from windows_tunnel_job import TunnelJob
            job = TunnelJob()
            status('requesting-public-https')
            tunnel_log = logs / 'tunnel.log'
            with tunnel_log.open('wb') as log:
                tunnel = subprocess.Popen([str(binary), 'tunnel', '--no-autoupdate', '--url',
                    'http://127.0.0.1:20880', '--metrics', '127.0.0.1:0'], cwd=root, env=env,
                    stdin=subprocess.DEVNULL, stdout=log, stderr=subprocess.STDOUT, creationflags=HIDDEN)
            job.attach(tunnel)
            deadline = time.monotonic() + 90
            origin = None
            while origin is None and time.monotonic() < deadline:
                check_stop()
                origin = tunnel_origin(tunnel_log.read_text(encoding='utf-8', errors='replace'))
                time.sleep(.5)
            if origin is None:
                raise RuntimeError('No HTTPS address was issued; see private-logs/tunnel.log')
            state['origin'] = origin
            command(['powershell.exe', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File',
                     HERE / 'Build-Public-Web.ps1', '-ApiOrigin', origin,
                     '-OutputDirectory', folder / 'web', '-MobileDirectory', mobile],
                    'build-develop-web', 600, cwd=mobile)
            if source_hashes(mobile) != before:
                raise RuntimeError('Mobile source changed during export')
            js_files = list((folder / 'web' / '_expo').rglob('*.js'))
            if not js_files or not any(origin.encode() in path.read_bytes() for path in js_files):
                raise RuntimeError('Exported frontend does not identify the new API origin')
            if not (folder / 'web' / 'index.html').is_file():
                raise RuntimeError('Exported index is missing')
            import qrcode
            qrcode.make(origin + '/').save(folder / 'web' / 'phone-qr.png')
            import cv2
            decoded, _, _ = cv2.QRCodeDetector().detectAndDecode(cv2.imread(str(folder / 'web' / 'phone-qr.png')))
            if decoded != origin + '/':
                raise RuntimeError('Generated QR could not be decoded to the current origin')
            shutil.copy2(HERE / 'Caddyfile.public-web', folder / 'Caddyfile')
            shutil.copy2(HERE.parent.parent / 'ai' / 'qa' / 'llm_client.py', folder / 'llm_client.py')
            package_upload_recovery(HERE.parent.parent, folder / 'upload-recovery')
            overlay = folder / 'compose.public-web.json'
            json_write(overlay, public_overlay(folder, origin))
            files = {path.relative_to(folder).as_posix(): checksum(path)
                     for path in folder.rglob('*') if path.is_file()}
            json_write(folder / 'release.json', {'origin': origin, 'files': files, 'mobileSources': before})
            receipt.update(origin=origin, releaseSha256=checksum(folder / 'release.json'))
            json_write(marker, receipt)
            compose = base + ['-f', str(overlay)]
            parsed = json.loads(command(compose + ['config', '--format', 'json'], 'validate-compose', 30))
            for name, service in parsed['services'].items():
                ports = service.get('ports', [])
                if name == 'edge':
                    if len(ports) != 1 or ports[0]['host_ip'] != '127.0.0.1' or str(ports[0]['published']) != '20880':
                        raise RuntimeError('Unexpected edge exposure')
                elif ports:
                    raise RuntimeError('Private service unexpectedly exposes a host port')
            command(compose + ['run', '--rm', '--no-deps', 'edge', 'caddy', 'validate',
                               '--config', '/etc/caddy/Caddyfile'], 'validate-caddy', 120)
            state['aiRestartRequested'] = True
            prepare_public_models(compose, manifest, command, out)
            state['modelsPreparedForPublicPhotos'] = True
            command(compose + ['up', '-d', '--no-deps', '--no-build', '--pull', 'never',
                               '--force-recreate', '--wait', '--wait-timeout', '180', 'backend'], 'connect-public-backend', 210)
            command(compose + ['up', '-d', '--no-deps', '--no-build', '--pull', 'never',
                               '--force-recreate', '--wait', '--wait-timeout', '120', 'edge'], 'open-public-web', 150)
            status('check-public-api-and-private-routes')
            verify_public_endpoints(lambda path, public=False: http_get(origin if public else 'http://127.0.0.1:20880', path),
                                    check_stop, lambda value: json_write(out / 'endpoint-checks.json', value), timeout=180)
            verify_local_readiness(lambda path: http_get(origin, path)[0],
                                   lambda value: json_write(out / 'public-stability-checks.json', value),
                                   cycles=16, interval=5, timeout=240)
            for path, expected in (('/', 200), ('/phone-qr.png', 200), ('/admin/', 200),
                                   ('/.env', 404), ('/download/SkinCare-AI.apk', 404)):
                code, body = http_get(origin, path)
                if code != expected:
                    raise RuntimeError('Public route contract failed: ' + path)
                if path == '/' and b'SkinCare AI' not in body:
                    raise RuntimeError('Public frontend index differs')
                if path == '/phone-qr.png' and hashlib.sha256(body).hexdigest() != checksum(folder / 'web' / 'phone-qr.png'):
                    raise RuntimeError('Public QR differs from the current release')
            allowed = urllib.request.Request(origin + '/api/wc/analyses', method='OPTIONS', headers={
                'Origin': origin, 'Access-Control-Request-Method': 'POST',
                'Access-Control-Request-Headers': 'authorization,content-type'})
            with urllib.request.urlopen(allowed, timeout=20) as response:
                # A same-origin OPTIONS request need not emit CORS headers.
                if response.headers.get('Access-Control-Allow-Origin') not in (None, origin):
                    raise RuntimeError('Public browser CORS origin differs')
            denied = http_get(origin, '/api/wc/auth/guest/policy', {'Origin': 'https://untrusted.invalid'})[0]
            if denied != 403:
                raise RuntimeError('Untrusted browser origin was not rejected')
            load_rollback_record(bundle, root / 'develop-upgrade')
            validate_active(bundle, manifest)
            shutil.copy2(folder / 'web' / 'phone-qr.png', out / 'phone-qr.png')
            json_write(out / 'source-provenance.json', {'sourceRoot': str(mobile), 'files': before,
                                                       'sourceUnchanged': True, 'origin': origin})
            (out / 'OPEN-ON-PHONE.txt').write_text(origin + '/\n\n안드로이드 카메라로 phone-qr.png를 스캔하고 브라우저에서 여세요.\n'
                '이 주소는 PC, Docker, 공개 실행 창이 켜진 동안 유효합니다. 재시작하면 새 QR을 사용하세요.\n'
                '기존 APK는 이전 주소용입니다. 이번 확인은 브라우저에서 진행합니다.\n', encoding='utf-8')
            awake = bool(ctypes.windll.kernel32.SetThreadExecutionState(0x80000001))
            status('complete-public-ready', passed=True, active=True, qr=str(out / 'phone-qr.png'),
                   browserE2EVerified=False, corsVerified=True, rollbackPreserved=True)
            print('Public phone URL: ' + origin + '/', flush=True)
            print('Keep this window and Docker running. Use STOP-PUBLIC-PHONE.cmd to restore local mode.', flush=True)
            while True:
                check_stop()
                time.sleep(1)
        except (KeyboardInterrupt, StopRequested):
            state['stopRequested'] = True
        except Exception as error:
            status('failed', passed=False, error=str(error))
            # Capture health-check reasons and numeric timings before restoration.
            # No extra host action is needed to diagnose a failed preparation.
            try:
                from diagnose_public_models import capture
                capture(args.config, out)
            except Exception as diagnostic_error:
                state['diagnosticErrorType'] = type(diagnostic_error).__name__
        finally:
            if tunnel is not None and tunnel.poll() is None:
                tunnel.terminate()
                try:
                    tunnel.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    tunnel.kill()
                    tunnel.wait(timeout=5)
            if job is not None:
                job.close()
            state['active'] = False
            if changed and base is not None:
                try:
                    restore()
                except Exception as error:
                    state.update(passed=False, restoreError=str(error), localRestored=False)
                    try:
                        command(base + ['stop', 'edge'], 'keep-failed-restore-private', 90)
                    except Exception:
                        pass
            if awake:
                ctypes.windll.kernel32.SetThreadExecutionState(0x80000000)
            # Never leave a stopped tunnel's QR advertised as the current link.
            qr = out / 'phone-qr.png'
            if qr.exists():
                qr.replace(out / ('inactive-phone-qr-' + str(time.time_ns()) + '.png'))
            (out / 'OPEN-ON-PHONE.txt').write_text(
                '현재 공개 연결은 종료됐습니다. START-PUBLIC-PHONE.cmd 실행 후 complete-public-ready와 새 QR을 확인하세요.\n',
                encoding='utf-8')
            status('stopped' if state.get('localRestored') else 'failed', active=False)
    return 0 if state.get('stopRequested') and state.get('localRestored') else 1


if __name__ == '__main__':
    sys.exit(main())
