"""Keep model work on the prepared worker without blocking HTTP health checks."""
import asyncio
import faulthandler
from functools import partial
import json
import logging
import os
import threading

from fastapi import HTTPException

_SLOW_JOB_TRACE_SECONDS = 10


def _run_native(operation, args, trace_slow_job):
    # This runs only on the one prepared vision executor. The C-level timer
    # also captures calls that hold the GIL, and fires before the HTTP deadline.
    # It emits frames only; a successful job cancels it before the next job.
    armed = False
    if trace_slow_job:
        try:
            faulthandler.dump_traceback_later(_SLOW_JOB_TRACE_SECONDS, repeat=False)
            armed = True
        except (OSError, RuntimeError, ValueError):
            logging.getLogger('skincare.performance').error('vision_slow_trace_unavailable')
    try:
        return operation(*args)
    finally:
        if armed:
            faulthandler.cancel_dump_traceback_later()


def _unavailable():
    return HTTPException(status_code=503, detail={
        'error': 'vision_recovering', 'message': '사진 분석 작업을 복구 중입니다. 저장된 기록에서 다시 시도해 주세요.'})


def _watchdog_failed(request, operation):
    state = request.app.state
    if getattr(state, 'vision_failed', False):
        return
    state.vision_failed = True
    name = getattr(operation, '__name__', '')
    logging.getLogger('skincare.performance').error(json.dumps({
        'event': 'vision_watchdog_timeout',
        'operation': name if name in ('read_bytes', 'predict_array', 'compare_images') else 'vision'}))
    # Python cannot stop a native Torch/OpenMP thread. Under the deployment's
    # verified restart policy, terminate this AI process after flushing the 503.
    # Never open a second executor while the old model is still computing.
    if os.environ.get('AI_VISION_WATCHDOG_EXIT') == '1':
        # Keep the blocked native call's Python stack before this process exits.
        # faulthandler emits frames only, never image buffers or local values.
        try:
            faulthandler.dump_traceback(all_threads=True)
        except (OSError, RuntimeError, ValueError):
            logging.getLogger('skincare.performance').error('vision_stack_dump_unavailable')
        timer = threading.Timer(1.0, os._exit, args=(70,))
        timer.daemon = True
        timer.start()


async def run_vision(request, operation, *args, timeout_seconds=None):
    if getattr(request.app.state, 'vision_failed', False):
        raise _unavailable()
    loop = asyncio.get_running_loop()
    timeout = (max(1., min(50., float(os.environ.get('AI_VISION_TIMEOUT_SECONDS', '40'))))
               if timeout_seconds is None else timeout_seconds)
    deadline = loop.time() + timeout
    executor = getattr(request.app.state, 'vision_executor', None)
    trace = executor is not None and os.environ.get('AI_VISION_WATCHDOG_EXIT') == '1'
    pending = loop.run_in_executor(executor, partial(_run_native, operation, args, trace))
    try:
        return await asyncio.wait_for(asyncio.shield(pending), timeout)
    except asyncio.TimeoutError:
        _watchdog_failed(request, operation)
        raise _unavailable() from None
    except asyncio.CancelledError:
        # A disconnected caller must not release its inflight slot while native
        # inference still runs. The executor also serializes model access.
        try:
            await asyncio.wait_for(asyncio.shield(pending), max(0., deadline - loop.time()))
        except asyncio.TimeoutError:
            _watchdog_failed(request, operation)
        finally:
            raise
