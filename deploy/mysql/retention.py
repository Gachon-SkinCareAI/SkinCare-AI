"""Five-calendar-year retention for the managed local DB and its own backups only."""
from datetime import datetime, timezone
from pathlib import Path
import hashlib, json, calendar

# Operational datasets and model-training files are intentionally outside this scope.
PERSONAL_TIMES = {'refresh_tokens':'created_at','user_change_histories':'created_at','diagnosis_action_histories':'created_at','users':'created_at','analysis_sessions':'created_at','analysis_answers':'created_at',
    'inquiries':'created_at','inquiry_answers':'created_at','diagnoses':'created_at','recoveries':'created_at',
    'consent_events':'occurred_at','user_settings':'created_at','user_refresh_tokens':'created_at',
    'password_reset_tokens':'created_at','email_verification_tokens':'created_at',
    'admin_audit_events':'occurred_at','account_erasure_receipts':'requested_at','analysis_erasure_receipts':'requested_at'}
OWNED = [('inquiry_answers','inquiry_id IN (SELECT inquiry_id FROM inquiries WHERE user_id IN ({ids}))'),
    ('inquiries','user_id IN ({ids})'),('recoveries','diagnosis_id IN (SELECT diagnosis_id FROM diagnoses WHERE user_id IN ({ids}))'),
    ('diagnosis_metrics','diagnosis_id IN (SELECT diagnosis_id FROM diagnoses WHERE user_id IN ({ids}))'),
    ('diagnosis_action_histories','diagnosis_id IN (SELECT diagnosis_id FROM diagnoses WHERE user_id IN ({ids}))'),('diagnoses','user_id IN ({ids})'),('analysis_answers','session_id IN (SELECT id FROM analysis_sessions WHERE user_id IN ({ids}))'),
    ('analysis_sessions','user_id IN ({ids})'),('consent_events','user_id IN ({ids})'),('user_refresh_tokens','user_id IN ({ids})'),('user_settings','user_id IN ({ids})'),('refresh_tokens',"subject_id IN ({ids}) AND role='USER'"),('user_change_histories','user_id IN ({ids})')]

def unexpired_receipts(values,now):
    """Use collection-date anniversaries, including leap-day clamping, like MySQL DATE_ADD."""
    current=datetime.fromisoformat(now);result={}
    for key,when in values.items():
        received=datetime.fromisoformat(when)
        year=received.year+5
        anniversary=received.replace(year=year,day=min(received.day,calendar.monthrange(year,received.month)[1]))
        if anniversary>current:result[key]=when
    return result

def horizon(instance,name):
    tables=set(instance.sql('SHOW TABLES;',name).splitlines())
    queries=['SELECT MIN('+column+') AS collected FROM '+table for table,column in PERSONAL_TIMES.items() if table in tables]
    if not queries:return None
    value=instance.sql('SELECT DATE_ADD(MIN(collected), INTERVAL 5 YEAR) FROM ('+' UNION ALL '.join(queries)+') x;',name)
    return None if value=='NULL' else value

