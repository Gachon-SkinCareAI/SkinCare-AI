"""MySQL 7->8 migration regression on a new synthetic QA database only."""
import json
from pathlib import Path
import secrets
import shutil
import tempfile
from db_runtime import ContainerInstance

db = ContainerInstance()
name = 'skincare_qa_' + secrets.token_hex(5)
with tempfile.TemporaryDirectory() as folder:
    for file in Path('/opt/migrations').glob('V*.sql'):
        if not file.name.startswith('V008__'):
            shutil.copy2(file, Path(folder) / file.name)
    db.migrate(name, folder)
db.sql("""INSERT INTO users(user_id,nickname,email,plan,provider,status,agreed_terms,agreed_privacy,created_at)
VALUES ('qa-user','Synthetic','synthetic@example.test','FREE','GOOGLE','ACTIVE',0,0,'2025-01-01');
INSERT INTO analysis_sessions(id,user_id,client_request_id,case_id,status,version,turn,image,image_type,slots_json,created_at,progress_note)
VALUES ('qa-photo','qa-user','qa-request','qa-photo','CREATED',0,0,X'010203','image/png','{}','2025-01-01','Synthetic old note');""", name)
db.migrate(name, '/opt/migrations')
assert db.sql("SELECT CONCAT(HEX(image),'|',DATE(created_at),'|',progress_note,'|',captured_on IS NULL,'|',swelling IS NULL,'|',exudate IS NULL) FROM analysis_sessions WHERE id='qa-photo';", name) == '010203|2025-01-01|Synthetic old note|1|1|1'
db.sql("UPDATE analysis_sessions SET captured_on='2024-12-31',swelling='PRESENT',exudate='UNKNOWN',pain_score=4 WHERE id='qa-photo';", name, account='app')
assert db.sql("SELECT CONCAT(captured_on,'|',swelling,'|',exudate,'|',pain_score) FROM analysis_sessions WHERE id='qa-photo';", name) == '2024-12-31|PRESENT|UNKNOWN|4'
assert db.migrate(name, '/opt/migrations') == []
print(json.dumps({'passed': True, 'oldPhotoAndCollectionDatePreserved': True, 'oldDatesRemainUnknown': True,
                  'symptomsRoundTrip': True, 'migrationReplaySafe': True, 'qaDatabase': name, 'servingDatabaseMutated': False}))
