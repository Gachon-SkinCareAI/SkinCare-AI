"""Apply or roll back only the verified startup source overlay on the existing local server."""
import argparse
import json
from pathlib import Path
import shutil
import subprocess
import time
import urllib.error
import urllib.request

from develop_rollback import load_rollback_record
from run_free_demo import HIDDEN, checksum, compose_command, instance_lock, json_write, start_verified_services
from startup_stability_release import FILES, active_overlay, overlay_config, validate_release
from upgrade_free_demo import validate_existing_volumes
from local_startup_readiness import verify_local_readiness


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config', required=True)
    parser.add_argument('--release')
    parser.add_argument('--output', required=True)
    parser.add_argument('--rollback', action='store_true')
    args = parser.parse_args()
    config = json.loads(Path(args.config).read_text(encoding='utf-8-sig'))
    if config.get('format') != 'skincare-free-demo-v1':
        raise RuntimeError('Unexpected existing server configuration')
    root = Path(config['runtime']).resolve()
    bundle = root / 'bundle'
    out = Path(args.output).resolve()
    out.mkdir(parents=True, exist_ok=True)
    logs = out / 'private-logs'
    logs.mkdir(exist_ok=True)
    report = {'passed': False, 'mode': 'rollback' if args.rollback else 'cold-start-update',
              'databaseReset': False, 'modelWeightsChanged': False, 'publicTunnelStarted': False,
              'imagesRebuilt': False, 'readOnlySourceOverlay': True}
    marker = bundle / 'startup-stability.json'
    changed = False

    def status(step):
        report.update(step=step, updatedAt=time.time())
        json_write(out / 'startup-status.json', report)
        print(step, flush=True)

    def command(argv, name, timeout=180, persist=True):
        status(name)
        if 'up' in argv:
            argv = list(argv)
            index = argv.index('up') + 1
            argv[index:index] = ['--no-build', '--pull', 'never']
        result = subprocess.run(argv, cwd=bundle, capture_output=True, timeout=timeout, creationflags=HIDDEN)
        if persist:
            (logs / (name + '.log')).write_bytes(result.stdout + result.stderr)
        if result.returncode:
            raise RuntimeError(name + ' failed; see private-logs/' + name + '.log')
        return result.stdout.decode('utf-8', errors='replace')

    def inspect_services(project, name):
        states = []
        for service in ('ai', 'llm', 'maintenance', 'backend', 'edge'):
            item = json.loads(command(['docker', 'inspect', project + '-' + service + '-1'],
                                     name + '-' + service, 30, False))[0]
            labels = item['Config'].get('Labels') or {}
            if (labels.get('com.docker.compose.project') != project
                    or labels.get('com.docker.compose.service') != service):
                raise RuntimeError('Unexpected service ownership')
            states.append({'service': service, 'id': item['Id'], 'startedAt': item['State']['StartedAt'],
                'running': item['State']['Running'], 'health': item['State'].get('Health', {}).get('Status'),
                'restartCount': item['RestartCount'], 'oomKilled': item['State']['OOMKilled']})
        return states

    try:
        with instance_lock(root / 'develop-upgrade'):
            # Preserve and validate the original release/rollback identities.
            load_rollback_record(bundle, root / 'develop-upgrade')
            manifest = json.loads((bundle / 'bundle-manifest.json').read_text(encoding='utf-8'))
            project = manifest['project']
            command(['docker', 'version', '--format', '{{.Server.Version}}'], 'docker-engine', 30)
            volumes = json.loads(command(['docker', 'volume', 'inspect', project + '_database',
                                          project + '_maintenance'], 'existing-data-volumes', 30, False))
            validate_existing_volumes(volumes, project)
            report['before'] = inspect_services(project, 'before')
            original_compose = compose_command(bundle, manifest)
            command(original_compose + ['logs', '--no-color', '--tail', '180', 'llm'],
                    'before-local-model-runtime', 30)
            for service in ('edge', 'maintenance'):
                command(original_compose + ['logs', '--no-color', '--tail', '100', service],
                        'before-' + service + '-runtime', 30)
            if args.rollback:
                if not marker.is_file():
                    raise RuntimeError('No startup overlay is active')
                active_overlay(bundle, manifest)
                changed = True
                command(original_compose + ['stop', 'edge', 'backend', 'ai', 'maintenance', 'llm'],
                        'close-for-overlay-rollback', 180)
                # Keep the prior receipt as evidence; source files and images stay intact.
                marker.replace(out / ('rolled-back-' + str(time.time_ns()) + '.json'))
            else:
                source = Path(args.release).resolve()
                release = validate_release(source, project)
                if checksum(bundle / 'bundle-manifest.json') != release.get('baseManifestSha256'):
                    raise RuntimeError('Existing deployment changed since packaging')
                if marker.exists():
                    active_overlay(bundle, manifest)
                    existing = json.loads(marker.read_text(encoding='utf-8'))
                    if existing['release'] != release['id']:
                        raise RuntimeError('Roll back the other startup overlay before replacing it')
                folder = bundle / 'startup-stability' / release['id']
                if folder.exists():
                    if validate_release(folder, project) != release:
                        raise RuntimeError('Saved startup release differs')
                else:
                    shutil.copytree(source, folder)
                overlay = folder / 'compose.startup-stability.json'
                json_write(overlay, overlay_config(folder))
                candidate = original_compose if marker.exists() else original_compose + ['-f', str(overlay)]
                merged = json.loads(command(candidate + ['config', '--format', 'json'], 'validate-compose', 30, False))
                # The overlay cannot add a network/port/secret or replace a data/model mount.
                base = json.loads(command(original_compose + ['config', '--format', 'json'], 'read-base-compose', 30, False))
                for name in ('db', 'llm', 'backend'):
                    if merged['services'][name] != base['services'][name]:
                        raise RuntimeError('Unexpected unrelated service change')
                for name in ('ai', 'maintenance', 'edge'):
                    for key in ('networks', 'ports', 'secrets', 'image', 'read_only', 'security_opt'):
                        if merged['services'][name].get(key) != base['services'][name].get(key):
                            raise RuntimeError('Overlay changes protected runtime settings')
                command(candidate + ['run', '--rm', '--no-deps', '--entrypoint', 'caddy', 'edge',
                                     'validate', '--config', '/etc/caddy/Caddyfile', '--adapter', 'caddyfile'],
                        'validate-caddy', 90)
                changed = True
                command(original_compose + ['stop', 'edge', 'backend', 'ai', 'maintenance', 'llm'],
                        'cold-stop-services', 180)
                receipt = {'format': 'skincare-startup-stability-active-v1', 'project': project,
                    'release': release['id'], 'baseManifestSha256': release['baseManifestSha256'],
                    'releaseSha256': checksum(folder / 'release.json'), 'overlaySha256': checksum(overlay)}
                json_write(marker, receipt)
            compose = compose_command(bundle, manifest)
            command(compose + ['up', '-d', '--no-deps', '--wait', '--wait-timeout', '180', 'db'],
                    'existing-database-ready', 220)
            command(compose + ['up', '-d', '--no-deps', '--force-recreate', '--wait', '--wait-timeout',
                               '180', 'maintenance', 'llm'], 'cold-start-maintenance-and-llm', 240)
            command(compose + ['up', '-d', '--no-deps', '--force-recreate', '--wait', '--wait-timeout',
                               '600', 'ai'], 'cold-start-ai', 660)
            start_verified_services(compose, manifest, command, out)
            status('checking-local-routes-across-maintenance-cycle')
            opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
            def get_status(path):
                try:
                    with opener.open('http://127.0.0.1:20880' + path, timeout=15) as response:
                        return response.status
                except urllib.error.HTTPError as error:
                    return error.code
            # Includes a full maintenance cycle. Connection-reset/503 startup
            # failures are retained and reset the entire healthy window.
            readiness = verify_local_readiness(get_status,
                lambda checks: json_write(out / 'local-readiness-checks.json', checks))
            report['after'] = inspect_services(project, 'after')
            if any(not item['running'] or item['oomKilled'] or item['restartCount'] for item in report['after']):
                raise RuntimeError('Unexpected runtime restart or memory failure')
            previous = {item['service']: item['startedAt'] for item in report['before']}
            if any(item['startedAt'] == previous[item['service']] for item in report['after']):
                raise RuntimeError('Cold restart was not verified for every application service')
            report.update(passed=True, browserUrl='http://127.0.0.1:8081/', localReadChecks=readiness['checks'],
                          httpReadiness=readiness)
            status('complete')
    except Exception as error:
        report.update(error=str(error), errorType=type(error).__name__)
        if changed:
            try:
                command(compose_command(bundle, manifest) + ['stop', 'edge'], 'keep-failed-start-private', 90)
                report['edgeStoppedOnFailure'] = True
            except Exception:
                report['edgeStoppedOnFailure'] = False
        status('failed')
        print(str(error), flush=True)
        raise


if __name__ == '__main__':
    try:
        main()
    except Exception:
        raise SystemExit(1)
