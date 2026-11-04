"""Read-only, bounded diagnosis of the active free demo upload failure."""
import argparse
from datetime import datetime, timezone, timedelta
import json
from pathlib import Path
import re
import subprocess

from run_free_demo import HIDDEN, compose_command, json_write


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config', required=True)
    args = parser.parse_args()
    config = json.loads(Path(args.config).read_text(encoding='utf-8-sig'))
    if config.get('format') != 'skincare-free-demo-v1':
        raise RuntimeError('Unexpected launcher configuration')
    root, out = Path(config['runtime']), Path(config['output'])
    bundle = root / 'bundle'
    manifest = json.loads((bundle / 'bundle-manifest.json').read_text())
    connection = json.loads((out / 'public-connection.json').read_text())
    if not connection.get('active') or connection.get('project') != manifest.get('project'):
        raise RuntimeError('The expected demo is not active.')
    compose = compose_command(bundle, manifest)
    secret_values = []
    for path in (bundle / 'private').iterdir():
        if not path.is_file() or path.suffix not in ('.txt', '.properties'):
            continue
        text = path.read_text(encoding='utf-8-sig').strip()
        if path.suffix == '.properties':
            secret_values.extend(line.split('=', 1)[1].strip() for line in text.splitlines()
                                 if '=' in line and not line.startswith('#'))
        elif len(text) >= 8:
            secret_values.append(text)

    def redact(text):
        for value in sorted(secret_values, key=len, reverse=True):
            if len(value) >= 8:
                text = text.replace(value, '[REDACTED]')
        text = re.sub(r'eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+', '[TOKEN]', text)
        text = re.sub(r'(?i)(access_token|refresh_token|client_secret|authorization|password)([=:\s]+)[^\s,;]+', r'\1\2[REDACTED]', text)
        text = re.sub(r'[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}', '[EMAIL]', text)
        return re.sub(r'(?i)\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b', '[UUID]', text)

    def capture(args):
        result = subprocess.run(compose + args, cwd=bundle, stdin=subprocess.DEVNULL,
                                stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                text=True, encoding='utf-8', errors='replace', timeout=60,
                                creationflags=HIDDEN)
        return {'exitCode': result.returncode, 'output': redact(result.stdout)}

    report = {'checkedAt': datetime.now(timezone(timedelta(hours=9))).isoformat(),
              'project': manifest['project'], 'origin': connection['origin'],
              'readOnly': True, 'containers': capture(['ps', '--all', '--format', 'json'])}
    for service in ('backend', 'ai', 'maintenance', 'edge'):
        result = capture(['logs', '--no-color', '--since', '30m', '--tail', '350', service])
        lines = result['output'].splitlines()
        relevant = re.compile(r'(?i)(error|exception|caused by|\bat (skincare\.|java\.desktop/)|permission denied|read-only|no space|not found|\.so\b|sqlstate|maintenance.*failed|timed? ?out|/api/v1/ai/(predict|question))')
        report[service + 'Diagnostics'] = {'exitCode': result['exitCode'],
            'lines': [line[:2500] for line in lines if relevant.search(line)][-160:]}
    report['backendTmp'] = capture(['exec', '-T', 'backend', 'sh', '-c',
        'id; test -w /tmp && echo TMP_WRITABLE; df -h /tmp; ldd "$JAVA_HOME/lib/libawt.so"; ldd "$JAVA_HOME/lib/libawt_headless.so"'])
    report['aiHealth'] = capture(['exec', '-T', 'ai', 'python', '/opt/demo/probe.py', 'ai'])
    probe = '''import json
from db_runtime import ContainerInstance
db=ContainerInstance()
print(json.dumps({"sessionStatuses":db.sql("SELECT status,COUNT(*) FROM analysis_sessions GROUP BY status", "skincare_dev"),"imageColumn":db.sql("SELECT COLUMN_TYPE FROM information_schema.columns WHERE table_schema='skincare_dev' AND table_name='analysis_sessions' AND column_name='image'")}))
'''
    report['databaseSummary'] = capture(['exec', '-T', 'maintenance', 'python3', '-c', probe])
    json_write(root / 'upload-diagnostics.json', report)
    json_write(out / 'upload-diagnostics-status.json', {
        'checkedAt': report['checkedAt'], 'readOnly': True, 'reportWritten': True,
        'databaseOrModelsChanged': False, 'publicUrlChanged': False})
    print('Upload diagnostics saved. No database, model, account or public URL was changed.')


if __name__ == '__main__':
    main()
