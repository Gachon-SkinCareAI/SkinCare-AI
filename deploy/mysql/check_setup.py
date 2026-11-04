"""Read-only local setup inspection. Never prints secret values or contacts providers."""
from pathlib import Path
from urllib.parse import urlsplit
import argparse,json

def https(value):
    if not isinstance(value,str) or not value.strip() or len(value.strip())>1000:return False
    try:
        p=urlsplit(value.strip())
        return p.scheme.lower()=='https' and bool(p.hostname) and not p.username and not p.password and p.port!=0 and not p.path.startswith('/api/wc/policy-previews/') and not any(c.isspace() for c in value.strip())
    except ValueError:return False

def inspect(config_path):
    checks=[]
    def add(id,label,status,required=True):checks.append({'id':id,'label':label,'status':status,'required':required})
    def read(path,allowed=None):
        if not path.is_file():return {}
        try:
            d=json.loads(path.read_text(encoding='utf-8-sig'))
            if not isinstance(d,dict) or (allowed is not None and (set(d)-allowed or any(not isinstance(v,str) for v in d.values()))):raise ValueError()
            return d
        except (OSError,ValueError):return None
    def result():return {'mode':'read-only configuration inspection','externalConnectionsVerified':False,'releaseApproved':False,'checks':checks,'missingRequired':sum(c['required'] and c['status']!='CONFIGURED' for c in checks)}
    config=read(Path(config_path))
    if not config:
        add('app-config','로컬 앱 설정','INVALID' if config is None else 'MISSING');return result()
    runtime=config.get('runtime')
    if not isinstance(runtime,str) or not runtime:
        add('runtime','MySQL 런타임 경로','INVALID');return result()
    root=Path(runtime);add('runtime','MySQL 인스턴스 표식','CONFIGURED' if (root/'instance.json').is_file() else 'MISSING')
    for name,label in [('python','Python 실행 파일'),('detector','기존 탐지 모델 파일'),('classifier','기존 분류 모델 파일'),('energy','기존 UNKNOWN 기준 파일')]:
        value=config.get(name);add(name,label,'CONFIGURED' if isinstance(value,str) and Path(value).is_file() else 'MISSING')
    java=config.get('java_home');add('java','Java 실행 파일','CONFIGURED' if isinstance(java,str) and (Path(java)/'bin/java.exe').is_file() else 'MISSING')
    social_allowed={'GOOGLE_CLIENT_ID','GOOGLE_SECRET','KAKAO_CLIENT_ID','KAKAO_CLIENT_SECRET','NAVER_CLIENT_ID','NAVER_CLIENT_SECRET','SOCIAL_PUBLIC_BASE_URL','SOCIAL_REDIRECT_URIS','SKINCARE_WEB_ALLOWED_ORIGINS'}
    service_allowed={'PUBLIC_MEDICAL_SERVICE_KEY','TERMS_POLICY_URL','PRIVACY_POLICY_URL','TRAINING_POLICY_URL','SKINCARE_CLINICAL_REVIEWERS','SKINCARE_BUILTIN_POLICIES'}
    social=read(root/'private/social-config.json',social_allowed);services=read(root/'private/service-config.json',service_allowed)
    for name,values in [('social-config',social),('service-config',services)]:
        if values is None:add(name,'비공개 설정 파일 형식','INVALID')
    social=social or {};services=services or {}
    for provider,a,b in [('google','GOOGLE_CLIENT_ID','GOOGLE_SECRET'),('kakao','KAKAO_CLIENT_ID','KAKAO_CLIENT_SECRET'),('naver','NAVER_CLIENT_ID','NAVER_CLIENT_SECRET')]:
        pair=[bool(social.get(key,'').strip()) for key in [a,b]]
        add(provider,provider+' 소셜 로그인','CONFIGURED' if all(pair) else 'INCOMPLETE' if any(pair) else 'MISSING')
    for id,key,label,required in [('terms','TERMS_POLICY_URL','이용약관 HTTPS 문서',True),('privacy','PRIVACY_POLICY_URL','개인정보·건강정보 HTTPS 문서',True),('training','TRAINING_POLICY_URL','선택 학습 HTTPS 문서',False)]:
        value=services.get(key,'');builtin=services.get('SKINCARE_BUILTIN_POLICIES','').lower()=='true' and id in ['terms','privacy']
        base=social.get('SOCIAL_PUBLIC_BASE_URL','http://127.0.0.1:'+str(config.get('api_port',9800)))
        try:
            u=urlsplit(base);local=u.scheme=='http' and u.hostname in ['localhost','127.0.0.1','::1'];valid=(https(base) or local) and not u.username and not u.password and u.port!=0 and u.path in ['', '/'] and not u.query and not u.fragment
        except ValueError:valid=False
        add(id,label,'CONFIGURED' if (builtin and valid) or (not builtin and https(value)) else 'INVALID' if value.strip() or builtin else 'MISSING',required)
    add('medical','공공 의료기관 키','CONFIGURED' if services.get('PUBLIC_MEDICAL_SERVICE_KEY','').strip() else 'MISSING')
    add('reviewers','임상 검수 담당자 지정','CONFIGURED' if services.get('SKINCARE_CLINICAL_REVIEWERS','').strip() else 'MISSING',False)
    add('clinical-release','전문가·운영 검토','PENDING_REVIEW',False)
    return result()

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--config',required=True);args=parser.parse_args()
    try:report=inspect(args.config)
    except (OSError,ValueError,TypeError):report={'mode':'read-only configuration inspection','checks':[{'id':'config','status':'INVALID'}],'missingRequired':1,'externalConnectionsVerified':False,'releaseApproved':False}
    print(json.dumps(report,ensure_ascii=False,indent=2));return 2 if report['missingRequired'] else 0
if __name__=='__main__':raise SystemExit(main())
