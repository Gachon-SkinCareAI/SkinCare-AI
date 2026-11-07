"""Compare two CPU startup profiles in isolated AI containers; never deploy them."""
import argparse
import copy
import json
from pathlib import Path
import subprocess
import time

from public_web import validate_active
from publish_active_app import verify_snapshot
from run_free_demo import HERE, HIDDEN, checksum, compose_command, instance_lock, json_write
from upload_recovery_check import check_isolated, IsolatedCheckError
from upload_recovery_release import validate

SINGLE_THREAD = {'AI_CPU_THREADS':'1', 'OMP_NUM_THREADS':'1', 'MKL_NUM_THREADS':'1',
                 'OMP_THREAD_LIMIT':'1'}


def diagnose(args, execute=subprocess.run):
    read=lambda path:json.loads(Path(path).read_text(encoding='utf-8-sig'))
    config=read(args.config)
    if config.get('format')!='skincare-free-demo-v1':raise RuntimeError('Unexpected runtime')
    root=Path(config['runtime']).resolve();bundle=root/'bundle'
    manifest=read(bundle/'bundle-manifest.json')
    receipt=validate_active(bundle,manifest)
    folder=bundle/'public-web'/receipt['release']
    verify_snapshot(folder,receipt)
    source=Path(args.release).resolve();candidate=validate(source)
    protected_paths=[bundle/'public-web-active.json',folder/'release.json',folder/'compose.public-web.json']
    hashes={p:checksum(p) for p in protected_paths}
    public_status=read(args.public_status)
    origin=receipt['origin']
    output=Path(args.output).resolve();output.mkdir(parents=True,exist_ok=True)
    out=output/('run-'+str(time.time_ns()));out.mkdir()
    report={'completed':False,'productionChanged':False,'origin':origin,'cases':{},'output':str(out)}
    validation_photo=getattr(args,'validation_photo',None)
    if validation_photo:
        validation_photo=Path(validation_photo).resolve()
        photo_hash=checksum(validation_photo)
        report['validationPhotoSha256']=photo_hash

    def save():
        json_write(out/'status.json',report)
        json_write(output/'latest.json',report)

    def current():
        status=read(args.public_status)
        if (not status.get('active') or not status.get('passed') or status.get('origin')!=origin
                or (root/'public-web-stop.request').exists() or (root/'stop.request').exists()
                or any(checksum(p)!=digest for p,digest in hashes.items())):
            raise RuntimeError('Public runtime changed or is stopping; diagnosis cancelled')

    current()
    if args.check_only:
        print('startup-diagnosis-preflight-complete',flush=True)
        return {'checkOnly':True,'passed':True}
    operation=root/'public-upload-recovery';operation.mkdir(exist_ok=True)
    compose=compose_command(bundle,manifest,allow_public_web=True)+['-f',str(folder/'compose.public-web.json')]

    def command(argv):
        current()
        result=execute(argv,cwd=bundle,capture_output=True,timeout=35,creationflags=HIDDEN)
        if result.returncode:raise RuntimeError('Docker read failed during diagnosis')
        return json.loads(result.stdout)

    names=[manifest['project']+'-'+name+'-1' for name in ('db','llm','backend','edge','maintenance','ai')]
    def inspect():
        records=command(['docker','inspect']+names)
        result={}
        for row in records:
            labels=row['Config']['Labels']
            if labels.get('com.docker.compose.project')!=manifest['project']:
                raise RuntimeError('Unexpected container ownership')
            result[labels['com.docker.compose.service']]={'id':row['Id'],'image':row['Image']}
        if set(result)!={'db','llm','backend','edge','maintenance','ai'}:
            raise RuntimeError('Production container identity is incomplete')
        return result

    try:
        with instance_lock(operation):
            initial=inspect()
            baseline=command(compose+['config','--format','json'])
            probe=(HERE/'vision_startup_probe.py').read_text(encoding='utf-8')
            for profile in ('existing-threads','single-thread'):
                current()
                if validate(source)!=candidate:raise RuntimeError('Diagnostic source changed')
                if validation_photo and checksum(validation_photo)!=photo_hash:
                    raise RuntimeError('Validation photo changed')
                settings=copy.deepcopy(baseline)
                if profile=='single-thread':settings['services']['ai'].setdefault('environment',{}).update(SINGLE_THREAD)
                case_out=out/profile;case_out.mkdir()
                case={'passed':False,'productionChanged':False}
                report['cases'][profile]=case
                def step(label):
                    case['step']=label;save();print(profile+': '+label,flush=True)
                try:
                    check_isolated(settings,source,initial['ai']['image'],operation,manifest['project'],
                                   bundle,case_out,execute,current,step,probe,validation_photo=validation_photo)
                    case['passed']=True
                except IsolatedCheckError as error:
                    case['failedStep']=error.step
                    case['errorType']=type(error).__name__
                    # The next case must not start if the test container may remain.
                    if error.step=='remove-isolated-ai' or not (case_out/'remove-isolated-ai.log').exists():raise
                if inspect()!=initial:raise RuntimeError('Production container identity changed')
                current();save()
            report['completed']=True
            report['productionIdentitiesUnchanged']=True
            print('complete-startup-diagnosis; production services unchanged',flush=True)
    except Exception as error:
        report['errorType']=type(error).__name__
        raise
    finally:save()
    return report


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ('config','public-status','release','output'):parser.add_argument('--'+name,required=True)
    parser.add_argument('--check-only',action='store_true')
    parser.add_argument('--validation-photo')
    diagnose(parser.parse_args())
