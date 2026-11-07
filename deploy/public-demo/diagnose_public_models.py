"""Read only numeric timings, container states and bounded error categories."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import sys
from datetime import datetime, timezone

from diagnose_analysis_speed import timing_records, vision_timings, backend_timings, llm_timings
from run_free_demo import HIDDEN, json_write


def capture(config_path, output):
    out = Path(output)
    config = json.loads(Path(config_path).read_text(encoding='utf-8-sig'))
    if config.get('format') != 'skincare-free-demo-v1':
        raise RuntimeError('Unexpected launcher configuration')
    out.mkdir(parents=True, exist_ok=True)
    bundle = Path(config['runtime']) / 'bundle'
    manifest = json.loads((bundle / 'bundle-manifest.json').read_text(encoding='utf-8'))
    project = manifest['project']
    if not re.fullmatch(r'skincare-free-demo-[0-9a-f]{8}', project):
        raise RuntimeError('Unexpected project')
    status = json.loads((out / 'public-status.json').read_text(encoding='utf-8'))
    report = {'checkedAt': datetime.now(timezone.utc).isoformat(), 'project': project,
              'origin': status.get('origin'), 'readOnly': True, 'serviceRestarted': False,
              'newInferenceRequested': False, 'patientContentCollected': False,
              'completed': False, 'containers': {}, 'timings': {}, 'errors': {}}

    def run(args):
        result = subprocess.run(['docker'] + args, cwd=bundle, stdin=subprocess.DEVNULL,
                                stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                timeout=45, creationflags=HIDDEN)
        if result.returncode:
            raise RuntimeError('Read-only Docker command failed: ' + args[0])
        return result.stdout.decode('utf-8', errors='replace')

    try:
        print('inspect-existing-services', flush=True)
        names = []
        for service in ('ai', 'llm', 'maintenance', 'backend', 'edge', 'db'):
            name = project + '-' + service + '-1'
            info = json.loads(run(['inspect', name]))[0]
            labels = info['Config'].get('Labels') or {}
            if labels.get('com.docker.compose.project') != project or labels.get('com.docker.compose.service') != service:
                raise RuntimeError('Container identity differs')
            state = info['State']
            report['containers'][service] = {
                'running': state['Running'], 'health': state.get('Health', {}).get('Status'),
                'startedAt': state['StartedAt'], 'restartCount': info['RestartCount'],
                'oomKilled': state.get('OOMKilled'), 'memoryLimitBytes': info['HostConfig'].get('Memory')}
            if service == 'llm':
                # This healthcheck runs `ollama show qwen3:4b`, never a patient prompt.
                report['llmHealthChecks'] = [{key: entry.get(key) for key in ('Start','End','ExitCode','Output')}
                    for entry in state.get('Health', {}).get('Log', [])[-4:]]
            names.append(name)
        report['dockerVm'] = json.loads(run(['info', '--format', '{"memoryBytes":{{.MemTotal}},"cpus":{{.NCPU}}}']))
        print('read-existing-timings', flush=True)
        patterns = {
            'readTimeout': r'ReadTimeout|Read timed out|read timeout',
            'connectTimeout': r'ConnectTimeout|connect timed out',
            'dnsFailure': r'Temporary failure in name resolution|Name or service not known|no such host|lookup .*timeout',
            'connectionRefused': r'Connection refused|connect: connection refused',
            'deadlockOrLockTimeout': r'Deadlock|Lock wait timeout|SQLState: 40001',
            'oom': r'OutOfMemoryError|out of memory|\bOOM\b',
            'modelUnavailable': r'model.*not found|model.*loading|unable to load',
        }
        for service in ('ai', 'backend', 'llm', 'maintenance', 'edge'):
            raw = run(['logs', '--since', '20m', '--tail', '1500', project + '-' + service + '-1'])
            report['errors'][service] = {key: len(re.findall(pattern, raw, re.I)) for key, pattern in patterns.items()}
            if service == 'ai':
                report['timings']['aiRequests'] = timing_records(raw)
                report['timings']['vision'] = vision_timings(raw)
            elif service == 'backend':
                report['timings']['backend'] = backend_timings(raw)
            elif service == 'llm':
                report['timings']['llm'] = llm_timings(raw)
                report['modelLoading'] = {
                    'mmapDisabledForHostPressure': raw.count('disabling mmap for llama-server load due to host memory pressure'),
                    'fileMappedLoads': len(re.findall(r'load_mode = mmap\b', raw)),
                    'nonMappedLoads': len(re.findall(r'load_mode = none\b', raw)),
                    'loadTimeouts': raw.count('timed out waiting for llama-server to start'),
                    'loadFailures': raw.count('msg="Load failed"'),
                }
        print('read-resources-and-loaded-models', flush=True)
        raw = run(['stats', '--no-stream', '--format', '{{json .}}'] + names)
        report['resources'] = [{key: item.get(key) for key in ('Name', 'CPUPerc', 'MemUsage', 'MemPerc', 'PIDs')}
                               for item in (json.loads(line) for line in raw.splitlines() if line.strip())]
        memory_probe = """import json
from pathlib import Path
keep={'MemTotal','MemAvailable','SwapTotal','SwapFree'}
memory={line.split(':',1)[0]:line.split(':',1)[1].strip() for line in Path('/proc/meminfo').read_text().splitlines() if line.split(':',1)[0] in keep}
print(json.dumps(memory))
"""
        report['vmMemory'] = json.loads(run(['exec', project + '-ai-1', 'python', '-B', '-c', memory_probe]))
        report['loadedModels'] = run(['exec', project + '-llm-1', 'ollama', 'ps'])[:2000]
        report['completed'] = True
    except Exception as error:
        report['error'] = str(error)
    finally:
        json_write(out / 'public-model-diagnostics.json', report)
    print(('complete' if report['completed'] else 'partial') +
          ' - read-only diagnostics saved; URL, services and data unchanged.', flush=True)
    return report


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config', required=True)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    sys.exit(0 if capture(args.config, args.output)['completed'] else 1)
