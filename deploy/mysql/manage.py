"""Private loopback MySQL development instance. Never edits an existing service/data directory."""
from pathlib import Path
from contextlib import contextmanager
from datetime import datetime,timezone
import argparse,hashlib,json,os,re,secrets,socket,subprocess,time

FORMAT='skincare-mysql-development-v1'
HIDDEN=getattr(subprocess,'CREATE_NO_WINDOW',0)
def digest(path):
    value=hashlib.sha256()
    with Path(path).open('rb') as stream:
        for chunk in iter(lambda:stream.read(1024*1024),b''):value.update(chunk)
    return value.hexdigest()
def identifier(value):
    if not re.fullmatch(r'skincare_(?:dev|qa_[a-z0-9_]+|restore_[a-z0-9_]+|schema_[a-z0-9_]+)',value):
        raise RuntimeError('Use skincare_dev or a skincare_qa_/restore_/schema_ name.')
    if len(value)>64:raise RuntimeError('Database name is too long.')
    return '`'+value+'`'
def hidden(args,**kwargs):return subprocess.run(list(map(str,args)),creationflags=HIDDEN,**kwargs)

class Instance:
    def __init__(self,runtime):
        self.root=Path(runtime).resolve();self.marker=self.root/'instance.json'
        if not self.marker.exists():raise RuntimeError('Run init for a new, empty runtime directory first.')
        self.config=json.loads(self.marker.read_text(encoding='utf-8'))
        if self.config.get('format')!=FORMAT or Path(self.config['runtime']).resolve()!=self.root:
            raise RuntimeError('This is not the expected SkinCare development runtime.')
        self.bin=Path(self.config['mysql_home'])/'bin';self.port=int(self.config['port'])
        self.private=self.root/'private';self.data=self.root/'data'
    @property
    def credentials(self):return json.loads((self.private/'credentials.json').read_text(encoding='utf-8'))
    def env(self):
        env=os.environ.copy()
        env['MYSQL_TEST_LOGIN_FILE']=str(self.private/'unused-login-path.cnf')
        env['MYSQL_HISTFILE']=os.devnull
        return env
    def client(self,exe='mysql.exe',account='root'):
        return [self.bin/exe,'--defaults-file='+str(self.private/(account+'.cnf')),
                '--protocol=TCP','--host=127.0.0.1','--port='+str(self.port),
                '--user='+('root' if account=='root' else 'skincare_app'),'--ssl-mode=REQUIRED']
    def sql(self,sql,database=None,account='root'):
        args=self.client(account=account)+['--batch','--raw','--skip-column-names','--binary-mode','--default-character-set=utf8mb4']
        if database:identifier(database);args.append(database)
        result=hidden(args,input=sql.encode('utf-8'),stdout=subprocess.PIPE,stderr=subprocess.PIPE,env=self.env())
        if result.returncode:
            # Statements may contain passwords or clinical text. Do not print failed SQL or stderr.
            code=re.search(rb'ERROR (\d+)',result.stderr)
            raise RuntimeError('MySQL command failed'+(' (error '+code.group(1).decode()+')' if code else '')+'. SQL and credentials omitted.')
        return result.stdout.decode('utf-8').strip()
    def check_identity(self):
        data=self.sql('SELECT @@datadir;')
        if Path(data).resolve()!=self.data.resolve():raise RuntimeError('Refusing to operate on an unrelated MySQL data directory.')
    def running(self):
        with socket.socket() as probe:
            probe.settimeout(.5);connected=probe.connect_ex(('127.0.0.1',self.port))==0
        if connected:self.check_identity()
        return connected
    def start(self):
        if self.running():return False
        init_file=self.private/'first-start.sql'
        args=[self.bin/'mysqld.exe','--defaults-file='+str(self.root/'my.ini'),'--console']
        if init_file.exists():args.append('--init-file='+init_file.as_posix())
        with (self.root/'server.log').open('ab') as log:
            process=subprocess.Popen(list(map(str,args)),stdin=subprocess.DEVNULL,stdout=log,stderr=subprocess.STDOUT,env=self.env(),creationflags=HIDDEN)
        deadline=time.monotonic()+60
        while time.monotonic()<deadline:
            if process.poll() is not None:raise RuntimeError('MySQL exited; inspect the private runtime server.log.')
            try:
                self.check_identity()
                if init_file.exists():init_file.unlink()
                return True
            except RuntimeError:time.sleep(.25)
        raise RuntimeError('MySQL startup timed out; no existing service was stopped.')
    def stop(self):
        if not self.running():return
        result=hidden(self.client('mysqladmin.exe')+['shutdown'],stdout=subprocess.PIPE,stderr=subprocess.PIPE,env=self.env())
        if result.returncode:raise RuntimeError('Graceful shutdown failed; no process was forcibly terminated.')
        deadline=time.monotonic()+30
        while time.monotonic()<deadline:
            with socket.socket() as s:
                if s.connect_ex(('127.0.0.1',self.port))!=0:return
            time.sleep(.25)
        raise RuntimeError('MySQL shutdown has not completed.')
    def create_database(self,name):
        self.check_identity();quoted=identifier(name)
        self.sql('CREATE DATABASE IF NOT EXISTS '+quoted+' CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;')
    def grant_app(self,name):
        self.sql('GRANT SELECT, INSERT, UPDATE, DELETE ON '+identifier(name)+".* TO 'skincare_app'@'localhost';")
    @contextmanager
    def lock(self):
        # All managed migrations/backups/restores share one local instance lock.
        import msvcrt
        with (self.root/'maintenance.lock').open('a+b') as lock:
            lock.seek(0);lock.write(b'1');lock.flush();lock.seek(0)
            try:msvcrt.locking(lock.fileno(),msvcrt.LK_NBLCK,1)
            except OSError:raise RuntimeError('Another maintenance command is running.')
            try:yield
            finally:lock.seek(0);msvcrt.locking(lock.fileno(),msvcrt.LK_UNLCK,1)
    def migrate(self,name,migrations):
        identifier(name);self.check_identity()
        files=sorted(Path(migrations).glob('V[0-9]*__*.sql'))
        if not files:raise RuntimeError('No versioned schema migrations found.')
        with self.lock():
            self.create_database(name)
            tables=self.sql('SHOW TABLES;',name).splitlines()
            if tables and 'schema_migrations' not in tables:
                raise RuntimeError('Populated unversioned DB: refusing automatic baseline. Existing legacy migrations need separate review.')
            self.sql('CREATE TABLE IF NOT EXISTS schema_migrations (version VARCHAR(100) PRIMARY KEY, checksum CHAR(64) NOT NULL, status VARCHAR(20) NOT NULL, applied_at TIMESTAMP(6) NULL) ENGINE=InnoDB;',name)
            rows=[row.split('\t') for row in self.sql('SELECT version,checksum,status FROM schema_migrations;',name).splitlines() if row]
            if any(row[2]!='APPLIED' for row in rows):raise RuntimeError('An incomplete migration is recorded. Review the partial schema before any further change.')
            ledger={row[0]:row[1] for row in rows}
            if not ledger and any(t!='schema_migrations' for t in tables):
                raise RuntimeError('Untracked tables found. A prior migration may be incomplete; manual review required.')
            known={p.name for p in files}
            if set(ledger)-known:raise RuntimeError('The DB has migrations not known to this checkout.')
            applied=[]
            for path in files:
                checksum=digest(path)
                if path.name in ledger:
                    if ledger[path.name]!=checksum:raise RuntimeError('Migration checksum changed: '+path.name)
                    continue
                if not re.fullmatch(r'V\d+__[A-Za-z0-9_]+\.sql',path.name):raise RuntimeError('Invalid migration filename.')
                self.sql("INSERT INTO schema_migrations(version,checksum,status) VALUES ('"+path.name+"','"+checksum+"','STARTED');",name)
                self.sql(path.read_text(encoding='utf-8')+"\nUPDATE schema_migrations SET status='APPLIED',applied_at=CURRENT_TIMESTAMP(6) WHERE version='"+path.name+"';",name)
                applied.append(path.name)
            self.grant_app(name)
            return applied
    def export_erasures(self,name):
        identifier(name)
        if self.sql("SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='"+name+"';")!='1':
            raise RuntimeError('Authoritative source DB is missing. Verify a current erasure ledger before restoring personal data.')
        ledger=self.private/'erasure-ledger.json'
        saved=json.loads(ledger.read_text(encoding='utf-8')) if ledger.exists() else {'format':'skincare-erasure-ledger-v1','users':{}}
        if saved.get('format')!='skincare-erasure-ledger-v1' or not isinstance(saved.get('users'),dict):raise RuntimeError('Invalid private erasure ledger.')
        exists=self.sql("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='"+name+"' AND table_name='account_erasure_receipts';")=='1'
        if exists:
            for line in self.sql('SELECT HEX(user_id),requested_at FROM account_erasure_receipts;',name).splitlines():
                user_hex,when=line.split('\t');user=bytes.fromhex(user_hex).decode('utf-8')
                saved['users'][user]=when
        from retention import unexpired_receipts
        saved['users']=unexpired_receipts(saved['users'],self.sql('SELECT CURRENT_TIMESTAMP(6);',name))
        for user in saved['users']:
            if not isinstance(user,str) or not 1<=len(user)<=36:raise RuntimeError('Invalid erasure identifier.')
        temp=ledger.with_suffix('.tmp');temp.write_text(json.dumps(saved,ensure_ascii=False,indent=2),encoding='utf-8');temp.replace(ledger)
        return saved['users']
    def apply_erasures(self,name,users):
        # Only newly restored databases may be filtered here. No development DB deletion command.
        identifier(name)
        if not name.startswith('skincare_restore_'):raise RuntimeError('Erasure replay requires an isolated restore target.')
        tables=set(self.sql('SHOW TABLES;',name).splitlines());statements=['START TRANSACTION;']
        pairs=[('inquiry_answers','inquiry_id IN (SELECT inquiry_id FROM inquiries WHERE user_id={})'),
            ('inquiries','user_id={}'),('recoveries','diagnosis_id IN (SELECT diagnosis_id FROM diagnoses WHERE user_id={})'),
            ('diagnosis_metrics','diagnosis_id IN (SELECT diagnosis_id FROM diagnoses WHERE user_id={})'),('diagnosis_action_histories','diagnosis_id IN (SELECT diagnosis_id FROM diagnoses WHERE user_id={})'),('diagnoses','user_id={}'),
            ('analysis_answers','session_id IN (SELECT id FROM analysis_sessions WHERE user_id={})'),('analysis_sessions','user_id={}'),
            ('consent_events','user_id={}'),('user_refresh_tokens','user_id={}'),('user_settings','user_id={}'),('refresh_tokens',"subject_id={} AND role='USER'"),('user_change_histories','user_id={}'),('users','user_id={}')]
        for user in users:
            key="CONVERT(0x"+user.encode('utf-8').hex()+" USING utf8mb4)"
            if 'users' in tables:
                for token_table in ['password_reset_tokens','email_verification_tokens']:
                    if token_table in tables:
                        statements.append('DELETE FROM '+token_table+' WHERE email IN (SELECT u.email FROM users u WHERE u.user_id='+key+' AND NOT EXISTS (SELECT 1 FROM users other WHERE other.email=u.email AND other.user_id<>u.user_id));')
            for table,condition in pairs:
                if table in tables:statements.append('DELETE FROM '+table+' WHERE '+condition.format(key)+';')
            if 'account_erasure_receipts' in tables:
                statements.append('INSERT IGNORE INTO account_erasure_receipts(id,user_id,requested_at) VALUES (UUID(),'+key+',CURRENT_TIMESTAMP);')
        statements.append('COMMIT;');self.sql('\n'.join(statements),name)
        return len(users)
    def suspend_restored_training(self,name):
        # A snapshot can predate a withdrawal. Until fresh opt-in, restored data must not be eligible.
        identifier(name)
        if not name.startswith('skincare_restore_'):raise RuntimeError('Training reset requires an isolated restore target.')
        columns=set(tuple(row.split('\t')) for row in self.sql(
            "SELECT table_name,column_name FROM information_schema.columns WHERE table_schema='"+name+"';").splitlines())
        statements=['START TRANSACTION;']
        if ('users','training_granted') in columns:
            statements.append('UPDATE users SET training_granted=false;')
        if ('analysis_sessions','training_eligible') in columns:
            statements.append("UPDATE analysis_sessions SET training_eligible=false,training_review_status='RECONSENT_REQUIRED' WHERE training_eligible=true;")
        if ('diagnoses','training_use') in columns:
            statements.append('UPDATE diagnoses SET training_use=false;')
        statements.append('COMMIT;');self.sql('\n'.join(statements),name)

    def export_record_erasures(self,name):
        identifier(name)
        # Source must still exist: a stale file alone must never authorize a restore.
        if self.sql("SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='"+name+"';")!='1':raise RuntimeError('Authoritative source DB is missing.')
        ledger=self.private/(name+'-record-erasures.json')
        saved=json.loads(ledger.read_text(encoding='utf-8')) if ledger.exists() else {'format':'skincare-record-erasures-v1','records':{}}
        if saved.get('format')!='skincare-record-erasures-v1' or not isinstance(saved.get('records'),dict):raise RuntimeError('Invalid record erasure ledger.')
        tables=set(self.sql('SHOW TABLES;',name).splitlines())
        if 'analysis_erasure_receipts' in tables:
            for row in self.sql('SELECT HEX(session_id),requested_at FROM analysis_erasure_receipts;',name).splitlines():
                key,when=row.split('\t');saved['records'][bytes.fromhex(key).decode('utf-8')]=when
        from retention import unexpired_receipts
        saved['records']=unexpired_receipts(saved['records'],self.sql('SELECT CURRENT_TIMESTAMP(6);',name))
        for key in saved['records']:
            if not isinstance(key,str) or not 1<=len(key)<=36:raise RuntimeError('Invalid record erasure identifier.')
        temp=ledger.with_suffix('.tmp');temp.write_text(json.dumps(saved,indent=2),encoding='utf-8');temp.replace(ledger)
        return saved['records']
    def apply_record_erasures(self,name,records):
        identifier(name)
        if not name.startswith('skincare_restore_'):raise RuntimeError('Record erasure replay requires an isolated restore target.')
        tables=set(self.sql('SHOW TABLES;',name).splitlines());queries=['START TRANSACTION;']
        for key in records:
            value="CONVERT(0x"+key.encode('utf-8').hex()+" USING utf8mb4)"
            if 'analysis_answers' in tables:queries.append('DELETE FROM analysis_answers WHERE session_id='+value+';')
            if 'analysis_sessions' in tables:queries.append('DELETE FROM analysis_sessions WHERE id='+value+';')
        queries.append('COMMIT;');self.sql('\n'.join(queries),name)
    def maintain(self,name):
        from retention import maintain
        return maintain(self,name)
    def backup(self,name):
        identifier(name);self.check_identity()
        with self.lock():
            return self._backup_unlocked(name)
    def _backup_unlocked(self,name):
        self.export_erasures(name)
        if self.sql("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='"+name+"' AND table_type='BASE TABLE' AND engine<>'InnoDB';")!='0':
            raise RuntimeError('Consistent backup requires all application tables to use InnoDB.')
        folder=self.private/'backups';folder.mkdir(exist_ok=True)
        path=folder/(name+'-'+datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S')+'-'+secrets.token_hex(3)+'.sql')
        from retention import horizon
        retention_until=horizon(self,name)
        args=self.client('mysqldump.exe')+['--single-transaction','--quick','--hex-blob','--no-tablespaces','--set-gtid-purged=OFF','--column-statistics=0','--skip-add-drop-table','--default-character-set=utf8mb4',name]
        with path.open('xb') as output:
            result=hidden(args,stdout=output,stderr=subprocess.PIPE,env=self.env())
        if result.returncode:raise RuntimeError('Backup failed; incomplete file retained without a manifest.')
        meta={'retentionUntil':retention_until,'format':FORMAT,'database':name,'sha256':digest(path),'bytes':path.stat().st_size,'createdAt':datetime.now(timezone.utc).isoformat()}
        path.with_suffix('.json').write_text(json.dumps(meta,indent=2),encoding='utf-8')
        return path,meta
    def restore(self,backup,name):
        identifier(name)
        if not name.startswith('skincare_restore_'):raise RuntimeError('Restore requires a new skincare_restore_ database; source DB is preserved.')
        path=Path(backup).resolve()
        if not path.is_relative_to((self.private/'backups').resolve()):raise RuntimeError('Use a backup from this managed instance.')
        meta=json.loads(path.with_suffix('.json').read_text(encoding='utf-8'))
        if meta.get('format')!=FORMAT or meta.get('sha256')!=digest(path):raise RuntimeError('Backup checksum verification failed.')
        self.check_identity()
        with self.lock():
            erased=self.export_erasures(meta['database'])
            records=self.export_record_erasures(meta['database'])
            exists=self.sql("SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='"+name+"';")
            if exists!='0':raise RuntimeError('Restore target already exists. It will not be overwritten.')
            self.create_database(name)
            # Dumps created here contain no CREATE DATABASE / USE statements.
            with path.open('rb') as source:
                result=hidden(self.client()+['--binary-mode','--default-character-set=utf8mb4',name],stdin=source,stdout=subprocess.PIPE,stderr=subprocess.PIPE,env=self.env())
            if result.returncode:raise RuntimeError('Restore failed. Partial target retained for review; source DB untouched.')
            self.suspend_restored_training(name)
            self.apply_erasures(name,erased)
            self.apply_record_erasures(name,records)
            from retention import expire
            expire(self,name)
            # Re-read the authoritative source after import to include erasures during restore.
            self.apply_erasures(name,self.export_erasures(meta['database']))
            self.apply_record_erasures(name,self.export_record_erasures(meta['database']))
            self.grant_app(name)
            return {**meta,'erasure_filter_applied':True,'erasure_ids_checked':len(erased),'record_erasure_ids_checked':len(records),'retention_filter_applied':True}

def initialize(runtime,mysql_home,port):
    root=Path(runtime).resolve();home=Path(mysql_home).resolve()
    if not 1024<=port<=65535:raise RuntimeError('Use an unprivileged TCP port.')
    if not (home/'bin/mysqld.exe').is_file():raise RuntimeError('MySQL installation not found.')
    if root.exists() and any(root.iterdir()):raise RuntimeError('Initialization requires a new empty directory. Existing data is never reinitialized.')
    with socket.socket() as probe:probe.bind(('127.0.0.1',port))
    root.mkdir(parents=True,exist_ok=True)
    identity=hidden(['whoami','/user','/fo','csv','/nh'],capture_output=True,text=True,check=True).stdout
    sid=re.search(r'S-1-5-[\d-]+',identity).group()
    hidden(['icacls',root,'/inheritance:r','/grant:r','*'+sid+':(OI)(CI)F','*S-1-5-18:(OI)(CI)F'],capture_output=True,check=True)
    private=root/'private';private.mkdir()
    creds={'root':secrets.token_hex(32),'app':secrets.token_hex(32),'jwt':secrets.token_hex(32),'ai':secrets.token_hex(32)}
    (private/'credentials.json').write_text(json.dumps(creds),encoding='utf-8')
    for account in ['root','app']:
        (private/(account+'.cnf')).write_text('[client]\nuser='+('root' if account=='root' else 'skincare_app')+'\npassword='+creds[account]+'\nhost=127.0.0.1\nport='+str(port)+'\nprotocol=TCP\n',encoding='utf-8')
    config={'format':FORMAT,'runtime':str(root),'mysql_home':str(home),'port':port}
    (root/'instance.json').write_text(json.dumps(config,indent=2),encoding='utf-8')
    (root/'my.ini').write_text('[mysqld]\nbasedir="'+home.as_posix()+'"\ndatadir="'+(root/'data').as_posix()+'"\nbind-address=127.0.0.1\nport='+str(port)+'\nmysqlx=0\nskip-log-bin\ncharacter-set-server=utf8mb4\ncollation-server=utf8mb4_0900_ai_ci\nmax-allowed-packet=64M\ninnodb-buffer-pool-size=128M\ndefault-time-zone=+09:00\npid-file="'+(root/'mysql.pid').as_posix()+'"\n',encoding='utf-8')
    with (root/'initialization.log').open('wb') as log:
        result=hidden([home/'bin/mysqld.exe','--no-defaults','--initialize-insecure','--basedir='+str(home),'--datadir='+str(root/'data'),'--console'],stdout=log,stderr=subprocess.STDOUT)
    if result.returncode:raise RuntimeError('Initialization failed; data retained and not retried automatically.')
    (private/'first-start.sql').write_text("ALTER USER 'root'@'localhost' IDENTIFIED BY '"+creds['root']+"';\nCREATE USER 'skincare_app'@'localhost' IDENTIFIED BY '"+creds['app']+"';\n",encoding='utf-8')
    return Instance(root)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command',choices=['init','start','stop','status','migrate','backup','restore','retention','retention-check'])
    parser.add_argument('--runtime',required=True)
    parser.add_argument('--mysql-home',default=r'C:\Program Files\MySQL\MySQL Server 8.0')
    parser.add_argument('--port',type=int,default=13306)
    parser.add_argument('--database',default='skincare_dev')
    parser.add_argument('--migrations',default=str(Path(__file__).resolve().parents[2]/'backend/src/main/resources/db/mysql'))
    parser.add_argument('--backup')
    args=parser.parse_args()
    if args.command=='init':instance=initialize(args.runtime,args.mysql_home,args.port);instance.start();print('Private MySQL instance initialized and started.');return
    instance=Instance(args.runtime)
    if args.command=='start':instance.start();print('MySQL ready on 127.0.0.1:'+str(instance.port))
    elif args.command=='stop':instance.stop();print('Managed MySQL stopped; data preserved.')
    elif args.command=='status':print(json.dumps({'running':instance.running(),'port':instance.port}))
    elif args.command=='migrate':print(json.dumps({'applied':instance.migrate(args.database,args.migrations)}))
    elif args.command=='backup':path,meta=instance.backup(args.database);print(json.dumps({'backup':str(path),'sha256':meta['sha256']}))
    elif args.command=='retention':print(json.dumps(instance.maintain(args.database)))
    elif args.command=='retention-check':
        from retention import expire
        instance.check_identity()
        with instance.lock():print(json.dumps(expire(instance,args.database,dry_run=True)))
    elif args.command=='restore':instance.restore(args.backup,args.database);print('Restored into new database '+args.database)
if __name__=='__main__':
    try:main()
    except (RuntimeError,OSError,ValueError) as error:raise SystemExit(str(error))
