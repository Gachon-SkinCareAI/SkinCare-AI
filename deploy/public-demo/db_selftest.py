"""Isolated container-only DB tests. Never edits the serving skincare_dev tables."""
import json
import secrets
from db_runtime import ContainerInstance
from retention import expire

db = ContainerInstance()
name = 'skincare_qa_' + secrets.token_hex(5)
restored = 'skincare_restore_' + secrets.token_hex(5)
db.migrate(name, '/opt/migrations')
assert db.sql('SELECT 1;', name, account='app') == '1'
denied = False
try:
    db.sql('CREATE TABLE forbidden_test(id INT);', name, account='app')
except RuntimeError:
    denied = True
assert denied, 'Application account must not own schema'
db.sql("INSERT INTO account_erasure_receipts(id,user_id,requested_at) VALUES ('qa-receipt','qa-user',CURRENT_TIMESTAMP(6));", name)
path, meta = db.backup(name)
assert path.is_file() and meta['sha256']
db.sql("INSERT INTO analysis_erasure_receipts(id,session_id,requested_at) VALUES ('qa-record-receipt','qa-session',CURRENT_TIMESTAMP(6));", name)
result = db.restore(path, restored)
assert result['erasure_filter_applied'] and result['record_erasure_ids_checked'] == 1
counts = expire(db, restored, dry_run=True)
assert all(value == 0 for value in counts.values())
print(json.dumps({'schemaMigration': True, 'crudAccountCannotCreateTable': True,
                  'managedBackupAndRestore': True, 'postBackupErasureLedgerApplied': True,
                  'retentionDryRun': True, 'servingDatabaseMutated': False,
                  'qaDatabase': name, 'restoreDatabase': restored}))
