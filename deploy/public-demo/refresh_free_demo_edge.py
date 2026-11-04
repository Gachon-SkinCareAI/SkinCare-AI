"""Apply only the public demo edge config, preserving its tunnel, DB and models."""
import argparse
import json
from pathlib import Path
import shutil
import subprocess
import time
import urllib.error
import urllib.request

from run_free_demo import HERE, HIDDEN, checksum, compose_command, json_write


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config', required=True)
    args = parser.parse_args()
    config = json.loads(Path(args.config).read_text(encoding='utf-8-sig'))
    if config.get('format') != 'skincare-free-demo-v1':
        raise RuntimeError('Unexpected launcher configuration')
    root, out = Path(config['runtime']), Path(config['output'])
    bundle = root / 'bundle'
    manifest_path = bundle / 'bundle-manifest.json'
    manifest = json.loads(manifest_path.read_text(encoding='utf-8'))
    connection = json.loads((out / 'public-connection.json').read_text(encoding='utf-8'))
    if not connection.get('active') or connection.get('project') != manifest.get('project'):
        raise RuntimeError('Keep the existing free demo launcher running.')
    if connection['origin'] != 'https://' + manifest['publicHost']:
        raise RuntimeError('Public origin does not match the active bundle.')
    compose = compose_command(bundle, manifest)
    source, target = HERE / 'Caddyfile.tunnel', bundle / 'Caddyfile.tunnel'
    if 'uri /ready?' not in source.read_text():
        raise RuntimeError('Expected the query-safe edge configuration.')
    backup = root / 'Caddyfile.tunnel.before-query-fix'
    report = {'origin': connection['origin'], 'project': manifest['project'],
              'tunnelRestarted': False, 'databaseChanged': False,
              'modelsChanged': False, 'passed': False}

    def run(args, name):
        with (root / (name + '.log')).open('wb') as log:
            result = subprocess.run(compose + args, cwd=bundle, stdin=subprocess.DEVNULL,
                                    stdout=log, stderr=subprocess.STDOUT, timeout=180,
                                    creationflags=HIDDEN)
        if result.returncode:
            raise RuntimeError(name + ' failed; inspect its private log.')

    def status(origin, path):
        try:
            with urllib.request.urlopen(origin + path, timeout=15) as response:
                return response.status
        except urllib.error.HTTPError as error:
            return error.code

    changed = False
    try:
        if checksum(target) != checksum(source):
            if backup.exists():
                raise RuntimeError('An earlier edge update needs review; the backup was preserved.')
            shutil.copy2(target, backup)
            shutil.copy2(source, target)
            changed = True
        print('edge-config-validation', flush=True)
        run(['run', '--rm', '--no-deps', 'edge', 'caddy', 'validate', '--config', '/etc/caddy/Caddyfile'], 'edge-fix-validation')
        print('edge-recreate-only', flush=True)
        run(['up', '-d', '--no-deps', '--force-recreate', '--wait', '--wait-timeout', '120', 'edge'], 'edge-fix-apply')
        manifest['sources']['Caddyfile.tunnel'] = checksum(target)
        json_write(manifest_path, manifest)
        checks = {}
        for scope, origin in [('local', 'http://127.0.0.1:20880'), ('public', connection['origin'])]:
            for attempt in range(10):
                try:
                    checks[scope] = {
                        'plainProviderStatus': status(origin, '/api/wc/auth/social/providers'),
                        'queryProviderStatus': status(origin, '/api/wc/auth/social/providers?probe=1'),
                        'queryAnalysisStatus': status(origin, '/api/wc/analyses?probe=1'),
                        'queryPrivateAiStatus': status(origin, '/api/v1/ai/health?probe=1'),
                        'attempts': attempt + 1,
                    }
                    if all(checks[scope][key] == expected for key, expected in
                           [('plainProviderStatus', 200), ('queryProviderStatus', 200),
                            ('queryAnalysisStatus', 401), ('queryPrivateAiStatus', 404)]):
                        break
                except OSError:
                    pass
                time.sleep(2)
            else:
                raise RuntimeError('Edge verification failed for ' + scope)
        report.update(passed=True, checks=checks, checkedAt=time.time())
        print('Edge query fix verified. Public URL unchanged: ' + connection['origin'], flush=True)
    except Exception:
        if changed:
            shutil.copy2(backup, target)
            try:
                run(['up', '-d', '--no-deps', '--force-recreate', '--wait', '--wait-timeout', '120', 'edge'], 'edge-fix-rollback')
                manifest['sources']['Caddyfile.tunnel'] = checksum(target)
                json_write(manifest_path, manifest)
                report['rollbackSucceeded'] = True
            except Exception:
                report['rollbackSucceeded'] = False
        raise
    finally:
        json_write(out / 'edge-query-fix-verification.json', report)


if __name__ == '__main__':
    main()
