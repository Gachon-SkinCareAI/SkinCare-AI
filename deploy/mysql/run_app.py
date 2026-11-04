"""Run the local demo against the managed MySQL DB. Existing H2 and MySQL services are untouched."""
from pathlib import Path
from functools import partial
from http.server import SimpleHTTPRequestHandler,ThreadingHTTPServer
import argparse,json,os,socket,subprocess,threading,time,urllib.request,zipfile,uuid
from manage import Instance,HIDDEN

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--config',required=True);parser.add_argument('--verify-once',action='store_true');parser.add_argument('--launch-id',default=str(uuid.uuid4()));args=parser.parse_args()
    config=json.loads(Path(args.config).read_text(encoding='utf-8'))
    repo=Path(__file__).resolve().parents[2];runtime=Path(config['runtime']).resolve();instance=Instance(runtime)
    app=runtime/'app';app.mkdir(exist_ok=True);status=app/'status.json';stop=app/'stop.request';backend_stop=app/'backend-stop.request';ai_stop=app/'ai-stop.request'
    java=Path(config['java_home'])/'bin';python=Path(config['python']);children=[];logs=[];web=None;owns_mysql=False;failure=None
    ports={'api':int(config.get('api_port',9800)),'ai':int(config.get('ai_port',8000)),'web':int(config.get('web_port',8081))}
    def record(state,**extras):
        temporary=status.with_suffix('.tmp');temporary.write_text(json.dumps({'state':state,'launchId':args.launch_id,'database':'MySQL','schema':'skincare_dev','port':instance.port,'url':f'http://127.0.0.1:{ports["web"]}','launcherPid':os.getpid(),'children':[{'name':n,'pid':p.pid} for n,p in children],**extras},ensure_ascii=False,indent=2),encoding='utf-8');temporary.replace(status)
    def start(name,command,env):
        log=(app/(name+'.log')).open('ab');logs.append(log)
        process=subprocess.Popen(list(map(str,command)),cwd=app,env=env,stdin=subprocess.DEVNULL,stdout=log,stderr=subprocess.STDOUT,creationflags=HIDDEN);children.append((name,process));return process
    def request(url,body=None,token=None):
        headers={}
        if token:headers['Authorization']='Bearer '+token
        if body is not None:headers['Content-Type']='application/json'
        req=urllib.request.Request(url,data=None if body is None else json.dumps(body).encode(),headers=headers)
        with urllib.request.urlopen(req,timeout=3) as response:return json.load(response)
    def ready(check,seconds=120):
        deadline=time.monotonic()+seconds
        while time.monotonic()<deadline:
            if stop.exists():raise RuntimeError('Start cancelled.')
            for name,process in children:
                if process.poll() is not None:raise RuntimeError(name+' stopped during startup; see private app logs.')
            try:
                result=check()
                if result:return result
            except (OSError,ValueError):pass
            time.sleep(.5)
        raise RuntimeError('Application startup timed out; see private app logs.')
    try:
        for port in ports.values():
            with socket.socket() as probe:probe.bind(('127.0.0.1',port))
        for flag in [stop,backend_stop,ai_stop]:
            if flag.exists():flag.unlink()
        record('starting');owns_mysql=instance.start()
        # Preserve the existing DB before applying any pending versioned migration.
        has_ledger=instance.sql("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='skincare_dev' AND table_name='schema_migrations';").strip()=='1'
        if has_ledger:
            applied=set(instance.sql("SELECT version FROM schema_migrations WHERE status='APPLIED';",'skincare_dev').splitlines())
            pending={p.name for p in (repo/'backend/src/main/resources/db/mysql').glob('V*.sql')}-applied
            if pending:instance.backup('skincare_dev')
        instance.migrate('skincare_dev',repo/'backend/src/main/resources/db/mysql')
        retention_status=instance.maintain('skincare_dev')
        boot=app/'backend';jar=repo/'backend/build/libs/skincare-0.0.1-SNAPSHOT.jar';stamp=str(jar.stat().st_mtime_ns);marker=boot/'source-stamp.txt'
        if not marker.exists() or marker.read_text()!=stamp:
            boot.mkdir(exist_ok=True)
            with zipfile.ZipFile(jar) as archive:archive.extractall(boot)
            marker.write_text(stamp)
        classpath=';'.join(map(str,[boot/'BOOT-INF/classes',boot/'BOOT-INF/lib/*',app]))
        compile_result=subprocess.run([str(java/'javac.exe'),'-encoding','UTF-8','-cp',classpath,'-d',str(app),str(repo/'deploy/mysql/DevelopmentLauncher.java')],capture_output=True,creationflags=HIDDEN)
        if compile_result.returncode:raise RuntimeError('Development launcher compilation failed.')
        creds=instance.credentials;env=os.environ.copy();env.update({'SERVER_ADDRESS':'127.0.0.1','SERVER_PORT':str(ports['api']),'SPRING_PROFILES_ACTIVE':'mysql',
            'DB_CLASS_NAME':'com.mysql.cj.jdbc.Driver','DB_URL':f'jdbc:mysql://127.0.0.1:{instance.port}/skincare_dev?sslMode=REQUIRED&serverTimezone=Asia/Seoul&characterEncoding=UTF-8',
            'DB_USERNAME':'skincare_app','DB_PASSWORD':creds['app'],'JWT_SECRET':creds['jwt'],'AI_SERVICE_TOKEN':creds['ai'],
            'AI_BASE_URL':f'http://127.0.0.1:{ports["ai"]}',
            'MAIL_USERNAME':'local-unconfigured','MAIL_PASSWORD':'local-unconfigured','SPRING_MAIL_HOST':'127.0.0.1','SPRING_MAIL_PORT':'9',
            'LOCAL_STOP_PATH':str(backend_stop),'PYTHONPATH':str(repo/'ai'),'PYTHONDONTWRITEBYTECODE':'1',
            'AI_DETECT_PATH':config['detector'],'AI_CLASSIFY_PATH':config['classifier'],'AI_ENERGY_PATH':config['energy'],
            'YOLO_CONFIG_DIR':str(app/'yolo'),'MPLCONFIGDIR':str(app/'mpl')})
        env.setdefault('SOCIAL_PUBLIC_BASE_URL',f'http://127.0.0.1:{ports["api"]}')
        env.setdefault('SOCIAL_REDIRECT_URIS',f'http://127.0.0.1:{ports["web"]}/,http://localhost:{ports["web"]}/,skincareai://oauth/callback')
        social_file=runtime/'private/social-config.json'
        if social_file.exists():
            social=json.loads(social_file.read_text(encoding='utf-8-sig'))
            allowed={'GOOGLE_CLIENT_ID','GOOGLE_SECRET','KAKAO_CLIENT_ID','KAKAO_CLIENT_SECRET','NAVER_CLIENT_ID','NAVER_CLIENT_SECRET','SOCIAL_PUBLIC_BASE_URL','SOCIAL_REDIRECT_URIS','SKINCARE_WEB_ALLOWED_ORIGINS'}
            if not isinstance(social,dict) or set(social)-allowed or any(not isinstance(v,str) for v in social.values()):
                raise RuntimeError('Invalid private/social-config.json format; use the example file.')
            env.update(social)
        service_file=runtime/'private/service-config.json'
        if service_file.exists():
            services=json.loads(service_file.read_text(encoding='utf-8-sig'))
            allowed={'PUBLIC_MEDICAL_SERVICE_KEY','TERMS_POLICY_URL','PRIVACY_POLICY_URL','TRAINING_POLICY_URL','SKINCARE_CLINICAL_REVIEWERS','SKINCARE_BUILTIN_POLICIES'}
            if not isinstance(services,dict) or set(services)-allowed or any(not isinstance(v,str) for v in services.values()):
                raise RuntimeError('Invalid private/service-config.json format; use the example file.')
            env.update(services)
        start('ai',[python,'-B',repo/'deploy/mysql/serve_ai.py','--port',str(ports['ai']),'--stop-file',ai_stop],env)
        start('backend',[java/'java.exe','-Duser.timezone=Asia/Seoul','-cp',classpath,'DevelopmentLauncher'],env)
        ready(lambda:request(f'http://127.0.0.1:{ports["ai"]}/api/v1/ai/health',token=creds['ai']).get('status')=='ok')
        providers=ready(lambda:request(f'http://127.0.0.1:{ports["api"]}/api/wc/auth/social/providers'))
        if {p['id'] for p in providers}!={'google','kakao','naver'}:raise RuntimeError('Unexpected social provider response.')
        class Handler(SimpleHTTPRequestHandler):
            def log_message(self,*args):pass
            def end_headers(self):self.send_header('Cache-Control','no-store');super().end_headers()
            def do_GET(self):
                if self.path=='/__local_runtime':
                    data=json.dumps({'database':'MySQL','launchId':args.launch_id}).encode();self.send_response(200);self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(data)));self.end_headers();self.wfile.write(data)
                else:super().do_GET()
        web=ThreadingHTTPServer(('127.0.0.1',ports['web']),partial(Handler,directory=str(repo/'mobile/dist')))
        threading.Thread(target=web.serve_forever,daemon=True).start();record('ready',ai='baseline ready',schema_mode='validate',retention=retention_status)
        print('MySQL application ready on '+f'http://127.0.0.1:{ports["web"]}',flush=True)
        if args.verify_once:
            with urllib.request.urlopen(f'http://127.0.0.1:{ports["web"]}',timeout=3) as response:assert response.status==200
            (app/'launch-verification.json').write_text(json.dumps({'passed':True,'database':'MySQL','schema_mode':'validate','social_providers':providers,'real_social_login_verified':False,'web':True,'ai_baseline_ready':True}),encoding='utf-8')
            return
        maintenance_at=time.monotonic()+60
        while not stop.exists():
            if time.monotonic()>=maintenance_at:
                retention_status=instance.maintain('skincare_dev')
                record('ready',ai='baseline ready',schema_mode='validate',retention=retention_status)
                maintenance_at=time.monotonic()+60
            for name,process in children:
                if process.poll() is not None:raise RuntimeError(name+' stopped; see private app logs.')
            time.sleep(1)
    except Exception as error:failure=str(error)
    finally:
        if web:web.shutdown();web.server_close()
        backend_stop.touch();ai_stop.touch()
        for name,process in reversed(children):
            if process.poll() is None:
                try:process.wait(timeout=40)
                except subprocess.TimeoutExpired:
                    failure=failure or name+' did not finish graceful shutdown.'
                    process.terminate();process.wait(timeout=10)
        for log in logs:log.close()
        if owns_mysql and instance.running():instance.stop()
        record('error' if failure else 'stopped',error=failure)
    if failure:raise SystemExit(failure)
if __name__=='__main__':main()
