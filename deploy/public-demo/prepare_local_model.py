"""Wake/verify the GPU before a cold Ollama load; recover only startup fixtures."""
import json
from pathlib import Path

STATUS = """import json,urllib.request
o=urllib.request.build_opener(urllib.request.ProxyHandler({}))
with o.open('http://llm:11434/api/ps',timeout=10) as r: models=json.load(r).get('models',[])
print(json.dumps({'gpuLoaded':any(m.get('name')=='qwen3:4b' and m.get('size_vram',0)>0 for m in models)}))
"""


def prepare_local_model(compose, run, source):
    def recreate(name):
        # Docker restart left this host's existing container unable to exec
        # (/proc/self/fd/6 missing). A fresh container restores its namespace and
        # GPU attachment while preserving the named model volume and pinned image.
        run(compose + ['up', '-d', '--no-deps', '--no-build', '--pull', 'never',
                       '--force-recreate', 'llm'], name, 120)
        # Cold metadata/GPU initialization on this host exceeded 90 seconds.
        # This budget applies only before publication; patient calls remain 20s.
        run(compose + ['up', '-d', '--no-deps', '--no-build', '--pull', 'never',
                       '--wait', '--wait-timeout', '600', 'llm'],
            name + '-healthy', 660)

    gpu_command = compose + ['exec', '-T', 'llm', 'nvidia-smi',
                             '--query-gpu=name,memory.total,memory.free', '--format=csv,noheader']
    runtime_recreated = False
    try:
        run(gpu_command, 'local-model-gpu-check', 30)
    except RuntimeError:
        # One bounded repair also covers a pre-existing broken exec namespace.
        recreate('local-model-runtime-recreate')
        runtime_recreated = True
        run(gpu_command, 'local-model-gpu-check-after-recreate', 30)
    loaded = json.loads(run(compose + ['exec', '-T', 'ai', 'python', '-B', '-c', STATUS],
                            'local-model-loaded-state', 30).strip().splitlines()[-1])
    cold_refresh = loaded.get('gpuLoaded') is not True and not runtime_recreated
    if cold_refresh:
        recreate('local-model-refresh-gpu')
    attempts = []
    for attempt in range(2):
        step = 'local-model-ready' if attempt == 0 else 'local-model-ready-after-restart'
        try:
            output = run(compose + ['exec', '-T', 'ai', 'python', '-B', '-c',
                                    Path(source).read_text(encoding='utf-8')], step, 900)
            result = json.loads(output.strip().splitlines()[-1])
            if result.get('passed') is not True or result.get('gpuBytes', 0) <= 0:
                raise RuntimeError('Local model did not pass GPU readiness')
            attempts.append({'attempt': attempt + 1, 'passed': True})
            result.update(coldGpuRefreshed=cold_refresh or runtime_recreated,
                          runtimeRecreated=runtime_recreated, preparationAttempts=attempts)
            return result
        except RuntimeError:
            attempts.append({'attempt': attempt + 1, 'passed': False})
            run(compose + ['logs', '--no-color', '--tail', '180', 'llm'],
                'local-model-failed-runtime' if attempt == 0 else 'local-model-final-failure-runtime', 30)
            if attempt:
                raise
            # Preserve the failed attempt, then reset only the local model. No
            # patient request is retried and no database or model file changes.
            recreate('local-model-recover-gpu')
    raise RuntimeError('Local model preparation failed')
