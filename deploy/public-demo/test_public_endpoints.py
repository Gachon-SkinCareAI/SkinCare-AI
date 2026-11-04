import json
import unittest
import urllib.error

from run_free_demo import StopRequested, verify_public_endpoints


class PublicEndpointTests(unittest.TestCase):
    def setUp(self):
        self.now=0
        self.reports=[]
        self.calls=[]

    def sleep(self,seconds):
        self.now+=seconds

    def record(self,report):
        self.reports.append(json.loads(json.dumps(report)))

    def good_response(self,path,public):
        if path.startswith('/api/wc/auth/social/providers'):
            return 200,json.dumps([{'id':name,'enabled':True} for name in ('google','kakao','naver')])
        if path=='/api/wc/auth/consent-policy':return 200,'{"requiredAvailable":true}'
        if path.startswith('/api/wc/analyses'):return 401,'private response body'
        return 404,'private response body'

    def verify(self,get,check_stop=lambda:None):
        def tracked(path,public):
            self.calls.append((path,public))
            return get(path,public)
        return verify_public_endpoints(tracked,check_stop,self.record,timeout=5,
                                       clock=lambda:self.now,sleep=self.sleep)

    def test_all_local_and_public_contracts_are_required(self):
        checks=self.verify(self.good_response)
        self.assertEqual(len(checks),14)
        self.assertTrue(all(checks.values()))
        self.assertEqual(len(self.calls),14)
        self.assertTrue(self.reports[-1]['passed'])
        self.assertNotIn('private response body',json.dumps(self.reports))

    def test_temporary_gate_503_on_auth_check_retries_then_still_requires_401(self):
        first=True
        def get(path,public):
            nonlocal first
            if path=='/api/wc/analyses?probe=1' and not public and first:
                first=False
                return 503,'maintenance in progress'
            return self.good_response(path,public)
        self.verify(get)
        attempts=[a for a in self.reports[-1]['attempts'] if a['scope']=='local' and a['check']=='QueryDoesNotBypassAuth']
        self.assertEqual([a['status'] for a in attempts],[503,401])
        self.assertTrue(self.reports[-1]['passed'])

    def test_public_gateway_and_network_errors_are_bounded_and_recorded_without_secrets(self):
        errors=[urllib.error.URLError('private URL or token'),(530,'private gateway detail'),(502,''),(504,'')]
        def get(path,public):
            if public and errors:
                item=errors.pop(0)
                if isinstance(item,Exception):raise item
                return item
            return self.good_response(path,public)
        with self.assertRaisesRegex(RuntimeError,'publicProvidersEnabled: readiness timeout'):
            self.verify(get)
        final=self.reports[-1]
        self.assertFalse(final['passed'])
        self.assertEqual(self.now,5)
        self.assertEqual([a['status'] for a in final['attempts'] if a['scope']=='public'],[None,530,502,504])
        self.assertEqual(final['attempts'][-4]['errorType'],'URLError')
        self.assertNotIn('private URL or token',json.dumps(self.reports))
        self.assertNotIn('private gateway detail',json.dumps(self.reports))

    def test_persistent_503_cannot_be_marked_ready(self):
        with self.assertRaisesRegex(RuntimeError,'localProvidersEnabled: readiness timeout; expected HTTP 200, received 503'):
            self.verify(lambda path,public:(503,'never ready'))
        self.assertFalse(self.reports[-1]['passed'])
        self.assertEqual(self.now,5)
        self.assertEqual(len(self.calls),4)
        self.assertFalse(any(public for _,public in self.calls))

    def test_unprotected_analysis_is_immediate_failure_not_retry(self):
        def get(path,public):
            if path=='/api/wc/analyses?probe=1':return 200,'leaked account data'
            return self.good_response(path,public)
        with self.assertRaisesRegex(RuntimeError,'localQueryDoesNotBypassAuth: unexpected HTTP status; expected HTTP 401, received 200'):
            self.verify(get)
        self.assertEqual(self.now,0)
        self.assertFalse(self.reports[-1]['passed'])
        self.assertNotIn('leaked account data',json.dumps(self.reports))

    def test_private_ai_and_swagger_cannot_be_publicly_available(self):
        for path in ('/api/v1/ai/health','/v3/api-docs'):
            with self.subTest(path=path):
                def get(candidate,public):
                    return (200,'{}') if candidate==path and public else self.good_response(candidate,public)
                with self.assertRaisesRegex(RuntimeError,'expected HTTP 404, received 200'):
                    self.verify(get)
                self.assertFalse(self.reports[-1]['passed'])
                self.assertEqual(self.now,0)

    def test_invalid_provider_or_policy_contract_is_not_accepted(self):
        for path,body in (
            ('/api/wc/auth/social/providers','not json'),
            ('/api/wc/auth/social/providers','[]'),
            ('/api/wc/auth/social/providers',json.dumps([{'id':name,'enabled':name!='kakao'} for name in ('google','kakao','naver')])),
            ('/api/wc/auth/consent-policy','{"requiredAvailable":false}'),
            ('/api/wc/auth/consent-policy','{}'),
            ('/api/wc/auth/consent-policy','[]'),
        ):
            with self.subTest(path=path,body=body):
                def get(candidate,public):
                    return (200,body) if candidate==path else self.good_response(candidate,public)
                with self.assertRaisesRegex(RuntimeError,'invalid response contract'):
                    self.verify(get)
                self.assertFalse(self.reports[-1]['passed'])
                self.assertEqual(self.now,0)

    def test_stop_request_is_honored_before_network_request(self):
        def stop():raise StopRequested()
        with self.assertRaises(StopRequested):self.verify(self.good_response,stop)
        self.assertEqual(self.calls,[])
        self.assertFalse(self.reports[-1]['passed'])


if __name__=='__main__':unittest.main()
