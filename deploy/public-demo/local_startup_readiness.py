"""Require a continuous healthy HTTP window, retaining transient startup failures."""
import time

ROUTES = (('/api/wc/auth/guest/policy', 200), ('/api/wc/analyses', 401), ('/api/v1/ai/health', 404))


def verify_local_readiness(get, record, *, cycles=16, interval=5, timeout=240,
                           clock=time.monotonic, pause=time.sleep):
    history=[]
    deadline=clock()+timeout
    consecutive=0
    attempt=0
    while consecutive<cycles:
        attempt+=1
        cycle_passed=True
        for path,expected in ROUTES:
            started=clock()
            code=None
            error_type=None
            try:
                code=get(path)
            except OSError as error:
                error_type=type(error).__name__
            passed=code==expected
            history.append({'attempt':attempt,'path':path,'expectedStatus':expected,'status':code,
                'errorType':error_type,'passed':passed,'seconds':round(clock()-started,3)})
            record(history)
            if not passed:
                cycle_passed=False
                if error_type is None and code not in (502,503,504):
                    raise RuntimeError('Unexpected local response contract: '+path)
                break
        consecutive=consecutive+1 if cycle_passed else 0
        if consecutive>=cycles:
            return {'checks':len(history),'consecutiveHealthyCycles':consecutive,
                    'transientFailures':sum(not item['passed'] for item in history),
                    'requiredHealthyWindowSeconds':(cycles-1)*interval}
        if clock()>=deadline:
            raise RuntimeError('Local HTTP readiness did not remain stable before the startup deadline')
        pause(min(interval,max(0,deadline-clock())))
