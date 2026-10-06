#!/usr/bin/env python3
"""Real HTTP + PostgreSQL acceptance. Creates labelled QA records; never touches frontend or target."""
import sys
import json, os, urllib.request, urllib.error, subprocess, shutil, uuid, datetime, pathlib
BASE=os.getenv('API_BASE','http://127.0.0.1:8080')
JAVA=os.getenv('JAVA_HOME','/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home')+'/bin/java'
DRIVER=str(max(pathlib.Path.home().glob('.m2/repository/org/postgresql/postgresql/*/postgresql-*.jar'),key=lambda p:tuple(int(n) for n in p.parent.name.split('.'))))
TAG='QA-'+datetime.datetime.now().strftime('%Y%m%d%H%M%S')
checks=[]; created=[]
def check(name,ok):
    assert ok,name
    checks.append(name); print('PASS',name,flush=True)
def api(method,path,body=None,user='admin',scope=None,expected=200,role=None):
    headers={'Content-Type':'application/json','X-Test-Data':'true'}
    if role and role.startswith('Bearer '): headers['Authorization']=role; role=None; user=None
    if user: headers['X-User-Id']=user
    if scope: headers['X-Data-Scope']=scope
    if role: headers['X-User-Role']=role
    req=urllib.request.Request(BASE+path,data=None if body is None else json.dumps(body,ensure_ascii=False).encode(),headers=headers,method=method)
    try:
        with urllib.request.urlopen(req,timeout=20) as r: code=r.status; data=json.load(r)
    except urllib.error.HTTPError as e: code=e.code; data=json.load(e)
    assert code==expected,(method,path,code,data)
    return data

def db(sql):
    return subprocess.check_output([JAVA,'--class-path',DRIVER,'scripts/DbQuery.java',sql],env=os.environ.copy(),text=True).strip()
def row(id):
    uuid.UUID(id)
    return json.loads(db("select row_to_json(r) from app_records r where id='"+id+"'"))
mods=api('GET','/api/modules')['data'];schemas={m['module_id']:m['form_schema'] for m in mods}
def fixture(module,extra=None):
    body={'title':TAG+' '+module}
    for f in schemas[module]:
        if f['required']:
            sample=str(f.get('sample_value') or TAG).split('|')[0]
            if f['field_type']=='date':sample='2027-10-04' if '有效期' in f['field_label'] else '2026-10-04'
            if f['field_type']=='datetime-local':sample='2026-10-04T09:00'
            if f['field_type']=='number':sample='10'
            body[f['field_label']]=sample
    body.update(extra or {});return body
def create(module,extra=None):
    payload=fixture(module,extra)
    r=api('POST','/api/modules/'+module,payload)['record']
    for field in schemas[module]:
        if field['required'] and field['field_type']=='file':
            boundary='QA-'+uuid.uuid4().hex
            content=(f'--{boundary}\r\nContent-Disposition: form-data; name="moduleId"\r\n\r\n{module}\r\n--{boundary}\r\nContent-Disposition: form-data; name="recordId"\r\n\r\n{r["id"]}\r\n--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="QA.txt"\r\nContent-Type: text/plain\r\n\r\nQA acceptance attachment\r\n--{boundary}--\r\n').encode()
            req=urllib.request.Request(BASE+'/api/attachments',data=content,headers={'X-User-Id':'admin','Content-Type':'multipart/form-data; boundary='+boundary},method='POST')
            with urllib.request.urlopen(req,timeout=20) as response:uploaded=json.load(response)['data']['id']
            payload[field['field_label']]=uploaded
            api('PATCH',f'/api/modules/{module}/{r["id"]}',{field['field_label']:uploaded})
    created.append({'module':module,'id':r['id']})
    return r['id'],payload
def action(module,id,a,body=None,user='admin',expected=200):return api('POST',f'/api/modules/{module}/{id}/{a}',body,user=user,expected=expected)
def flow(module,id):
    action(module,id,'submit');action(module,id,'approve',user='approver')
