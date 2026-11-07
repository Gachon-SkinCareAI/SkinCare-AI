"""Replace verified APK assets on an already configured, unchanged download route.

Does not control Docker or edit Caddy/Compose. Preserves the develop server rollback
by recording the new distribution alongside the same previous backend/AI images.
"""
import argparse
import copy
import hashlib
import json
from pathlib import Path
import shutil
import time
import urllib.parse
import zipfile

from apk_distribution import PUBLIC_FILES
from develop_rollback import FILES, load_rollback_record, save_rollback_record
from publish_apk import read_json, ready_response, validate
from run_free_demo import checksum, compose_command, instance_lock, json_write


def atomic_copy(source, destination, private_directory):
    temporary = private_directory / ('copy-' + str(time.time_ns()))
    shutil.copyfile(source, temporary)
    if checksum(temporary) != checksum(source):
        raise RuntimeError('Staged public asset checksum mismatch')
    temporary.replace(destination)


def verify_previous_api(old_jar, current_jar):
    allowed = {'skincare/config/AppConfig.class', 'skincare/dto/ai/AiQuestionResponseDto.class',
               'skincare/external/AiServiceClient.class', 'skincare/external/AiServiceClient$1.class',
               'skincare/service/QuestionnaireService.class'}
    prefix = 'BOOT-INF/classes/'
    with zipfile.ZipFile(old_jar) as old, zipfile.ZipFile(current_jar) as new:
        names = {n for n in set(old.namelist()) | set(new.namelist()) if n.startswith(prefix + 'skincare/') and n.endswith('.class')}
        checked = 0
        for name in names:
            relative = name[len(prefix):]
            if relative in allowed or relative.startswith('skincare/external/AiProtocol'):
                continue
            if name not in old.namelist() or name not in new.namelist() or old.read(name) != new.read(name):
                raise RuntimeError('Previous backend API differs; do not refresh the rollback automatically')
            checked += 1
    if not checked:
        raise RuntimeError('No application classes were verified')
    return checked


def verify_distribution(origin, release, get=ready_response):
    with get(origin + '/download/release.json') as response:
        if json.load(response) != release:
            raise RuntimeError('Public APK metadata mismatch')
    with get(origin + '/download/') as response:
        html = response.read().decode('utf-8')
        if 'SkinCare-AI.apk' not in html or release['sha256'].lower() not in html.lower():
            raise RuntimeError('Install page does not identify the new APK')
    digest = hashlib.sha256()
    size = 0
    with get(origin + '/download/SkinCare-AI.apk', timeout=45) as response:
        for chunk in iter(lambda: response.read(1024 * 1024), b''):
            size += len(chunk)
            digest.update(chunk)
            if size > release['bytes']:
                raise RuntimeError('Downloaded APK exceeds expected size')
    if size != release['bytes'] or digest.hexdigest() != release['sha256'].lower():
        raise RuntimeError('Downloaded APK checksum mismatch')


def validate_existing_route(bundle, manifest, origin, previous):
    compose_command(bundle, manifest)
    host = urllib.parse.urlsplit(origin).hostname
    if not manifest.get('distributionEnabled'):
        raise RuntimeError('The public download route has not been configured')
    for name in ('Caddyfile.tunnel', 'compose.distribution.yaml'):
        if checksum(bundle / name) != manifest['sources'].get(name):
            raise RuntimeError('Existing public route configuration changed')
    overlay = read_json(bundle / 'compose.distribution.yaml')
    edge = overlay.get('services', {}).get('edge', {})
    if (edge.get('environment', {}).get('DISTRIBUTION_HOST') != host
            or './distribution:/srv/distribution:ro' not in edge.get('volumes', [])):
        raise RuntimeError('The existing server does not mount this install directory')
    if manifest.get('distributionRelease') != previous or previous.get('apiOrigin') != origin:
        raise RuntimeError('Current distribution identity differs from the server manifest')


def prepare_rollback_for_distribution(bundle, operation, old_backup, record, original, release):
    backup = operation / ('before-' + str(time.time_ns()))
    backup.mkdir()
    for name in FILES:
        destination = backup / name
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(old_backup / name, destination)
    old_manifest = copy.deepcopy(original)
    old_manifest['distributionRelease'] = release
    json_write(backup / 'bundle-manifest.json', old_manifest)
    protected = dict(record['protectedFiles'])
    for name in PUBLIC_FILES:
        protected['distribution/' + name] = checksum(bundle / 'distribution' / name)
    server_release = {'id': record['release'], 'project': record['project'], 'origin': record['origin'],
                      'baseFiles': record['oldFiles'], 'files': record['newFiles'], 'protectedFiles': protected}
    refreshed = save_rollback_record(backup, server_release, record['images'], {})
    return backup, refreshed


