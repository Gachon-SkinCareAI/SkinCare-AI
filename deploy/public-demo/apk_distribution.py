"""Create a small public APK install directory and an actual, decodable QR.

No signing key, token or account data is copied. Publishing is a separate host action.
"""
import argparse
import hashlib
import html
import json
from pathlib import Path
import re
import shutil
import zipfile

PUBLIC_FILES = {'index.html','SkinCare-AI.apk','install-qr.png','release.json'}

def validate_apk(apk, origin, expected_certificate):
    if not re.fullmatch(r'https://[a-z0-9]+(?:-[a-z0-9]+)*\.trycloudflare\.com',origin):
        raise ValueError('Use the current verified HTTPS tunnel origin')
    meta=json.loads(apk.with_suffix('.apk.json').read_text(encoding='utf-8-sig'))
    digest=hashlib.sha256(apk.read_bytes()).hexdigest()
    if meta.get('apiOrigin') != origin or meta.get('package') != 'com.skincareai.mobile' or meta.get('sha256','').lower()!=digest:
        raise ValueError('APK metadata, checksum or server mismatch')
    if meta.get('signingCertificate','').lower() != expected_certificate.lower():
        raise ValueError('Update signing identity changed')
    with zipfile.ZipFile(apk) as archive:
        bundle=archive.read('assets/index.android.bundle')
        if origin.encode() not in bundle: raise ValueError('APK contains another server')
    return meta

def prepare(apk, origin, target, expected_certificate):
    import qrcode
    meta=validate_apk(apk,origin,expected_certificate)
    if target.exists() and any(target.iterdir()): raise ValueError('Use an empty staging directory')
    target.mkdir(parents=True,exist_ok=True)
    url=origin+'/download/'
    qr=qrcode.QRCode(error_correction=qrcode.constants.ERROR_CORRECT_M,box_size=10,border=4)
    qr.add_data(url);qr.make(fit=True);qr.make_image(fill_color='black',back_color='white').save(target/'install-qr.png')
    shutil.copy2(apk,target/'SkinCare-AI.apk')
    release={k:meta[k] for k in ('versionCode','package','apiOrigin','sha256','signingCertificate')}
    release.update(downloadUrl=url+'SkinCare-AI.apk',installUrl=url,bytes=apk.stat().st_size,architecture='arm64-v8a')
    (target/'release.json').write_text(json.dumps(release,ensure_ascii=False,indent=2),encoding='utf-8')
    text=f'''<!doctype html><html lang="ko"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="robots" content="noindex,nofollow"><title>SkinCare AI 앱 설치</title>
<style>body{{margin:0;background:#f4f6f9;color:#0d1b3e;font:16px/1.7 system-ui,sans-serif}}main{{max-width:500px;margin:32px auto;padding:28px;background:white;border-radius:24px}}h1{{margin:4px 0}}a{{color:#0d2b6b}}.install{{display:block;text-align:center;background:#0d2b6b;color:white;padding:16px;border-radius:14px;text-decoration:none;font-weight:bold}}img{{display:block;width:240px;max-width:100%;margin:20px auto}}small,code{{overflow-wrap:anywhere}}li{{margin-bottom:12px}}@media(max-width:560px){{main{{margin:16px;padding:22px}}}}</style>
<main><small>연구·시연용 · Android</small><h1>SkinCare AI</h1><p>사진 분석과 상처 경과 기록을 휴대전화에서 확인하세요.</p>
<a class="install" href="SkinCare-AI.apk" download="SkinCare-AI.apk">앱 다운로드 · 버전 {meta['versionCode']}</a>
<p>약 {apk.stat().st_size/1024/1024:.1f}MB · ARM64 기기용</p><ol><li>휴대전화 카메라로 QR을 읽고 이 설치 페이지를 여세요.</li><li>APK를 내려받아 여세요. Android가 요청하면 지금 사용한 브라우저 또는 파일 앱에만 ‘이 출처 허용’을 직접 선택하세요.</li><li>기존 SkinCare AI가 있으면 업데이트를 선택하세요. 계정과 기록을 유지하기 위해 앱을 지우지 마세요.</li></ol>
<p>보호 기능이 설치를 막으면 경고 내용을 확인하고 관리자에게 알려 주세요. 보호 기능 전체를 끄지 마세요.</p><img src="install-qr.png" alt="SkinCare AI 설치 페이지 QR 코드">
<p>시연 PC와 Docker가 켜져 있어야 서비스에 연결돼요. 임시 주소가 바뀌면 새 설치 링크를 받아 주세요.</p>
<p>문의: <a href="mailto:wanjang0320@gachon.ac.kr">wanjang0320@gachon.ac.kr</a></p><small>파일 SHA-256<br><code>{html.escape(meta['sha256'].lower())}</code></small>
<p><a href="https://developer.android.com/distribute/marketing-tools/alternative-distribution">Android 공식 설치·배포 안내</a></p></main></html>'''
    (target/'index.html').write_text(text,encoding='utf-8')
    if {p.name for p in target.iterdir()}!=PUBLIC_FILES: raise ValueError('Unexpected public file')
    return release

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--apk',required=True);parser.add_argument('--origin',required=True);parser.add_argument('--output',required=True);parser.add_argument('--certificate',required=True)
    args=parser.parse_args()
    print(json.dumps(prepare(Path(args.apk),args.origin,Path(args.output),args.certificate)))
