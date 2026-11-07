"""Validate an AI release without joining production networks or replacing services."""
import copy
import json
from pathlib import Path
import re
import subprocess
import time

from run_free_demo import HIDDEN, json_write
from upload_recovery_release import ENVIRONMENT, FILES, validate


def isolated_config(baseline, source, image):
    validate(source)
    ai = copy.deepcopy(baseline['services']['ai'])
    for key in ('build', 'networks', 'depends_on', 'ports', 'container_name', 'hostname',
                'profiles', 'labels', 'links', 'external_links', 'extra_hosts', 'env_file'):
        ai.pop(key, None)
    ai.update(image=image, network_mode='none', restart='no')
    ai.setdefault('environment', {}).update(ENVIRONMENT)
    mounts = {m['target']: m for m in ai.get('volumes', [])}
    for name, (service, target) in FILES.items():
        if service == 'ai':
            mounts[target] = {'type': 'bind', 'source': str(Path(source)/name),
                             'target': target, 'read_only': True, 'bind': {'create_host_path': False}}
    if any(m.get('read_only') is not True for m in mounts.values()):
        raise RuntimeError('Isolated AI check requires all shared mounts to be read-only')
    ai['volumes'] = list(mounts.values())
    config = {'services': {'ai': ai}}
    for mount in mounts.values():
        if mount['type'] == 'volume':
            name = mount['source']
            volume = baseline.get('volumes', {}).get(name, {})
            if not volume.get('name'):
                raise RuntimeError('Existing model volume has no resolved name')
            config.setdefault('volumes', {})[name] = {'name': volume['name'], 'external': True}
    for secret in ai.get('secrets', []):
        name = secret['source'] if isinstance(secret, dict) else secret
        config.setdefault('secrets', {})[name] = copy.deepcopy(baseline['secrets'][name])
    return config


def summarize_logs(raw):
    # Do not retain exception messages, request URLs, answers or source lines.
    records = []
    events = {'vision_prepare_stage', 'vision_prepare_failed', 'vision_load', 'vision_warmup',
              'vision_timing', 'vision_watchdog_timeout'}
    keys = {'event', 'stage', 'error_type', 'operation', 'device', 'elapsed_ms', 'total_ms',
            'detector_ms', 'classifier_ms', 'quality_ms', 'cpu_threads', 'synthetic_input_only'}
    frames = []
    for line in raw.splitlines():
        if '{' in line:
            try:
                value = json.loads(line[line.index('{'):])
            except ValueError:
                value = None
            if isinstance(value, dict) and value.get('event') in events:
                records.append({k: v for k, v in value.items() if k in keys})
        # Accept both direct Python output and Compose's "ai-1 | ..." prefix.
        match = re.search(r'(?:^|\|)\s*File "([^"\r\n]+\.py)", line (\d+)(?:,? in ([\w<>]+))?', line)
        if match:
            frames.append({'file': match[1], 'line': int(match[2]), 'function': match[3]})
    return {'events': records[-60:], 'stackFrames': frames[-80:],
            'startupComplete': 'Application startup complete' in raw,
            'deadlineExpired': bool(re.search(r'Timeout \([\d:.]+\)!', raw)),
            'errorTypes': sorted(set(re.findall(r'\b(?:[A-Za-z]+Error|[A-Za-z]+Exception)\b', raw)))}


def capture_diagnostics(execute, bundle, compose, out, name):
    report = {'patientContentCollected': False}
    try:
        result = execute(compose+['logs', '--no-color', '--no-log-prefix', '--tail', '600', 'ai'], cwd=bundle,
                         capture_output=True, timeout=25, creationflags=HIDDEN)
        report.update(summarize_logs((result.stdout+result.stderr).decode('utf-8', errors='replace')))
        report['logsExitCode'] = result.returncode
    except Exception as error:
        report['logsReadFailure'] = type(error).__name__
    try:
        result = execute(compose+['ps', '--all', '--format', 'json', 'ai'], cwd=bundle,
                         capture_output=True, timeout=20, creationflags=HIDDEN)
        raw = result.stdout.decode('utf-8', errors='replace').strip()
        rows = json.loads(raw) if raw.startswith('[') else [json.loads(s) for s in raw.splitlines() if s]
        report['containers'] = [{key: row.get(key) for key in ('State', 'Health', 'ExitCode')} for row in rows]
    except Exception as error:
        report['stateReadFailure'] = type(error).__name__
    json_write(Path(out)/(name+'.json'), report)


