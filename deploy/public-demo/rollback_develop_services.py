"""Restore the deployment that immediately preceded the develop server update."""
import argparse
import json
from pathlib import Path
import subprocess
import time

from develop_rollback import load_rollback_record, restore_services, verify_images
from prepare_bundle import protect
from run_free_demo import HIDDEN, compose_command, instance_lock, json_write
from upgrade_free_demo import validate_existing_volumes


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config', required=True)
    parser.add_argument('--check', action='store_true', help='Check backups and Docker images without stopping services')
    args = parser.parse_args()
    config = json.loads(Path(args.config).read_text(encoding='utf-8-sig'))
    if config.get('format') != 'skincare-free-demo-v1':
        raise RuntimeError('Unexpected launcher configuration')
    root, out = Path(config['runtime']).resolve(), Path(config['output']).resolve()
    bundle, operation = root / 'bundle', root / 'develop-upgrade'
    if not operation.is_dir():
        raise RuntimeError('No develop update backup exists yet')
    with instance_lock(operation):
        backup, record, original = load_rollback_record(bundle, operation)
        compose = compose_command(bundle, original)

        def require_launcher():
            connection = json.loads((out / 'public-connection.json').read_text(encoding='utf-8'))
            if (connection.get('active') is not True or connection.get('origin') != record['origin']
                    or (root / 'stop.request').exists()):
                raise RuntimeError('Keep the original public launcher running at its existing address')

        require_launcher()
        logs = operation / ('rollback-' + str(time.time_ns()))
        logs.mkdir()
        protect(logs)
        report = {'passed': False, 'release': record['release'], 'backupDirectory': str(backup),
                  'checkOnly': args.check, 'databaseRestored': False, 'modelsChanged': False,
                  'apkChanged': False, 'tunnelRestarted': False}
        status_file = out / ('develop-rollback-check.json' if args.check else 'develop-rollback.json')

        def status(step):
            report.update(step=step, updatedAt=time.time())
            json_write(status_file, report)
            print(step, flush=True)

        def command(argv, name, timeout=180, persist=True):
            status(name)
            result = subprocess.run(argv, cwd=bundle, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                    timeout=timeout, creationflags=HIDDEN)
            content = result.stdout.decode('utf-8', errors='replace')
            if persist:
                (logs / (name + '.log')).write_text(content, encoding='utf-8')
            if result.returncode:
                raise RuntimeError(name + ' failed; inspect the private log')
            return content

        try:
            command(['docker', 'version', '--format', '{{.Server.Version}}'], 'docker-engine', 30)
            volumes = json.loads(command(['docker', 'volume', 'inspect', record['project'] + '_database',
                                         record['project'] + '_maintenance'], 'verify-existing-data-volumes', 30, False))
            validate_existing_volumes(volumes, record['project'])
            if args.check:
                verify_images(record, command)
            else:
                restore_services(bundle, backup, record, compose, command, require_launcher)
            report.update(passed=True, publicOrigin=record['origin'])
            status('rollback-preflight-complete' if args.check else 'rollback-complete')
        except Exception as error:
            report.update(failedStep=report.get('step'), errorType=type(error).__name__)
            status('failed')
            raise


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print('Rollback stopped: ' + str(error) + '. No automatic database restore is performed.', flush=True)
        raise SystemExit(1)
