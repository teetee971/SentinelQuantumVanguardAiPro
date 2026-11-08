#!/usr/bin/env python3
"""Validate/copy actual artifacts; never signs, installs, or sends messages."""
import argparse, hashlib, json, os, re, shutil, subprocess, tempfile, zipfile
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('build_root',type=Path);p.add_argument('output',type=Path);p.add_argument('--bundletool',type=Path);a=p.parse_args();a.output.mkdir(parents=True,exist_ok=True)
tools=Path(os.environ['ANDROID_HOME'])/'build-tools/36.0.0';ledger=[];artifacts={}
def command(label,args):
 r=subprocess.run([str(x) for x in args],capture_output=True,text=True)
 (a.output/(label+'.log')).write_text(r.stdout+r.stderr)
 ledger.append({'label':label,'command':[str(x) for x in args],'exit':r.returncode})
 if r.returncode:raise RuntimeError(f'{label} failed: {r.returncode}')
 return r.stdout
def freeze_copy(source, destination):
 if destination.exists():
  assert hashlib.sha256(destination.read_bytes()).digest()==hashlib.sha256(source.read_bytes()).digest(), 'Refusing to overwrite a different archived artifact'
 else:shutil.copyfile(source,destination)
specs={'app':'app/outputs/apk/debug/app-debug.apk','app-test':'app/outputs/apk/androidTest/debug/app-debug-androidTest.apk','lab':'mms-compression-lab/outputs/apk/debug/mms-compression-lab-debug.apk','lab-test':'mms-compression-lab/outputs/apk/androidTest/debug/mms-compression-lab-debug-androidTest.apk'}
for label,relative in specs.items():
 apk=a.build_root/relative
 signature=command(label+'-signature',[tools/'apksigner','verify','--verbose','--print-certs',apk])
 assert 'Verified using v2 scheme (APK Signature Scheme v2): true' in signature
 command(label+'-align',[tools/'zipalign','-c','-P','16','4',apk])
 badging=command(label+'-badging',[tools/'aapt2','dump','badging',apk])
 if label=='app':assert "name='com.sentinel.quantum'" in badging and "versionCode='23'" in badging
 if label=='lab':assert "name='com.sentinel.quantum.mmslab.v23'" in badging and 'uses-permission:' not in badging
 with zipfile.ZipFile(apk) as z:
  native=[n for n in z.namelist() if re.fullmatch(r'lib/[^/]+/libsentinel_jpeg_validator\.so',n)]
  if label in ('app','lab'):
   assert {n.split('/')[1] for n in native}=={'arm64-v8a','armeabi-v7a','x86','x86_64'}
   for name in ('LICENSE.md','README.ijg','NOTICE.txt'):assert 'assets/licenses/libjpeg-turbo/'+name in z.namelist()
  for name in native:
   abi=name.split('/')[1]
   with tempfile.TemporaryDirectory() as temporary:
    path=Path(temporary)/'validator.so';path.write_bytes(z.read(name))
    headers=command(label+'-'+abi+'-elf',['readelf','-lW',path]);loads=[line for line in headers.splitlines() if line.strip().startswith('LOAD')]
    assert loads and all(int(line.split()[-1],16)>=16384 for line in loads)
    symbols=command(label+'-'+abi+'-symbols',['readelf','--dyn-syms','-W',path]);exports=[]
    for line in symbols.splitlines():
     parts=line.split()
     if len(parts)>=8 and parts[4] in ('GLOBAL','WEAK') and parts[5]=='DEFAULT' and parts[6]!='UND':exports.append(parts[7])
    assert set(exports)=={'Java_com_sentinel_quantum_mmsjpeg_StrictJpegValidator_validateNative'},exports
 dest=a.output/(label+'.apk');freeze_copy(apk,dest)
 artifacts[dest.name]={'bytes':dest.stat().st_size,'sha256':hashlib.sha256(dest.read_bytes()).hexdigest(),'source':str(apk),'hard_link_to_build':os.path.samestat(apk.stat(),dest.stat())}
 assert not artifacts[dest.name]['hard_link_to_build']
bundle=a.build_root/'app/outputs/bundle/releaseUnsigned/app-releaseUnsigned.aab'
if bundle.exists():
 with zipfile.ZipFile(bundle) as z:
  assert 'base/manifest/AndroidManifest.xml' in z.namelist() and 'BundleConfig.pb' in z.namelist()
  assert not any(n.startswith('META-INF/') and n.endswith(('.RSA','.DSA','.EC','.SF')) for n in z.namelist())
  assert {n.split('/')[2] for n in z.namelist() if n.startswith('base/lib/') and n.endswith('/libsentinel_jpeg_validator.so')}=={'arm64-v8a','armeabi-v7a','x86','x86_64'}
  for name in ('LICENSE.md','README.ijg','NOTICE.txt'):assert 'base/assets/licenses/libjpeg-turbo/'+name in z.namelist()
 dest=a.output/'app-releaseUnsigned.aab';freeze_copy(bundle,dest)
 artifacts[dest.name]={'bytes':dest.stat().st_size,'sha256':hashlib.sha256(dest.read_bytes()).hexdigest(),'signing':'UNSIGNED_NOT_PUBLISHABLE','source':str(bundle),'hard_link_to_build':os.path.samestat(bundle.stat(),dest.stat())}
 assert not artifacts[dest.name]['hard_link_to_build']
 if a.bundletool:command('bundletool-validate',['java','-jar',a.bundletool,'validate','--bundle='+str(dest)])
 seeds=(a.build_root/'app/outputs/mapping/releaseUnsigned/seeds.txt').read_text()
 kept=[line for line in seeds.splitlines() if 'com.sentinel.quantum.mmsjpeg.StrictJpegValidator' in line]
 assert any('boolean interrupted()' in line for line in kept) and any('int validateNative(byte[])' in line for line in kept)
 (a.output/'R8_BRIDGE_SEEDS.txt').write_text('\n'.join(kept)+'\n')
(a.output/'ARTIFACT_COMMANDS.json').write_text(json.dumps(ledger,indent=2)+'\n')
(a.output/'ARTIFACTS.json').write_text(json.dumps(artifacts,indent=2)+'\n')
(a.output/'SHA256SUMS').write_text(''.join(v['sha256']+'  '+k+'\n' for k,v in artifacts.items()))
print(json.dumps(artifacts,indent=2))
