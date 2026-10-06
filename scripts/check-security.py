#!/usr/bin/env python3
"""Real HTTP security checks on isolated local ports; processes always terminate."""
import os,subprocess,pathlib,tempfile,time,json,urllib.request,urllib.error
jar=pathlib.Path(os.environ['APP_JAR']).resolve()
java=pathlib.Path(os.getenv('JAVA_HOME','/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home'))/'bin/java'
base=os.getenv('API_BASE','http://127.0.0.1:8080')
checks=[]
def api(origin,path,headers=None,body=None,expected=200):
 h={'Content-Type':'application/json',**(headers or {})}
 req=urllib.request.Request(origin+path,headers=h,data=None if body is None else json.dumps(body).encode())
 try:
  with urllib.request.urlopen(req,timeout=20) as response:code=response.status;data=json.load(response)
 except urllib.error.HTTPError as error:code=error.code;data=json.load(error)
 assert code==expected,(path,code,data)
 return data
def check(name):checks.append(name);print('PASS',name,flush=True)
def stop(process):
 if process.poll() is None:
  process.terminate()
  try:process.wait(timeout=10)
  except subprocess.TimeoutExpired:process.kill();process.wait(timeout=5)
# Obtain a real administrator token without changing the administrator password.
password=os.environ['API_PASSWORD']
token=api(base,'/api/auth/login',body={'account':os.getenv('API_ACCOUNT','admin'),'password':password})['data']['token']
with tempfile.TemporaryFile(mode='w+') as log:
 process=subprocess.Popen([str(java),'-jar',str(jar),'--server.port=8082','--app.security.allow-header-auth=false','--app.security.public-ledger-read=false','--app.include-test-data=false'],stdout=log,stderr=subprocess.STDOUT)
 try:
  ready=False
  for _ in range(40):
   if process.poll() is not None:break
   try:ready=api('http://127.0.0.1:8082','/health')['data']['database']=='severe_equipment_assets'
   except Exception:pass
   if ready:break
   time.sleep(1)
  assert ready,'Strict security server did not start'
  origin='http://127.0.0.1:8082'
  api(origin,'/api/auth/me',{'X-User-Id':'admin'},expected=403);check('strict configuration rejects simulated identity')
  api(origin,'/api/modules/engineeringEquipment/list',expected=401);check('strict configuration rejects anonymous business data')
  h={'Authorization':'Bearer '+token}
  api(origin,'/api/auth/me',h);check('strict configuration accepts persisted Bearer session')
  rows=api(origin,'/api/modules/engineeringEquipment/list?keyword=QA-',h)
  assert rows['total']==0;check('strict configuration excludes QA business records')
  users=api(origin,'/api/users',h)['data'];assert not any(u['id'].startswith('QA-') or u['id'] in ('viewer','approver') for u in users)
  check('strict configuration excludes QA accounts')
 finally:stop(process)
# Verify the actual prod profile fails fast with the development DB credential.
with tempfile.TemporaryFile(mode='w+') as log:
 env=os.environ.copy();env.update(DB_URL=env.get('DB_URL','jdbc:postgresql://127.0.0.1:55432/severe_equipment_assets'),DB_USERNAME=env.get('DB_USERNAME','jd_admin'),DB_PASSWORD='jd_dev_password',ATTACHMENT_DIR=str(pathlib.Path(tempfile.gettempdir())/'severe-security-check-files'))
 process=subprocess.Popen([str(java),'-jar',str(jar),'--server.port=8083','--spring.profiles.active=prod'],env=env,stdout=log,stderr=subprocess.STDOUT)
 try:
  code=process.wait(timeout=40);log.seek(0);output=log.read()
  assert code!=0 and 'Development database password is forbidden in production' in output
  check('prod profile refuses development database password')
 finally:stop(process)
api(base,'/api/auth/logout',{'Authorization':'Bearer '+token},body={})
pathlib.Path('scripts/security-verification-result.json').write_text(json.dumps({'verifiedAt':time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime()),'passed':len(checks),'checks':checks,'formalServerDeployed':False},ensure_ascii=False,indent=2))
print('ALL PASS',len(checks))
