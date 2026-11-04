import unittest
from update_analysis_speed import stable_llm_checks


def good(seconds=0.5):return {'passed':True,'turns':[{'seconds':seconds,'allowed':True}]}


class LlmReadinessTests(unittest.TestCase):
    def execute(self,outcomes):
        calls=[];records=[];pauses=[]
        def check(attempt):
            calls.append(attempt);result=outcomes[len(calls)-1]
            if isinstance(result,Exception):raise result
            return result
        result=stable_llm_checks(check,lambda history:records.append([dict(i) for i in history]),pauses.append)
        return result,calls,records,pauses

    def test_two_consecutive_complete_checks_are_required(self):
        result,calls,records,pauses=self.execute([good(),good()])
        self.assertEqual(calls,[1,2]);self.assertEqual(pauses,[10])
        self.assertEqual(records[-1][-1]['consecutivePasses'],2)

    def test_transient_failure_is_retained_and_two_more_passes_are_required(self):
        result,calls,records,pauses=self.execute([RuntimeError('fixture timeout'),good(),good()])
        self.assertEqual(calls,[1,2,3]);self.assertEqual(pauses,[10,10])
        self.assertFalse(result['attempts'][0]['passed']);self.assertEqual(result['attempts'][0]['consecutivePasses'],0)

    def test_repeated_failure_stops_after_three_checks(self):
        calls=[]
        def check(attempt):calls.append(attempt);raise RuntimeError('fixture failure')
        with self.assertRaises(RuntimeError):stable_llm_checks(check,lambda _:None,lambda _:None)
        self.assertEqual(calls,[1,2,3])

    def test_nonconsecutive_passes_are_rejected(self):
        with self.assertRaises(RuntimeError):self.execute([good(),RuntimeError('failure'),good()])

    def test_slow_or_disallowed_result_does_not_pass(self):
        for outcome in (good(20.01),good(float('nan')),good(True),{'passed':True,'turns':[]},
                        {'passed':True,'turns':[{'seconds':0.2,'allowed':False}]}):
            with self.subTest(outcome=outcome),self.assertRaises(RuntimeError):self.execute([outcome]*3)

    def test_outer_command_timeout_is_not_retried(self):
        import subprocess
        calls=[]
        def check(attempt):calls.append(attempt);raise subprocess.TimeoutExpired('synthetic check',120)
        with self.assertRaises(subprocess.TimeoutExpired):stable_llm_checks(check,lambda _:None,lambda _:None)
        self.assertEqual(calls,[1])


if __name__=='__main__':unittest.main()
