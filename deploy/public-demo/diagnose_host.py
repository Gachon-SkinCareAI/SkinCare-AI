"""Read-only connectivity diagnostics for the isolated QA project; never reset a DB."""
import json
import subprocess
from verify_host import COMPOSE, ROOT

def capture(args):
    result = subprocess.run(args, cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                            text=True, encoding='utf-8', errors='replace', timeout=90)
    output = result.stdout
    # Extra protection for the private log: never retain generated secret values.
    for filename in ('db-root.txt', 'db-app.txt', 'ai-token.txt', 'jwt.txt'):
        value = (ROOT/'private'/filename).read_text().strip()
        output = output.replace(value, '[REDACTED]')
    return {'exitCode': result.returncode, 'output': output}

probe = r'''
import hashlib, json, os, re, socket, subprocess
from pathlib import Path
from db_runtime import ContainerInstance, root_password, option_password
db=ContainerInstance()
secret=root_password('/run/secrets/db_root')
config=Path('/state/private/root.cnf').read_text()
details={'configContainsExpectedPassword': ('password='+option_password(secret)+'\n') in config,
         'configMode': oct(Path('/state/private/root.cnf').stat().st_mode & 0o777),
         'rootSecretFingerprint': hashlib.sha256(secret.encode()).hexdigest(),
         'databaseAddresses': sorted({value[4][0] for value in socket.getaddrinfo('db',3306)})}
defaults=subprocess.run([str(db.bin/'mysql'),'--defaults-file=/state/private/root.cnf','--print-defaults'],capture_output=True,text=True,env=db.env())
details['defaultsExitCode']=defaults.returncode
details['defaultsContainRootUser']='--user=root' in defaults.stdout
details['defaultsContainPasswordOption']='--password=' in defaults.stdout
def check(args, env):
    result=subprocess.run(list(map(str,args)),input='SELECT 1;',capture_output=True,text=True,env=env)
    code=re.search(r'ERROR (\d+)',result.stderr)
    using=re.search(r'using password: (YES|NO)',result.stderr)
    denied=re.search(r"Access denied for user '([^']+)'@'([^']+)'",result.stderr)
    return {'exitCode':result.returncode, 'mysqlErrorCode':int(code.group(1)) if code else None,
            'deniedPrincipal': denied.groups() if denied else None,
            'usingPassword':using.group(1) if using else None, 'selectOnePassed':result.stdout.strip()=='1'}
details['optionFileConnection']=check(db.client()+['--batch','--skip-column-names'],db.env())
env=db.env();env['MYSQL_PWD']=secret
details['explicitCredentialConnection']=check([db.bin/'mysql','--no-defaults','--no-login-paths','--user=root','--host=db','--port=3306','--protocol=TCP','--ssl-mode=REQUIRED','--batch','--skip-column-names'],env)
print(json.dumps(details))
'''
query = "SELECT user,host,plugin,account_locked,ssl_type FROM mysql.user WHERE user='root' OR user=''; SELECT @@hostname,@@skip_name_resolve;"
root_command = 'MYSQL_PWD="$(cat /run/secrets/db_root)" mysql --protocol=TCP --host=127.0.0.1 --user=root --ssl-mode=REQUIRED --batch --execute="'+query+'"'
report = {
    'purpose': 'QA connectivity only; no schema/data/password changes',
    'rootAccounts': capture(COMPOSE+['exec','-T','db','sh','-c',root_command]),
    'databaseSecretFingerprint': capture(COMPOSE+['exec','-T','db','sh','-c','printf %s "$(cat /run/secrets/db_root)" | sha256sum']),
    'rootDbNetworkConnection': capture(COMPOSE+['exec','-T','db','sh','-c',root_command.replace('--host=127.0.0.1','--host=db')]),
    'maintenanceClient': capture(COMPOSE+['run','--rm','--no-deps','--entrypoint','python3','maintenance','-c',probe]),
    'aiHealth': capture(COMPOSE+['exec','-T','ai','python','/opt/demo/probe.py','ai']),
}
(ROOT/'connection-diagnostics.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print('Connectivity diagnostics saved. No database or password was changed.')
