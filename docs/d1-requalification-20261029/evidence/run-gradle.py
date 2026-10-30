import os,subprocess,sys,urllib.parse,pathlib
root='/workspace/SentinelQuantumVanguardAiPro'
e=os.environ.copy(); e.update(JAVA_HOME='/workspace/tooling/jdk17',ANDROID_HOME='/workspace/tooling/android-sdk')
p=urllib.parse.urlparse(e.get('HTTPS_PROXY',''))
e['JAVA_TOOL_OPTIONS']='-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts'+(f' -Dhttps.proxyHost={p.hostname} -Dhttps.proxyPort={p.port} -Dhttp.proxyHost={p.hostname} -Dhttp.proxyPort={p.port}' if p.hostname else '')
name=sys.argv[1]
args=['/workspace/tooling/gradle-9.8.0/bin/gradle','-p',root+'/native-android-app']+sys.argv[2:]+['-PsentinelSignatureFixture=/workspace/phone-core-v1-delivery/Sentinel-PhoneCore-V1-debug.apk','--no-daemon','--console=plain','--max-workers=2']
with open('/workspace/d1-evidence/'+name+'.log','w') as f:r=subprocess.run(args,env=e,cwd=root,stdout=f,stderr=subprocess.STDOUT)
pathlib.Path('/workspace/d1-evidence/'+name+'.exit').write_text(str(r.returncode)+'\n')
print(name,r.returncode)
sys.exit(r.returncode)
