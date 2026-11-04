"""Stage an explicit runtime allowlist. Never include datasets or copy a live database."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import subprocess

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[1]
RUNTIME_FILES = ('compose.yaml','compose.qa.yaml','Caddyfile','Caddyfile.qa','Dockerfile.backend',
    'Dockerfile.ai','Dockerfile.maintenance','requirements-serving.txt','.dockerignore',
    'db_runtime.py','maintenance.py','probe.py','serve_ai.py','db_selftest.py','verify_host.py','diagnose_host.py')
PROVIDERS = ('GOOGLE_CLIENT_ID','GOOGLE_SECRET','KAKAO_CLIENT_ID','KAKAO_CLIENT_SECRET','NAVER_CLIENT_ID','NAVER_CLIENT_SECRET')

def digest(path):
    value = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024*1024), b''):
            value.update(chunk)
    return value.hexdigest()

def valid_host(value):
    return bool(re.fullmatch(r'(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}', value)) and not value.endswith(('.invalid','.test','.example','.localhost')) and value not in ('example.com','example.org','example.net')

def protect(path):
    if os.name == 'nt':
        result = subprocess.check_output(['whoami','/user','/fo','csv','/nh'], text=True)
        sid = re.search(r'S-1-5-[\d-]+', result).group()
        subprocess.run(['icacls',str(path),'/inheritance:r','/grant:r','*'+sid+':(OI)(CI)F','*S-1-5-18:(OI)(CI)F'], check=True, capture_output=True)
    else:
        path.chmod(0o700)

def property_value(value):
    return value.replace('\\','\\\\').replace('\n','\\n').replace('\r','\\r').replace('\t','\\t').replace('=', '\\=').replace(':','\\:')

def write_secret(path, value):
    # Path.write_text() otherwise translates LF to CRLF on Windows. The MySQL
    # image treats that CR as part of MYSQL_ROOT_PASSWORD_FILE's password.
    Path(path).write_bytes(value.encode('ascii') + b'\n')

def prepare(args):
    if args.mode == 'public' and (not valid_host(args.hostname) or not re.fullmatch(r'[^\s@]+@[^\s@]+\.[^\s@]+',args.email)):
        raise ValueError('Public mode requires your real DNS hostname and TLS contact email')
    target = Path(args.output).resolve()
    if target == REPO or target.is_relative_to(REPO):
        raise ValueError('Keep the private bundle outside the source repository')
    if target.exists() and any(target.iterdir()):
        raise ValueError('Use a new empty output directory; existing secrets and data are never replaced')
    source = json.loads(Path(args.local_app_config).read_text(encoding='utf-8-sig'))
    target.mkdir(parents=True, exist_ok=True)
    protect(target)
    os.umask(0o077)
    for name in RUNTIME_FILES:
        shutil.copy2(HERE/name, target/name)
    for part in ('app','vision','qa','guide','tracking','jobs'):
        shutil.copytree(REPO/'ai'/part, target/'ai'/part,
            ignore=shutil.ignore_patterns('__pycache__','*.pyc','*.log','.env','tests'))
    for name in ('train_classify.py','eval_classify.py','config.yaml'):
        shutil.copy2(REPO/'ai'/name,target/'ai'/name)
    (target/'backend').mkdir()
    jar = REPO/'backend/build/libs/skincare-0.0.1-SNAPSHOT.jar'
    shutil.copy2(jar,target/'backend/app.jar')
    (target/'mysql').mkdir()
    for name in ('manage.py','retention.py'):
        shutil.copy2(REPO/'deploy/mysql'/name,target/'mysql'/name)
    shutil.copytree(REPO/'backend/src/main/resources/db/mysql',target/'migrations')
    (target/'models').mkdir()
    model_files = {}
    for key, name in [('detector','detector.pt'),('classifier','classifier.pt'),('energy','energy.json')]:
        original = Path(source[key])
        expected = digest(original)
        copied = target/'models'/name
        shutil.copy2(original,copied)
        if digest(copied) != expected or digest(original) != expected:
            raise ValueError('Model changed while packaging')
        model_files[name] = {'sha256':expected,'bytes':copied.stat().st_size}
    (target/'models/manifest.json').write_text(json.dumps({'source':'unchanged configured baseline','files':model_files},indent=2),encoding='utf-8')
    private = target/'private'
    private.mkdir(mode=0o700)
    values = {name: secrets.token_hex(32) for name in ('db-root','db-app','ai-token','jwt')}
    for name,value in values.items():
        write_secret(private/(name+'.txt'), value)
    config = {'DB_PASSWORD':values['db-app'],'JWT_SECRET':values['jwt'],'AI_SERVICE_TOKEN':values['ai-token'],
              'SKINCARE_BOOTSTRAP_ADMIN_ID':'demo_admin','SKINCARE_BOOTSTRAP_ADMIN_PASSWORD':'Demo9!'+secrets.token_hex(20)}
    if args.mode == 'public':
        original_private = Path(source['runtime'])/'private'
        social = json.loads((original_private/'social-config.json').read_text(encoding='utf-8-sig'))
        service = json.loads((original_private/'service-config.json').read_text(encoding='utf-8-sig'))
        for key in PROVIDERS:
            if not social.get(key):
                raise ValueError('Missing provider setting: '+key)
            config[key] = social[key]
        config['PUBLIC_MEDICAL_SERVICE_KEY'] = service.get('PUBLIC_MEDICAL_SERVICE_KEY','')
    else:
        config.update({key:'' for key in PROVIDERS})
        config['PUBLIC_MEDICAL_SERVICE_KEY'] = ''
    (private/'application-secrets.properties').write_text('\n'.join(key+'='+property_value(value) for key,value in config.items())+'\n',encoding='utf-8')
    host = args.hostname if args.mode == 'public' else 'demo.invalid'
    email = args.email if args.mode == 'public' else 'qa@example.invalid'
    (target/'.env').write_text('PUBLIC_HOST='+host+'\nTLS_EMAIL='+email+'\n',encoding='utf-8')
    project = 'skincare-demo-qa-'+secrets.token_hex(4) if args.mode == 'qa' else 'skincare-public-demo'
    manifest = {'format':'skincare-public-demo-bundle-v1','mode':args.mode,'project':project,
                'publicHost':host,'baselineModels':model_files,'databaseCopied':False,
                'providerSecretsIncluded':args.mode == 'public','backendSha256':digest(target/'backend/app.jar'),
                'sources':{name:digest(target/name) for name in RUNTIME_FILES}}
    (target/'bundle-manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
    print(json.dumps({'prepared':True,'mode':args.mode,'project':project,'modelBytes':sum(v['bytes'] for v in model_files.values()),'databaseCopied':False}))

if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',required=True)
    parser.add_argument('--local-app-config',required=True)
    parser.add_argument('--mode',choices=('qa','public'),default='qa')
    parser.add_argument('--hostname',default='')
    parser.add_argument('--email',default='')
    prepare(parser.parse_args())
