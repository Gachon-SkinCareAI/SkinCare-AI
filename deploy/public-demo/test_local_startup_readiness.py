from http.client import RemoteDisconnected
import unittest

from local_startup_readiness import ROUTES, verify_local_readiness


class LocalReadinessTests(unittest.TestCase):
    def run_check(self, outcomes, cycles=2, timeout=30):
        now=[0.]
        pending=iter(outcomes)
        history=[]
        def get(path):
            item=next(pending)
            if isinstance(item,Exception):raise item
            return item
        def pause(seconds):now[0]+=seconds
        result=verify_local_readiness(get,lambda rows:history.append([dict(r) for r in rows]),
            cycles=cycles,interval=5,timeout=timeout,clock=lambda:now[0],pause=pause)
        return result,history

    def test_connection_closed_at_boot_is_retained_then_full_window_required(self):
        result,history=self.run_check([RemoteDisconnected('fixture'),200,401,404,200,401,404])
        self.assertEqual(result['consecutiveHealthyCycles'],2)
        self.assertEqual(result['transientFailures'],1)
        self.assertEqual(history[-1][0]['errorType'],'RemoteDisconnected')

    def test_mid_window_failure_restarts_the_entire_healthy_window(self):
        result,_=self.run_check([200,401,404,503,200,401,404,200,401,404])
        self.assertEqual(result['checks'],10)

    def test_wrong_auth_contract_is_not_retried(self):
        with self.assertRaisesRegex(RuntimeError,'contract'):
            self.run_check([200,200])

    def test_repeated_transient_failures_exhaust_bounded_deadline(self):
        with self.assertRaisesRegex(RuntimeError,'deadline'):
            self.run_check([502,502,502],timeout=10)


if __name__=='__main__':unittest.main()
