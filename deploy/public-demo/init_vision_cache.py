"""Copy only verified baseline model files; never overwrite an existing different cache."""
import hashlib
import json
from pathlib import Path
import shutil

FILES=('detector.pt','classifier.pt','energy.json','manifest.json')

def digest(path):
    value=hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda:stream.read(1024*1024),b''):value.update(chunk)
    return value.hexdigest()

def populate(source,cache):
    manifest=json.loads((source/'manifest.json').read_text())
    expected={name:manifest['files'][name]['sha256'] for name in FILES[:-1]}
    expected['manifest.json']=digest(source/'manifest.json')
    # Validate all input and existing output files before performing any writes.
    for name in FILES:
        if digest(source/name)!=expected[name]:raise RuntimeError('Baseline source checksum mismatch')
        target=cache/name
        if target.exists() and (target.is_symlink() or digest(target)!=expected[name]):
            raise RuntimeError('Different cache contents were preserved; use a new cache version')
        if (cache/(name+'.tmp')).exists():raise RuntimeError('Incomplete cache copy needs review')
    for name in FILES:
        target=cache/name
        if target.exists():continue
        temporary=cache/(name+'.tmp')
        shutil.copyfile(source/name,temporary)
        if digest(temporary)!=expected[name]:raise RuntimeError('Copied model checksum mismatch')
        temporary.chmod(0o444)
        temporary.replace(target)
    return {'verified':True,'files':expected,'sourceFilesModified':False}

if __name__=='__main__':print(json.dumps(populate(Path('/source'),Path('/cache'))))