def expire(instance,name,dry_run=False):
    # Caller holds maintenance lock. Use DB time (Asia/Seoul), not a client-provided cutoff.
    from manage import identifier
    identifier(name)
    tables=set(instance.sql('SHOW TABLES;',name).splitlines())
    def due(column):return 'DATE_ADD('+column+', INTERVAL 5 YEAR) <= CURRENT_TIMESTAMP(6)'
    counts={table:int(instance.sql('SELECT COUNT(*) FROM '+table+' WHERE '+due(column)+';',name))
            for table,column in PERSONAL_TIMES.items() if table in tables}
    if dry_run:return counts
    # Lock only expired accounts. READ COMMITTED releases locks on nonmatching
    # rows during scans, so a live analysis does not block an empty expiry pass.
    # A locking scan can wait on a live account before evaluating its age, even
    # at READ COMMITTED. Build the expired-ID set with a nonlocking read first;
    # lock only those primary keys, in order, before erasing their owned rows.
    sql=['SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;',
         'SET SESSION innodb_lock_wait_timeout=5;', 'START TRANSACTION;',
         'CREATE TEMPORARY TABLE retention_users (id VARCHAR(36) PRIMARY KEY) ENGINE=InnoDB;',
         'INSERT INTO retention_users SELECT user_id FROM users WHERE '+due('created_at')+';']
    sql.append('SELECT u.user_id FROM retention_users r STRAIGHT_JOIN users u ON u.user_id=r.id ORDER BY u.user_id FOR UPDATE;')
    if 'account_erasure_receipts' in tables:
        sql.append('INSERT IGNORE INTO account_erasure_receipts(id,user_id,requested_at) SELECT UUID(),id,CURRENT_TIMESTAMP(6) FROM retention_users;')
    for token in ['password_reset_tokens','email_verification_tokens']:
        if token in tables:
            sql.append('DELETE FROM '+token+' WHERE email IN (SELECT u.email FROM users u JOIN retention_users r ON r.id=u.user_id) AND email NOT IN (SELECT u.email FROM users u WHERE DATE_ADD(u.created_at, INTERVAL 5 YEAR) > CURRENT_TIMESTAMP(6));')
    for table,condition in OWNED:
        if table in tables:sql.append('DELETE FROM '+table+' WHERE '+condition.format(ids='SELECT id FROM retention_users')+';')
    sql.append('DELETE FROM users WHERE user_id IN (SELECT id FROM retention_users);')
    # Independently collected/imported older records expire even on younger accounts.
    if 'analysis_sessions' in tables:
        if 'analysis_erasure_receipts' in tables:sql.append('INSERT IGNORE INTO analysis_erasure_receipts(id,session_id,requested_at) SELECT UUID(),id,CURRENT_TIMESTAMP(6) FROM analysis_sessions WHERE '+due('created_at')+';')
        sql += ['DELETE FROM analysis_answers WHERE session_id IN (SELECT id FROM analysis_sessions WHERE '+due('created_at')+');',
                'DELETE FROM analysis_sessions WHERE '+due('created_at')+';']
    if 'inquiries' in tables:
        sql += ['DELETE FROM inquiry_answers WHERE inquiry_id IN (SELECT inquiry_id FROM inquiries WHERE '+due('created_at')+');',
                'DELETE FROM inquiries WHERE '+due('created_at')+';']
    if 'diagnosis_action_histories' in tables:
        sql.append('DELETE FROM diagnosis_action_histories WHERE diagnosis_id IN (SELECT diagnosis_id FROM diagnoses WHERE '+due('created_at')+');')
    if 'diagnoses' in tables:
        sql += ['UPDATE inquiries SET diagnosis_id=NULL WHERE diagnosis_id IN (SELECT diagnosis_id FROM diagnoses WHERE '+due('created_at')+');',
                'DELETE FROM recoveries WHERE diagnosis_id IN (SELECT diagnosis_id FROM diagnoses WHERE '+due('created_at')+');',
                'DELETE FROM diagnosis_metrics WHERE diagnosis_id IN (SELECT diagnosis_id FROM diagnoses WHERE '+due('created_at')+');',
                'DELETE FROM diagnoses WHERE '+due('created_at')+';']
    for table,column in PERSONAL_TIMES.items():
        if table in tables and table not in ['users','analysis_sessions','inquiries','diagnoses']:
            sql.append('DELETE FROM '+table+' WHERE '+due(column)+';')
    for token in ['password_reset_tokens','email_verification_tokens']:
        if token in tables:sql.append('DELETE FROM '+token+' WHERE expiration_time <= CURRENT_TIMESTAMP(6);')
    sql += ['DROP TEMPORARY TABLE retention_users;','COMMIT;']
    # SELECT lock results remain private and are never logged.
    instance.sql('\n'.join(sql),name)
    return counts

def snapshots(instance,name):
    from manage import FORMAT
    folder=(instance.private/'backups').resolve();result=[]
    for candidate in folder.glob(name+'-*.sql'):
        path=candidate.resolve();meta_path=candidate.with_suffix('.json').resolve()
        if path.parent!=folder or meta_path.parent!=folder:raise RuntimeError('Backup path escaped the managed folder.')
        if not meta_path.is_file():raise RuntimeError('Incomplete managed backup requires review before retention can finish.')
        meta=json.loads(meta_path.read_text(encoding='utf-8'))
        if meta.get('format')!=FORMAT or meta.get('database')!=name:raise RuntimeError('Backup identity mismatch; no files removed.')
        result.append((path,meta_path,meta))
    return result

def maintain(instance,name):
    from manage import digest,identifier
    identifier(name);instance.check_identity()
    with instance.lock():
        counts=expire(instance,name)
        instance.export_erasures(name);instance.export_record_erasures(name)
        rows=[]
        for table,column in [('account_erasure_receipts','user_id'),('analysis_erasure_receipts','session_id')]:
            rows.append(instance.sql('SELECT HEX('+column+'),requested_at FROM '+table+' ORDER BY '+column+';',name))
        fingerprint=hashlib.sha256(json.dumps(rows).encode()).hexdigest()
        state_file=instance.private/(name+'-retention.json')
        previous=json.loads(state_file.read_text(encoding='utf-8')) if state_file.exists() else {}
        old=snapshots(instance,name)
        now=instance.sql('SELECT CURRENT_TIMESTAMP(6);',name)
        due=any('retentionUntil' not in m or (m['retentionUntil'] is not None and m['retentionUntil']<=now) for _,_,m in old)
        changed=previous.get('erasureFingerprint')!=fingerprint and (bool(previous) or any(rows))
        removed=0
        if old and (due or changed or any(counts.values())):
            # Replacement must finish before deleting any previous snapshot. Captured watermark
            # predates the dump: concurrent new erasures are processed on the next iteration.
            for path,_,meta in old:
                if digest(path)!=meta['sha256']:raise RuntimeError('Backup checksum mismatch; no backups removed.')
            instance._backup_unlocked(name)
            for path,meta_path,_ in old:
                # Each final resolved path was checked above. No recursive deletion.
                path.unlink();meta_path.unlink();removed+=1
        state={'format':'skincare-retention-v1','erasureFingerprint':fingerprint,'checkedAt':now,
               'expiredRows':sum(counts.values()),'removedBackups':removed,'periodYears':5}
        temp=state_file.with_suffix('.tmp');temp.write_text(json.dumps(state,indent=2),encoding='utf-8');temp.replace(state_file)
        return {k:v for k,v in state.items() if k!='erasureFingerprint'}
