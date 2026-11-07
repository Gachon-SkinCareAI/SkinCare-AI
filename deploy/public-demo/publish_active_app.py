"""Publish a signed native APK on the active temporary server without changing its URL.

Only the current public release's static assets and edge are updated. The base
bundle, database, AI/LLM processes, model volume and rollback identity stay intact.
"""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import time
import urllib.error

from apk_distribution import PUBLIC_FILES
from develop_rollback import load_rollback_record
from public_web import validate_active
from publish_apk import read_json, ready_response, validate
from publish_existing_install import verify_distribution, atomic_copy
from run_free_demo import HERE, HIDDEN, checksum, compose_command, instance_lock, json_write


def verify_snapshot(folder, receipt):
    if checksum(folder/'release.json') != receipt.get('releaseSha256'):
        raise RuntimeError('Active public release receipt differs')
    release = read_json(folder/'release.json')
    for relative, digest in release['files'].items():
        path = (folder/relative).resolve()
        if not path.is_relative_to(folder.resolve()) or path.is_symlink() or not path.is_file() or checksum(path) != digest:
            raise RuntimeError('Active public release file differs: '+relative)
    return release


def check_install(origin, release, qr_sha256):
    verify_distribution(origin, release)
    with ready_response(origin+'/download/install-qr.png') as response:
        if hashlib.sha256(response.read()).hexdigest() != qr_sha256:
            raise RuntimeError('Public install QR differs from the verified QR')
    for suffix in ('private/application-secrets.properties', 'backend/app.jar', '.env', 'not-published.txt'):
        try:
            ready_response(origin+'/download/'+suffix, timeout=15)
        except urllib.error.HTTPError as error:
            if error.code != 404:
                raise
        else:
            raise RuntimeError('Unpublished file is accessible')


