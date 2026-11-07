"""Verify the built signed APK, embedded server and required mobile feature bundle."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import zipfile

def verify(apk, previous, origin, sdk, java_home, version):
    env=os.environ.copy();env['JAVA_HOME']=str(java_home);env['PATH']=str(java_home/'bin')+os.pathsep+env['PATH']
    tools=sdk/'build-tools/37.0.0'
    def certificate(path):
        result=subprocess.check_output(['cmd.exe','/d','/c',str(tools/'apksigner.bat'),'verify','--print-certs',str(path)],env=env,encoding='utf-8')
        found=set(re.findall(r'certificate SHA-256 digest:\s*([0-9a-fA-F]+)',result))
        if len(found)!=1:raise RuntimeError('Exactly one verified signing certificate is required')
        return next(iter(found)).lower()
    def aapt(*args):return subprocess.check_output([str(tools/'aapt.exe'),*args],encoding='utf-8',errors='replace')
    meta=json.loads(apk.with_suffix('.apk.json').read_text(encoding='utf-8-sig'))
    badging=aapt('dump','badging',str(apk));manifest=aapt('dump','xmltree',str(apk),'AndroidManifest.xml')
    cert=certificate(apk);digest=hashlib.sha256(apk.read_bytes()).hexdigest()
    with zipfile.ZipFile(apk) as z:
        bundle=z.read('assets/index.android.bundle');config=json.loads(z.read('assets/app.config'))
        dex=b''.join(z.read(n) for n in z.namelist() if n.startswith('classes') and n.endswith('.dex'))
    checks={
        'sameCertificateAsPrevious':cert==certificate(previous)==meta.get('signingCertificate','').lower(),
        'checksumMatches':digest==meta.get('sha256','').lower(),
        'samePackage':"package: name='com.skincareai.mobile'" in badging and meta.get('package')=='com.skincareai.mobile',
        'expectedVersion':f"versionCode='{version}'" in badging and meta.get('versionCode')==version,
        'arm64Only':"native-code: 'arm64-v8a'" in badging,
        'serverBundled':meta.get('apiOrigin')==origin and origin.encode() in bundle,
        'httpsOnly':bool(re.search(r'android:usesCleartextTraffic\([^)]*\)=\(type 0x12\)0x0',manifest)),
        'publicProfile':config.get('extra',{}).get('buildProfile')=='public-demo' and 'networkSecurityConfig' not in manifest,
        'nativeOAuthCallback':'skincareai' in manifest and b'skincareai://oauth/callback' in bundle,
        'cameraPermission':'android.permission.CAMERA' in badging,
        'foregroundLocationPermission':'android.permission.ACCESS_FINE_LOCATION' in badging,
        'noBackgroundLocationOrAudio':all(n not in badging for n in ('android.permission.ACCESS_BACKGROUND_LOCATION','android.permission.RECORD_AUDIO')),
        'nativeSecureStorage':b'Lexpo/modules/securestore/SecureStoreModule;' in dex,
        'nativeCamera':b'Lexpo/modules/camera/CameraViewModule;' in dex,
        'guestApiBundled':b'/api/wc/auth/guest' in bundle and b'/api/wc/guest' in bundle,
        'guestPolicyVersion':meta.get('guestPolicyVersion')=='2026-11-04',
        'threeStageRiskUiBundled':all(value in bundle for value in (b'risk-summary',b'risk-step-',b'risk-badge')),
        'agreedRiskLabels':all(v.encode('utf-16-le') in bundle for v in ('관찰','경고','응급','판단 불가')),
        'captureAndSymptomFields':all(v.encode() in bundle for v in ('capturedOn','swelling','exudate','UNRECORDED')),
        'comparisonEncyclopediaAndDraft':all(v.encode('utf-16-le') in bundle for v in ('사진 비교','경과 기록 저장','피부·상처 백과','문의 초안 저장','예시 지도 체험')),
        'noSyntheticPatientFixture':b'synthetic-ui-fixture-token' not in bundle and b'qa-record-' not in bundle,
    }
    if version >= 11:
        checks['guestCurrentOnlyUiBundled'] = all(value.encode('utf-16-le') in bundle for value in (
            '비회원은 이번 분석만 확인해요', '이번 분석 이어서 보기', '비회원 자료 삭제·동의 관리'))
    if version >= 12:
        checks['signupConsentAndPrivacyUiBundled'] = all(value.encode('utf-16-le') in bundle for value in (
            '전체 동의 (선택 항목 포함)', '전체 동의 해제', '개인정보 설정', '주변 병원·약국 검색을 위한 위치정보 수집·이용'))
    result={'versionCode':version,'origin':origin,'certificateSha256':cert,'sha256':digest,'bytes':apk.stat().st_size,
            'minSdk':int(re.search(r"sdkVersion:'(\d+)'",badging)[1]),'checks':checks,'allPassed':all(checks.values()),
            'phoneInstalled':False,'publicDownloadVerified':False,'nativeFeatureFlowVerified':False}
    return result

if __name__=='__main__':
    p=argparse.ArgumentParser()
    for name in ('apk','previous','origin','sdk','java-home','output'):p.add_argument('--'+name,required=True)
    p.add_argument('--version',required=True,type=int);a=p.parse_args()
    r=verify(Path(a.apk),Path(a.previous),a.origin,Path(a.sdk),Path(a.java_home),a.version)
    Path(a.output).write_text(json.dumps(r,indent=2),encoding='utf-8');print(json.dumps(r))
    raise SystemExit(0 if r['allPassed'] else 1)