class IsolatedCheckError(RuntimeError):
    def __init__(self, message, step):
        super().__init__(message)
        self.step = step


def check_isolated(baseline, source, image, operation, project, bundle, out, execute, current, step, probe,
                   validation_photo=None, question_probe=None):
    if not re.fullmatch(r'skincare-free-demo-[0-9a-f]{8}', project) or not re.fullmatch(r'sha256:[0-9a-f]{64}', image):
        raise RuntimeError('Unexpected isolated-check identity')
    check_project = project+'-upload-check-'+str(time.time_ns())
    folder = Path(operation)/check_project
    folder.mkdir()
    config = isolated_config(baseline, source, image)
    photo_arg = []
    photo_bytes = None
    if validation_photo:
        photo = Path(validation_photo).resolve()
        if not photo.is_file() or not 0 < photo.stat().st_size <= 8 * 1024 * 1024:
            raise RuntimeError('Missing or oversized validation photo')
        with photo.open('rb') as stream:
            photo_bytes = stream.read(8 * 1024 * 1024 + 1)
        if not photo_bytes.startswith(b'\xff\xd8') or len(photo_bytes) > 8 * 1024 * 1024:
            raise RuntimeError('Validation photo must be a JPEG within the API size limit')
        # Exercise the same binary-stdin path as the read-only production AI.
        photo_arg = ['--stdin-photo']
    json_write(folder/'compose.json', config)
    compose = ['docker', 'compose', '-p', check_project, '-f', str(folder/'compose.json')]
    completed = False
    started = False

    def run(tail, label, timeout, input_data=None):
        current()
        step(label)
        try:
            result = execute(compose+tail, cwd=bundle, capture_output=True, timeout=timeout,
                             creationflags=HIDDEN, **({'input':input_data} if input_data is not None else {}))
        except subprocess.TimeoutExpired as error:
            (Path(out)/(label+'.log')).write_bytes((error.stdout or b'')+(error.stderr or b''))
            raise IsolatedCheckError(label+' timed out; production services unchanged', label) from None
        (Path(out)/(label+'.log')).write_bytes(result.stdout+result.stderr)
        if result.returncode:
            raise IsolatedCheckError(label+' failed; production services unchanged; see isolated-ai.json', label)
        return result.stdout.decode('utf-8', errors='replace')

    try:
        run(['config', '--quiet'], 'validate-isolated-ai', 30)
        started = True
        run(['up', '-d', '--no-deps', '--no-build', '--pull', 'never', '--wait',
             '--wait-timeout', '180', 'ai'], 'check-isolated-ai-startup', 210)
        phases = ('synthetic', 'real-first', 'after-idle') if photo_arg else ('first', 'after-idle')
        for phase in phases:
            if phase == 'after-idle':
                step('waiting-isolated-ai-idle')
                for _ in range(13):
                    current()
                    time.sleep(5)
            arguments = photo_arg if phase != 'synthetic' else []
            raw = run(['exec', '-T', '--interactive', 'ai', 'python', '-B', '-c', probe]+arguments,
                      'check-isolated-photo-'+phase, 100, input_data=photo_bytes if arguments else None)
            result = json.loads(raw.strip().splitlines()[-1])
            if result.get('passed') is not True or arguments and result.get('validationPhoto') is not True:
                raise RuntimeError('Isolated photo inference failed')
        if question_probe:
            raw = run(['exec','-T','ai','python','-B','-c',question_probe], 'check-isolated-questionnaire', 55)
            if json.loads(raw.strip().splitlines()[-1]).get('passed') is not True:
                raise RuntimeError('Isolated questionnaire probe failed')
        completed = True
    finally:
        if started:
            capture_diagnostics(execute, bundle, compose, out, 'isolated-ai')
            # This unique project contains one disposable AI, no DB or networks.
            # Cleanup must still work when the public launcher requested a stop.
            step('remove-isolated-ai')
            result = execute(compose+['down', '--timeout', '10'], cwd=bundle,
                             capture_output=True, timeout=60, creationflags=HIDDEN)
            (Path(out)/'remove-isolated-ai.log').write_bytes(result.stdout+result.stderr)
            if result.returncode:
                raise IsolatedCheckError('Isolated AI cleanup failed; production services unchanged', 'remove-isolated-ai')
    return completed