def publish(args, public_check=verify_distribution):
    config = read_json(Path(args.config))
    if config.get('format') != 'skincare-free-demo-v1':
        raise RuntimeError('Unexpected launcher configuration')
    root, out = Path(config['runtime']).resolve(), Path(config['output']).resolve()
    bundle = root / 'bundle'
    operation, server_operation = root / 'apk-distribution', root / 'develop-upgrade'
    operation.mkdir(exist_ok=True)
    source = Path(args.assets).resolve()
    report = {'passed': False, 'databaseChanged': False, 'serverRestarted': False, 'routeChanged': False,
              'sourceRoot': r'C:\Users\kirit\SkinCare-AI-develop\mobile'}

    def status(step):
        report.update(step=step, updatedAt=time.time())
        json_write(Path(args.report), report)
        print(step, flush=True)

    with instance_lock(operation), instance_lock(server_operation):
        connection_file = out / 'public-connection.json'
        connection = read_json(connection_file)
        origin = connection['origin']
        def current():
            c = read_json(connection_file)
            if not c.get('active') or c.get('origin') != origin or (root / 'stop.request').exists():
                raise RuntimeError('Existing public launcher stopped or changed')
        current()
        release = validate(source, origin, Path(args.verification))
        marker = bundle / 'bundle-manifest.json'
        manifest = read_json(marker)
        distribution = bundle / 'distribution'
        previous = read_json(distribution / 'release.json')
        validate_existing_route(bundle, manifest, origin, previous)
        if set(p.name for p in distribution.iterdir()) != PUBLIC_FILES:
            raise RuntimeError('Unexpected files in existing distribution directory')
        if (release['package'] != previous['package']
                or release['signingCertificate'].lower() != previous['signingCertificate'].lower()
                or release['versionCode'] < previous['versionCode']):
            raise RuntimeError('New APK must preserve package/signature and increase version')
        if release['versionCode'] == previous['versionCode']:
            if release != previous:
                raise RuntimeError('The same version has a different APK')
            public_check(origin, release)
            load_rollback_record(bundle, server_operation)
            report.update(passed=True, alreadyApplied=True, versionCode=release['versionCode'])
            status('already-published')
            return report
        old_backup, record, original = load_rollback_record(bundle, server_operation)
        if record['origin'] != origin or manifest != record['newManifest']:
            raise RuntimeError('Only the current verified develop release may publish this update')
        report['unchangedApplicationClasses'] = verify_previous_api(old_backup / 'backend/app.jar', bundle / 'backend/app.jar')
        public_check(origin, previous)
        report.update(versionCode=release['versionCode'], previousVersionCode=previous['versionCode'], origin=origin)
        if args.check_only:
            report.update(passed=True, checkOnly=True)
            status('preflight-complete')
            return report
        status('save-previous-install-assets')
        backup = operation / ('before-static-' + str(time.time_ns()))
        backup.mkdir()
        shutil.copytree(distribution, backup / 'distribution')
        saved_manifest = marker.read_bytes()
        saved_pointer = (server_operation / 'latest-rollback.json').read_bytes()
        saved_connection = connection_file.read_bytes()
        (backup / 'bundle-manifest.json').write_bytes(saved_manifest)
        (backup / 'rollback-pointer.json').write_bytes(saved_pointer)
        (backup / 'public-connection.json').write_bytes(saved_connection)
        report['previousAssetsBackup'] = str(backup)
        changed, pointer_changed, connection_changed = False, False, False
        try:
            current()
            if marker.read_bytes() != saved_manifest:
                raise RuntimeError('Deployment changed during preflight')
            # Each rename exposes a complete file; readers never receive a half-written APK.
            changed = True
            for name in ('SkinCare-AI.apk', 'install-qr.png', 'release.json', 'index.html'):
                atomic_copy(source / name, distribution / name, backup)
            status('verify-new-public-apk')
            public_check(origin, release)
            current()
            # All public API classes were checked unchanged; preserve the new APK when
            # rolling the server back to its original files and original Docker images.
            pointer_changed = True
            rollback_backup, refreshed = prepare_rollback_for_distribution(bundle, server_operation, old_backup, record, original, release)
            json_write(marker, refreshed['newManifest'])
            load_rollback_record(bundle, server_operation)
            c = read_json(connection_file)
            c.update(publicApkVersionCode=release['versionCode'], publicApkBuilt=True, installUrl=origin + '/download/')
            connection_changed = True
            json_write(connection_file, c)
            report.update(passed=True, fullApkDownloadVerified=True, rollbackRecordRefreshed=True,
                          serverRollbackBackup=str(rollback_backup), installUrl=origin + '/download/', sha256=release['sha256'])
            status('complete')
        except Exception as error:
            report.update(failedStep=report.get('step'), errorType=type(error).__name__)
            if changed:
                try:
                    for name in ('SkinCare-AI.apk', 'install-qr.png', 'release.json', 'index.html'):
                        atomic_copy(backup / 'distribution' / name, distribution / name, backup)
                    marker.write_bytes(saved_manifest)
                    if pointer_changed:
                        (server_operation / 'latest-rollback.json').write_bytes(saved_pointer)
                    if connection_changed:
                        connection_file.write_bytes(saved_connection)
                    public_check(origin, previous)
                    report['previousAssetsRestored'] = True
                except Exception as restore_error:
                    report.update(previousAssetsRestored=False, restoreErrorType=type(restore_error).__name__)
            status('failed')
            raise
    return report


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('config', 'assets', 'verification', 'report'):
        parser.add_argument('--' + name, required=True)
    parser.add_argument('--check-only', action='store_true')
    args = parser.parse_args()
    try:
        publish(args)
    except Exception as error:
        print('Install update stopped: ' + str(error), flush=True)
        raise SystemExit(1)
