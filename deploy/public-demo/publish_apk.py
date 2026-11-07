"""Publish only the verified APK install assets on the current existing HTTPS tunnel."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import time
import traceback
import urllib.error
import urllib.parse
import urllib.request
from apk_distribution import PUBLIC_FILES
from run_free_demo import HERE, HIDDEN, checksum, compose_command, instance_lock, json_write

def read_json(path):
    # Deployment writers use UTF-8; PowerShell may add its UTF-8 BOM.
    # Never interpret these files using the Windows ANSI code page.
    try:return json.loads(path.read_text(encoding='utf-8-sig'))
    except (UnicodeError, ValueError) as error:
        error.input_file=path.name
        raise

def ready_response(url, timeout=30):
    # Maintenance briefly closes its gate while applying scheduled erasures.
    # Only anonymous read-only GETs are retried; persistent 404s are not hidden.
    for attempt in range(8):
        try:return urllib.request.urlopen(url,timeout=timeout)
        except urllib.error.HTTPError as error:
            if error.code not in (502,503,504,530) or attempt==7:raise
        except urllib.error.URLError:
            if attempt==7:raise
        time.sleep(3)

def validate(source, origin, verification):
    if {p.name for p in source.iterdir()} != PUBLIC_FILES or any(not p.is_file() or p.is_symlink() for p in source.iterdir()):
        raise RuntimeError('Unexpected public asset set')
    release=read_json(source/'release.json')
    checked=read_json(verification)
    if release.get('apiOrigin') != origin or checked.get('origin') != origin or not checked.get('allPassed'):
        raise RuntimeError('APK verification or current origin mismatch')
    if checksum(source/'SkinCare-AI.apk') != release['sha256'].lower() or release['sha256'].lower()!=checked['sha256'].lower():
        raise RuntimeError('Public APK checksum mismatch')
    if release['signingCertificate'].lower()!=checked['certificateSha256'].lower() or release['versionCode']!=checked['versionCode']:
        raise RuntimeError('Package identity mismatch')
    return release

def publish(args, attempt):
    config=read_json(Path(args.config))
    root=Path(config['runtime']);out=Path(config['output']);bundle=root/'bundle'
    connection=read_json(out/'public-connection.json');origin=connection['origin']
    if not connection.get('active') or urllib.parse.urlsplit(origin).scheme!='https':raise RuntimeError('Start the verified public server first')
    source=Path(args.assets).resolve(); release=validate(source,origin,Path(args.verification))
    marker=bundle/'bundle-manifest.json';manifest=read_json(marker)
    if not manifest.get('progressRelease'): raise RuntimeError('Deploy capture-date/symptom API before distributing this app')
    compose_command(bundle,manifest)  # Validate the existing bundle before any writes.
    if args.check_only:
        return {'passed':True,'step':'preflight-complete','preflightOnly':True,'versionCode':release['versionCode'],'origin':origin}
    operation=root/'apk-distribution';operation.mkdir(exist_ok=True)
    report={'passed':False,'origin':origin,'versionCode':release['versionCode'],'sha256':release['sha256'],'qrUrl':origin+'/download/','databaseChanged':False}
    def status(step):
        report['step']=step;attempt.update(report);json_write(out/'apk-distribution.json',report);print(step,flush=True)
    def command(args,name,timeout=90):
        status(name);result=subprocess.run(args,cwd=bundle,capture_output=True,timeout=timeout,creationflags=HIDDEN)
        (operation/(name+'.log')).write_bytes(result.stdout+result.stderr)
        if result.returncode:raise RuntimeError(name+' failed')
    def current():
        c=read_json(out/'public-connection.json')
        if not c.get('active') or c.get('origin')!=origin or (root/'stop.request').exists():raise RuntimeError('Public server changed')
    with instance_lock(operation):
        current(); backup=operation/('before-'+str(time.time_ns()));backup.mkdir()
        saved_manifest=marker.read_bytes()
        names=['Caddyfile.tunnel','compose.distribution.yaml']
        for name in names:
            if (bundle/name).exists():shutil.copy2(bundle/name,backup/name)
        old_assets=bundle/'distribution'
        if old_assets.exists():shutil.copytree(old_assets,backup/'distribution')
        try:
            # A dedicated directory contains exactly the four public files.
            if old_assets.exists() and any(p.name not in PUBLIC_FILES for p in old_assets.iterdir()):raise RuntimeError('Distribution contains unmanaged files')
            attempt['publicationAttempted']=True
            old_assets.mkdir(exist_ok=True)
            for name in PUBLIC_FILES:shutil.copy2(source/name,old_assets/name)
            shutil.copy2(HERE/'Caddyfile.tunnel',bundle/'Caddyfile.tunnel')
            host=urllib.parse.urlsplit(origin).hostname
            overlay={'services':{'edge':{'environment':{'DISTRIBUTION_HOST':host},'volumes': ['./distribution:/srv/distribution:ro']}}}
            # Compose merges volumes by target; the existing Caddy and data mounts remain intact.
            (bundle/'compose.distribution.yaml').write_text(json.dumps(overlay,indent=2),encoding='utf-8')
            manifest['distributionEnabled']=True
            for name in names:manifest['sources'][name]=checksum(bundle/name)
            compose=compose_command(bundle,manifest)
            command(compose+['run','--rm','--no-deps','-T','--entrypoint','caddy','edge','validate','--config','/etc/caddy/Caddyfile','--adapter','caddyfile'],'validate-download-route')
            current();command(compose+['up','-d','--no-deps','--force-recreate','--wait','--wait-timeout','60','edge'],'publish-download-route')
            status('verify-public-download')
            with ready_response(origin+'/download/release.json') as response:
                if json.load(response)!=release:raise RuntimeError('Public release metadata mismatch')
            with ready_response(origin+'/download/') as response:
                if 'SkinCare-AI.apk' not in response.read().decode():raise RuntimeError('Install page missing')
            digest=hashlib.sha256();size=0
            with ready_response(origin+'/download/SkinCare-AI.apk',timeout=45) as response:
                for chunk in iter(lambda:response.read(1024*1024),b''):
                    size+=len(chunk);digest.update(chunk)
                    if size>release['bytes']:raise RuntimeError('Unexpected APK size')
            if size!=release['bytes'] or digest.hexdigest()!=release['sha256'].lower():raise RuntimeError('Public APK differs from the signed build')
            for suffix in ('private/application-secrets.properties','backend/app.jar','not-published.txt'):
                try:ready_response(origin+'/download/'+suffix,timeout=15)
                except urllib.error.HTTPError as error:
                    if error.code!=404:raise
                else:raise RuntimeError('Non-public file is accessible')
            current();manifest['distributionRelease']=release;json_write(marker,manifest)
            report.update(passed=True,fullApkDownloadVerified=True,installUrl=origin+'/download/',bytes=size)
            status('complete');print('Install URL: '+origin+'/download/',flush=True)
        except Exception as error:
            report.update(errorType=type(error).__name__,failedStep=report.get('step'))
            for name in names:
                if (backup/name).exists():shutil.copy2(backup/name,bundle/name)
            if (backup/'distribution').exists():
                for name in PUBLIC_FILES:shutil.copy2(backup/'distribution'/name,old_assets/name)
            marker.write_bytes(saved_manifest)
            try:current();command(compose_command(bundle,json.loads(saved_manifest))+['up','-d','--no-deps','--force-recreate','edge'],'restore-previous-edge')
            except Exception:report['edgeRestoreFailed']=True
            status('failed');raise
    return report

def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config',required=True);parser.add_argument('--assets',required=True);parser.add_argument('--verification',required=True)
    parser.add_argument('--check-only',action='store_true',help='Validate local assets and config without Docker or runtime changes')
    parser.add_argument('--diagnostic-output',help='Private attempt report, including failures before Docker starts')
    args=parser.parse_args(argv)
    attempt={'passed':False,'step':'preflight','publicationAttempted':False}
    try:
        attempt.update(publish(args,attempt))
        if args.check_only:print('Publication preflight passed. No runtime or public files were changed.',flush=True)
    except Exception as error:
        attempt.update(passed=False,errorType=type(error).__name__,failedStep=attempt['step'])
        if hasattr(error,'input_file'):attempt['inputFile']=error.input_file
        if isinstance(error,UnicodeDecodeError):attempt.update(encoding=error.encoding,byteOffset=error.start)
        # No exception text, JSON contents, tokens or subprocess output in this report.
        attempt['frames']=[{'file':Path(frame.filename).name,'line':frame.lineno,'function':frame.name}
                           for frame in traceback.extract_tb(error.__traceback__)]
        raise
    finally:
        if args.diagnostic_output:json_write(Path(args.diagnostic_output),attempt)

if __name__=='__main__':
    try:main()
    except Exception as error:
        print('APK publication stopped: '+type(error).__name__+'. Inspect saved status/logs.',flush=True);raise SystemExit(1)