def publish(args, public_check=check_install, execute=subprocess.run):
    config = read_json(Path(args.config))
    if config.get('format') != 'skincare-free-demo-v1':
        raise RuntimeError('Unexpected runtime configuration')
    root = Path(config['runtime']).resolve()
    bundle = root/'bundle'
    out = Path(args.output).resolve()
    out.mkdir(parents=True, exist_ok=True)
    status_path = Path(args.public_status).resolve()
    status = read_json(status_path)
    origin = status.get('origin')
    source = Path(args.assets).resolve()
    release = validate(source, origin, Path(args.verification))
    # Signatures are already verified against the previous installed package by
    # verify_public_apk.py. Validate the installation QR before any publication.
    import cv2
    decoded, _, _ = cv2.QRCodeDetector().detectAndDecode(cv2.imread(str(source/'install-qr.png')))
    if decoded != origin+'/download/':
        raise RuntimeError('Install QR points to another destination')
    qr_sha256 = checksum(source/'install-qr.png')
    manifest = read_json(bundle/'bundle-manifest.json')
    report = {'passed':False, 'origin':origin, 'versionCode':release['versionCode'],
              'databaseChanged':False, 'modelFilesChanged':False, 'aiRestarted':False,
              'llmRestarted':False, 'tunnelRestarted':False, 'nativeDeviceVerified':False}
    operation = root/'public-app-publication'
    operation.mkdir(exist_ok=True)

    def step(name, **extra):
        report.update(step=name, updatedAt=time.time(), **extra)
        json_write(out/'publication.json', report)
        print(name, flush=True)

    with instance_lock(operation):
        receipt = validate_active(bundle, manifest)
        folder = bundle/'public-web'/receipt['release']
        if not folder.resolve().is_relative_to(bundle.resolve()):
            raise RuntimeError('Public release escapes the runtime bundle')
        prior_release = verify_snapshot(folder, receipt)
        if receipt.get('origin') != origin or prior_release.get('origin') != origin:
            raise RuntimeError('Public release origin differs')
        def current():
            latest = read_json(status_path)
            active = read_json(bundle/'public-web-active.json')
            if (not latest.get('active') or not latest.get('passed') or latest.get('origin') != origin
                    or active.get('release') != receipt['release'] or active.get('origin') != origin
                    or (root/'public-web-stop.request').exists() or (root/'stop.request').exists()):
                raise RuntimeError('Public server stopped or changed; do not publish this APK')
        current()
        load_rollback_record(bundle, root/'develop-upgrade')
        base = compose_command(bundle, manifest, allow_public_web=True)
        compose = base+['-f',str(folder/'compose.public-web.json')]
        def command(argv, name):
            current()
            step(name)
            result = execute(argv, cwd=bundle, capture_output=True, timeout=180, creationflags=HIDDEN)
            (out/(name+'.log')).write_bytes(result.stdout+result.stderr)
            if result.returncode:
                raise RuntimeError(name+' failed; inspect its log')
        target = folder/'web/download'
        if target.exists():
            if {p.name for p in target.iterdir()} != PUBLIC_FILES or any(not p.is_file() or p.is_symlink() for p in target.iterdir()):
                raise RuntimeError('Unexpected existing install directory contents')
            previous = read_json(target/'release.json')
            if previous.get('versionCode',0) >= release['versionCode']:
                if previous == release:
                    public_check(origin, release, qr_sha256)
                    step('complete-app-ready', passed=True, alreadyApplied=True, installUrl=origin+'/download/')
                    return report
                raise RuntimeError('App updates must increase versionCode')
            if (previous.get('package') != release['package'] or
                    previous.get('signingCertificate','').lower() != release['signingCertificate'].lower()):
                raise RuntimeError('App update identity changed')
        if args.check_only:
            step('preflight-complete', passed=True, checkOnly=True)
            return report
        backup = operation/('before-'+str(time.time_ns()))
        backup.mkdir()
        for name in ('Caddyfile','release.json'):
            shutil.copy2(folder/name, backup/name)
        shutil.copy2(bundle/'public-web-active.json', backup/'public-web-active.json')
        if target.exists():
            shutil.copytree(target, backup/'download')
        candidate = operation/('assets-'+str(time.time_ns()))
        shutil.copytree(source, candidate)
        report['backup'] = str(backup)
        changed = False
        try:
            current()
            changed = True
            target.mkdir(exist_ok=True)
            for name in PUBLIC_FILES:
                atomic_copy(candidate/name, target/name, backup)
            shutil.copy2(HERE/'Caddyfile.public-app', folder/'Caddyfile')
            command(compose+['run','--rm','--no-deps','-T','edge','caddy','validate',
                             '--config','/etc/caddy/Caddyfile'], 'validate-app-download-route')
            command(compose+['up','-d','--no-deps','--no-build','--pull','never','--force-recreate',
                             '--wait','--wait-timeout','120','edge'], 'open-app-download-route')
            step('verify-public-app-download')
            public_check(origin, release, qr_sha256)
            current()
            prior_release['files'] = {p.relative_to(folder).as_posix():checksum(p)
                                      for p in folder.rglob('*') if p.is_file() and p != folder/'release.json'}
            prior_release['nativeApp'] = release
            json_write(folder/'release.json', prior_release)
            receipt['releaseSha256'] = checksum(folder/'release.json')
            receipt['nativeAppVersionCode'] = release['versionCode']
            json_write(bundle/'public-web-active.json', receipt)
            verify_snapshot(folder, receipt)
            load_rollback_record(bundle, root/'develop-upgrade')
            shutil.copy2(source/'install-qr.png', out/'install-qr.png')
            # The old root QR also redirects to this install page after publication.
            (out/'INSTALL-ON-ANDROID.txt').write_text(origin+'/download/\n\n'
                '안드로이드 APK 다운로드용 QR입니다. 웹 앱 실행용 QR이 아닙니다.\n'
                'PC, Docker, 공개 실행 창을 켜 두세요. 주소가 바뀌면 APK를 새로 빌드해야 합니다.\n',encoding='utf-8')
            step('complete-app-ready', passed=True, installUrl=origin+'/download/',
                 apkUrl=release['downloadUrl'], fullApkDownloadVerified=True,
                 sha256=release['sha256'], bytes=release['bytes'], rollbackPreserved=True)
        except Exception as error:
            report.update(error=str(error), failedStep=report.get('step'))
            if changed:
                for name in ('Caddyfile','release.json'):
                    shutil.copy2(backup/name, folder/name)
                # Keep failed candidates outside the served tree for diagnosis.
                if target.exists():
                    failed = backup/'failed-download'
                    if not target.resolve().is_relative_to(root) or not failed.resolve().is_relative_to(root):
                        raise RuntimeError('Recovery path escapes the runtime')
                    target.rename(failed)
                if (backup/'download').exists():
                    shutil.copytree(backup/'download', target)
                try:
                    current()
                    shutil.copy2(backup/'public-web-active.json',bundle/'public-web-active.json')
                    command(compose+['up','-d','--no-deps','--no-build','--pull','never',
                                     '--force-recreate','--wait','--wait-timeout','120','edge'],
                            'restore-previous-public-edge')
                    report['previousPublicRouteRestored'] = True
                except Exception as restore_error:
                    report['restoreError'] = str(restore_error)
            step('failed')
            raise
    return report


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('config','public-status','assets','verification','output'):
        parser.add_argument('--'+name,required=True)
    parser.add_argument('--check-only',action='store_true')
    publish(parser.parse_args())
