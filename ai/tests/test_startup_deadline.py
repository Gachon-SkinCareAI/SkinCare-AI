import os
import subprocess
import sys

import pytest


@pytest.mark.parametrize('hang',[False,True])
def test_startup_deadline_terminates_stall_but_is_cancelled_after_readiness(hang):
    body = '''
import asyncio, time
from app.main import startup_deadline
async def main():
    async with startup_deadline():
        await asyncio.sleep(DELAY)
    await asyncio.sleep(0.5)
    print('ready-and-still-running')
asyncio.run(main())
'''.replace('DELAY','5' if hang else '0')
    env=dict(os.environ,AI_STARTUP_TIMEOUT_SECONDS='0.2')
    result=subprocess.run([sys.executable,'-B','-c',body],env=env,capture_output=True,timeout=45)
    if hang:
        assert result.returncode==1
        assert b'Timeout' in result.stderr
        assert b'ready-and-still-running' not in result.stdout
    else:
        assert result.returncode==0
        assert b'ready-and-still-running' in result.stdout
