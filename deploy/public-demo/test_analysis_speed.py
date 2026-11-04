import unittest
from diagnose_analysis_speed import timing_records,llm_timings,vision_timings,backend_timings


class SpeedDiagnosticTests(unittest.TestCase):
    def test_extracts_only_allowlisted_latency_fields(self):
        raw='''ai | {"path":"/api/v1/ai/predict","status":200,"latency_ms":22000,"request_id":"private-id","symptom":"private text"}
ai | {"path":"/other/private/path","latency_ms":100}
ai | {bad json}
ai | {"path":"/api/v1/ai/question","latency_ms":"not a number"}'''
        self.assertEqual(timing_records(raw),[{'path':'/api/v1/ai/predict','status':200,'latency_ms':22000}])

    def test_llm_times_do_not_include_ip_or_prompt(self):
        raw='''llm | [GIN] timestamp | 200 | 21.5s | private-ip | POST "/api/chat"
llm | [GIN] timestamp | 200 | 350ms | private-ip | POST "/api/chat"
llm | [GIN] timestamp | 200 | 1m2.5s | private-ip | POST "/api/chat"
llm | [GIN] timestamp | 200 | 350µs | private-ip | GET "/api/ps"
private text'''
        self.assertEqual(llm_timings(raw),[{'status':200,'seconds':21.5},{'status':200,'seconds':0.35},{'status':200,'seconds':62.5}])

    def test_rejects_nonfinite_durations_and_unstructured_status(self):
        raw='''{"path":"/api/v1/ai/predict","status":"private text","latency_ms":12}
{"path":"/api/v1/ai/predict","status":200,"latency_ms":NaN}
{"path":"/api/v1/ai/predict","status":200,"latency_ms":true}
{"path":"/api/v1/ai/predict","status":200,"latency_ms":-1}'''
        self.assertEqual(timing_records(raw),[])

    def test_vision_stage_records_exclude_patient_fields_and_invalid_numbers(self):
        raw='''ai | {"event":"vision_timing","total_ms":230,"quality_ms":10,"detector_ms":120,"classifier_ms":100,"request_id":"private id","device":"cpu","label":"private label"}
ai | {"event":"vision_warmup","detector_ms":400,"classifier_ms":30,"synthetic_input_only":true}
ai | {"event":"vision_load","elapsed_ms":Infinity,"device":"private text"}
ai | {"event":"vision_timing","quality_ms":true,"total_ms":-1}'''
        self.assertEqual(vision_timings(raw),[
            {'event':'vision_timing','total_ms':230,'quality_ms':10,'detector_ms':120,'classifier_ms':100,'device':'cpu'},
            {'event':'vision_warmup','detector_ms':400,'classifier_ms':30,'synthetic_input_only':True}])

    def test_backend_records_are_metadata_only(self):
        raw='''backend | timestamp skincare.performance : event=ai_request operation=predict completed=true elapsed_ms=250
backend | timestamp skincare.performance : event=analysis_request operation=create status=201 completed=true elapsed_ms=32000
backend | event=ai_request operation=question completed=false elapsed_ms=20001
backend | event=analysis_request operation=private-text status=200 completed=true elapsed_ms=10
backend | event=ai_request operation=predict completed=true elapsed_ms=10 private-payload
backend | private account, token and image'''
        self.assertEqual(backend_timings(raw),[
            {'event':'ai_request','operation':'predict','completed':True,'elapsed_ms':250},
            {'event':'analysis_request','operation':'create','status':201,'completed':True,'elapsed_ms':32000},
            {'event':'ai_request','operation':'question','completed':False,'elapsed_ms':20001}])


if __name__=='__main__':unittest.main()
