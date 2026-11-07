"""Fail-closed publication gate plus Linux migration, expiry and managed backups."""
from datetime import datetime, timezone, timedelta
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import re
import threading
import time
import traceback
from db_runtime import ContainerInstance
from manage import digest
from retention import snapshots

os.umask(0o077)
DB = 'skincare_dev'
state = {'ok': False, 'last': 0.0}
ready_event = threading.Event()


def set_ready(ready):
    state.update(ok=ready, last=time.monotonic() if ready else 0.0)
    if ready:
        ready_event.set()
    else:
        ready_event.clear()


def run_maintenance_cycle(db, pause=time.sleep):
    set_ready(False)
    # Deadlocks and lock timeouts roll back on connection close. Retry the whole
    # idempotent cycle, keeping the readiness HTTP server alive and fail-closed.
    for attempt in range(3):
        try:
            result = db.maintain(DB)
            daily_backup(db)
            set_ready(True)
            return result
        except RuntimeError as error:
            code = re.search(r'MySQL command failed \(error (1205|1213)\)', str(error))
            if not code:
                raise
            print(json.dumps({'maintenance': 'retrying', 'mysqlErrorCode': int(code.group(1)),
                              'attempt': attempt + 1}), flush=True)
            if attempt < 2:
                pause(1)
    return None

class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass
    def do_GET(self):
        if self.path == '/ready':
            # Brief scheduled work may finish while this safe GET waits. Never
            # return a cached success while retention/backup is incomplete.
            ready_event.wait(timeout=10)
        ready = self.path == '/ready' and state['ok'] and time.monotonic() - state['last'] < 120
        body = json.dumps({'status': 'ok' if ready else 'unavailable'}).encode()
        self.send_response(200 if ready else 503)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Cache-Control', 'no-store')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

def daily_backup(db):
    with db.lock():
        previous = snapshots(db, DB)
        now = datetime.now(timezone.utc)
        if not previous or max(datetime.fromisoformat(meta['createdAt']) for _,_,meta in previous) < now-timedelta(days=1):
            db._backup_unlocked(DB)
            for path, meta_path, meta in previous:
                if datetime.fromisoformat(meta['createdAt']) < now-timedelta(days=7):
                    if digest(path) != meta['sha256']:
                        raise RuntimeError('Managed backup checksum changed')
                    path.unlink()
                    meta_path.unlink()

def main():
    server = ThreadingHTTPServer(('0.0.0.0', 9100), Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    phase = 'initialize'
    try:
        db = ContainerInstance()
        phase = 'database-identity'
        db.check_identity()
        phase = 'migration-check'
        if db.sql("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='skincare_dev' AND table_name='schema_migrations';") == '1':
            known = set(db.sql("SELECT version FROM schema_migrations WHERE status='APPLIED';", DB).splitlines())
            if {p.name for p in Path('/opt/migrations').glob('V*.sql')} - known:
                phase = 'pre-migration-backup'
                db.backup(DB)
        phase = 'schema-migration'
        db.migrate(DB, '/opt/migrations')
        while True:
            # Do not serve a public request while expiry/backup replacement is underway.
            phase = 'maintenance-cycle'
            result = run_maintenance_cycle(db)
            if result is not None:
                print(json.dumps({'maintenance': 'ok', **result}), flush=True)
            time.sleep(60 if result is not None else 5)
    except Exception as error:
        set_ready(False)
        # Only structural diagnostic fields; never SQL, credentials or raw exceptions.
        code = re.search(r'MySQL command failed \(error (\d+)\)', str(error))
        print(json.dumps({'maintenance': 'failed', 'phase': phase,
                          'exceptionType': type(error).__name__,
                          'mysqlErrorCode': int(code.group(1)) if code else None,
                          'trace': [{'file': Path(frame.filename).name, 'line': frame.lineno,
                                     'function': frame.name} for frame in traceback.extract_tb(error.__traceback__)]}), flush=True)
        raise SystemExit(1)

if __name__ == '__main__':
    main()
