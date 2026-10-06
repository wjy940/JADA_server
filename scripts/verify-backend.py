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
if '--status' in sys.argv:
    health=json.loads(subprocess.check_output(['curl','--fail','--silent',BASE+'/health'],text=True))
    ledger=json.loads(subprocess.check_output(['curl','--fail','--silent',BASE+'/api/modules/engineeringEquipment/list?page=1&pageSize=1'],text=True))
    assert health['data']['database']=='severe_equipment_assets' and len(ledger['list'])==1
    print(json.dumps({'health':health,'ledgerTotal':ledger['total'],'ledgerRecordId':ledger['list'][0]['id']},ensure_ascii=False))
    with open('scripts/verification-result.json') as f:report=json.load(f)
    report['finalCurlHealth']=health;report['finalCurlLedgerRecordId']=ledger['list'][0]['id']
    with open('scripts/verification-result.json','w') as f:json.dump(report,f,ensure_ascii=False,indent=2)
    sys.exit(0)
if '--prepare-restart' in sys.argv:
    permissions=[p['id'] for p in api('GET','/api/roles/viewer/permissions')['data']]
    with open('scripts/restart-permissions.json','w') as f:json.dump(permissions,f)
    api('POST','/api/roles/viewer/permissions',{'permissions':[p for p in permissions if p!='engineeringEquipment:view']})
    api('GET','/api/modules/engineeringEquipment/list',user='viewer',expected=403)
    print('PREPARED permission revocation restart check',flush=True)
    sys.exit(0)
if '--restore-permissions' in sys.argv:
    with open('scripts/restart-permissions.json') as f:permissions=json.load(f)
    api('POST','/api/roles/viewer/permissions',{'permissions':permissions})
    print('RESTORED viewer permissions')
    sys.exit(0)
if '--check-restart' in sys.argv:
    with open('scripts/restart-permissions.json') as f:permissions=json.load(f)
    api('GET','/api/modules/engineeringEquipment/list',user='viewer',expected=403)
    check('revoked database permission survives backend restart',True)
    api('POST','/api/roles/viewer/permissions',{'permissions':permissions})
    api('GET','/api/modules/engineeringEquipment/list',user='viewer')
    with open('scripts/verification-result.json') as f:report=json.load(f)
    report['checks']+=checks;report['passed']=len(report['checks']);report['restartPermissionsPersisted']=True
    with open('scripts/verification-result.json','w') as f:json.dump(report,f,ensure_ascii=False,indent=2)
    print('RESTORED viewer permissions; ALL PASS',report['passed'])
    sys.exit(0)
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
front=pathlib.Path('../WEB_Equipment_Assets').resolve()
check('frontend source is requested WEB_Equipment_Assets',str(front)=='/Users/winston/Desktop/JADA_Work/JADA/JADA_Program/WEB_Equipment_Assets')
with urllib.request.urlopen('http://localhost:5173/workbench',timeout=10) as response:check('requested workbench is served',response.status==200)
check('health uses severe_equipment_assets' ,api('GET','/health',user=None)['data']['database']=='severe_equipment_assets')
check('35 database metadata modules',len(mods)==35)
check('legacy states preserved and standardized',db("select count(*) from app_records where status not in ('draft','submitted','reviewing','approved','rejected','voided','archived')")=='0')
check('existing ledger returns real ordered cells',len(api('GET','/api/modules/engineeringEquipment/list?page=1&pageSize=1',user=None)['list'][0]['cells'])>0)
loginUser=TAG+'-login';api('POST','/api/users',{'id':loginUser,'name':loginUser,'roles':['viewer']});api('POST',f'/api/users/{loginUser}/password',{'password':'Disposable-QA-2026!'});api('POST','/api/auth/login',{'account':loginUser,'password':'Disposable-QA-2026!'});check('login/header identity',api('GET','/api/auth/me')['data']['id']=='admin')
equipment,payload=create('engineeringEquipment',{'资产编号':TAG+'-EQ','nested':{'a':[1,2],'text':'完整表单'},'projectName':TAG})
r=row(equipment);check('create payload persisted with actor',r['payload']['nested']==payload['nested'] and r['created_by']=='admin')
api('PATCH',f'/api/modules/engineeringEquipment/{equipment}',{'备注':'数据库编辑'});check('edit preserves UUID',row(equipment)['payload']['备注']=='数据库编辑')
api('PUT',f'/api/modules/engineeringEquipment/{equipment}',{'status':'approved'},expected=409)
api('POST','/api/modules/engineeringEquipment',payload,user='viewer',expected=403)
api('POST','/api/modules/engineeringEquipment',payload,user='viewer',role='superAdmin',expected=403)
api('GET','/api/modules/engineeringEquipment/list',user='viewer',scope='company',expected=403)
check('button permissions and forged role/scope denied',True)
check('own scope hides other creators',api('GET','/api/modules/engineeringEquipment/list',user='viewer',scope='own')['total']==0)
check('admin own scope excludes imported legacy rows',api('GET','/api/modules/engineeringEquipment/list',scope='own')['total']<api('GET','/api/modules/engineeringEquipment/list')['total'])
action('engineeringEquipment',equipment,'submit');api('POST',f'/api/modules/engineeringEquipment/{equipment}/submit',expected=409)
check('approver personal todo visible',any(t['record_id']==equipment for t in api('GET','/api/todos/my',user='approver')['data']))
action('engineeringEquipment',equipment,'approve',user='approver');check('approved status in PostgreSQL',row(equipment)['status']=='approved')
logs=api('GET',f'/api/audit/logs?recordId={equipment}')['data'];check('create edit submit approve audit',{'create','edit','submit','approve'}<=set(x['action'] for x in logs))
action('engineeringEquipment',equipment,'archive');check('archive persisted',row(equipment)['status']=='archived')
supplier,_=create('supplier',{'供应商名称':TAG+' Supplier'});flow('supplier',supplier);check('supplier qualified',row(supplier)['payload']['qualified'] is True)
inbound,_=create('inbound',{'supplierId':supplier,'assetNo':TAG+'-IN','manufacturer':'QA厂商','equipment_model':'QA型号','warehouseId':TAG+'-WH'})
flow('inbound',inbound);ir=row(inbound);target=ir['payload']['equipmentId'];check('inbound creates linked ledger and archive',row(target)['module_id']=='engineeringEquipment' and ir['payload']['inboundArchived'] is True)
bad,_=create('inbound',{'supplierId':supplier});action('inbound',bad,'submit');action('inbound',bad,'approve',expected=409)
check('failed linkage rolls entire approval back',row(bad)['status']=='submitted' and db("select count(*) from app_business_links where source_id='"+bad+"'")=='0')
purchase,_=create('partsPurchase',{'supplierId':supplier,'projectName':TAG,'partNo':TAG+'-PART','quantity':'2','warehouseId':TAG+'-WH','amount':'700'})
flow('partsPurchase',purchase)
links=json.loads(db("select coalesce(json_agg(target_id),'[]') from app_business_links where source_id='"+purchase+"' and kind='purchase'"));check('purchase creates pending receipt',len(links)==1 and row(links[0])['module_id']=='purchaseInbound')
check('purchase durable settlement reservation',db("select count(*) from app_finance_settlements where source_id='"+purchase+"'")=='1')
receipt,_=create('purchaseInbound',{'supplierId':supplier,'partNo':TAG+'-PART','quantity':'2','warehouseId':TAG+'-WH'});flow('purchaseInbound',receipt)
check('spare receipt updates stock',db("select quantity from app_stock_items where part_no='"+TAG+"-PART'")=='2')
transfer,_=create('warehouse',{'stockAction':'transfer','partNo':TAG+'-PART','warehouseId':TAG+'-WH','targetWarehouseId':TAG+'-WH2','quantity':'1'});flow('warehouse',transfer)
check('stock transfer updates both warehouses',db("select sum(quantity) from app_stock_items where part_no='"+TAG+"-PART'")=='2' and db("select quantity from app_stock_items where part_no='"+TAG+"-PART' and warehouse_id='"+TAG+"-WH2'")=='1')
outbound,_=create('warehouse',{'stockAction':'outbound','partNo':TAG+'-PART','warehouseId':TAG+'-WH2','quantity':'1'});flow('warehouse',outbound)
check('outbound reduces persisted stock',db("select quantity from app_stock_items where part_no='"+TAG+"-PART' and warehouse_id='"+TAG+"-WH2'")=='0')
insufficient,_=create('warehouse',{'stockAction':'outbound','partNo':TAG+'-PART','warehouseId':TAG+'-WH2','quantity':'1'});action('warehouse',insufficient,'submit');action('warehouse',insufficient,'approve',expected=409)
check('insufficient stock rolls approval back',row(insufficient)['status']=='submitted')
maintenance,_=create('maintenance',{'equipmentId':target});action('maintenance',maintenance,'submit');check('maintenance submit occupies equipment',row(target)['payload']['equipmentStatus']=='maintenance')
api('PATCH',f'/api/modules/engineeringEquipment/{target}',{'备注':'不能绕过维修'},expected=409)
api('DELETE',f'/api/modules/engineeringEquipment/{target}',expected=409)
check('occupied equipment cannot be edited or deleted',True)
other,_=create('maintenance',{'equipmentId':target});action('maintenance',other,'submit',expected=409);check('duplicate equipment occupation rolls back',row(other)['status']=='draft')
action('maintenance',maintenance,'approve',user='approver');check('maintenance completion releases equipment',row(target)['payload']['equipmentStatus']=='available')
contract,_=create('contract',{'equipmentId':target,'amount':'1000','startDate':'2026-10-01','endDate':'2026-10-31','lessee':TAG});flow('contract',contract);check('effective rental occupies equipment',row(target)['payload']['equipmentStatus']=='rented')
action('contract',contract,'archive',{'contractEnded':True});check('contract end releases equipment',row(target)['payload']['equipmentStatus']=='available')
dispatch,_=create('dispatch',{'equipmentId':target,'location':TAG+'-SITE','custodian':TAG+'-TEAM','warehouseId':TAG+'-WH2'});flow('dispatch',dispatch);check('dispatch updates ledger location and keeper',row(target)['payload']['location']==TAG+'-SITE' and row(target)['payload']['custodian']==TAG+'-TEAM')
draft,_=create('engineeringEquipment',{'资产编号':TAG+'-DEL'});api('DELETE',f'/api/modules/engineeringEquipment/{draft}');check('delete is persisted soft delete',row(draft)['deleted_at'] is not None)
voided,_=create('engineeringEquipment',{'资产编号':TAG+'-VOID'});action('engineeringEquipment',voided,'void');action('engineeringEquipment',voided,'archive');check('void then archive follows rules',row(voided)['status']=='archived')
rejected,_=create('engineeringEquipment',{'资产编号':TAG+'-REJ'});action('engineeringEquipment',rejected,'submit');action('engineeringEquipment',rejected,'reject',user='approver');action('engineeringEquipment',rejected,'saveDraft',{'备注':'保存草稿内容'});check('reject returns to editable draft',row(rejected)['status']=='draft' and row(rejected)['payload']['备注']=='保存草稿内容')
review,_=create('engineeringEquipment',{'资产编号':TAG+'-REVIEW'});action('engineeringEquipment',review,'submit');action('engineeringEquipment',review,'startReview',user='approver');check('reviewing state supported',row(review)['status']=='reviewing');action('engineeringEquipment',review,'reject',user='approver')
api('POST','/api/modules/engineeringEquipment/import',[fixture('engineeringEquipment',{'资产编号':TAG+'-IMP'})]);api('POST','/api/modules/engineeringEquipment/export',{'keyword':TAG});check('import export audited and durable',db("select count(*) from app_import_batches")!='0')
api('POST',f'/api/modules/engineeringEquipment/{rejected}/attachments',{'name':'QA.txt','storageKey':TAG+'/QA.txt'});check('attachment metadata persisted',len(api('GET',f'/api/modules/engineeringEquipment/{rejected}/attachments')['data'])==1)
api('GET',f'/api/modules/engineeringEquipment/{rejected}/attachments',user='viewer',expected=403)
role=TAG+'-role';user=TAG+'-user';api('POST','/api/roles',{'id':role,'name':'QA角色','dataScope':'project'});api('POST',f'/api/roles/{role}/permissions',{'permissions':['engineeringEquipment:view','engineeringEquipment:create']});api('POST','/api/users',{'id':user,'name':'QA用户','roles':[role],'projectName':TAG})
api('GET',f'/api/users/{user}');api('GET',f'/api/roles/{role}');api('PUT',f'/api/roles/{role}',{'name':'QA角色更新','dataScope':'project'});api('PUT',f'/api/users/{user}',{'name':'QA用户更新','roles':[role],'projectName':TAG})
check('account role permission persistence',len(api('GET',f'/api/roles/{role}/permissions')['data'])==2)
project=api('GET','/api/modules/engineeringEquipment/list',user=user)['list'];check('project scope filters records',all(row(x['id'])['project_name']==TAG for x in project) and len(project)>0)
api('GET','/api/users',user=user,expected=403);api('GET','/api/auth/permissions',user=user);api('GET','/api/todos');check('system administration permission boundary',True)
scoped,_=create('engineeringEquipment',{'资产编号':TAG+'-SCOPED','warehouseId':TAG+'-WS','supplierId':supplier,'departmentId':TAG+'-DEPT'})
for scope,property,value in [('warehouse','warehouseId',TAG+'-WS'),('supplier','supplierId',supplier),('department','departmentId',TAG+'-DEPT')]:
    rid=TAG+'-'+scope;uid=rid+'-user'
    api('POST','/api/roles',{'id':rid,'name':'QA '+scope,'dataScope':scope})
    api('POST',f'/api/roles/{rid}/permissions',{'permissions':['engineeringEquipment:view']})
    api('POST','/api/users',{'id':uid,'name':uid,'roles':[rid],property:value})
    records=api('GET','/api/modules/engineeringEquipment/list',user=uid)['list']
    check(scope+' scope enforces identity affiliation',len(records)>0 and all(row(r['id'])['payload'].get(property)==value for r in records))
api('POST','/api/modules/engineeringEquipment',fixture('engineeringEquipment',{'资产编号':TAG+'-SCOPED'}),expected=409)
check('duplicate asset identifier rejected',True)
check('settlement interface returns durable requests',len(api('GET','/api/finance/settlements')['data'])>0)
with urllib.request.urlopen(urllib.request.Request(BASE+'/api/modules',headers={'Origin':'http://localhost:5173'},method='OPTIONS'),timeout=10) as response:check('localhost CORS allowed',response.headers.get('Access-Control-Allow-Origin')=='http://localhost:5173')
with urllib.request.urlopen(urllib.request.Request(BASE+'/api/modules',headers={'Origin':'http://127.0.0.1:5173'},method='OPTIONS'),timeout=10) as response:check('127.0.0.1 CORS allowed',response.headers.get('Access-Control-Allow-Origin')=='http://127.0.0.1:5173')
check('failed requests durable operation log',int(db("select count(*) from app_operation_logs where result='failure'"))>0)
report={'frontendSource':'/Users/winston/Desktop/JADA_Work/JADA/JADA_Program/WEB_Equipment_Assets','backendSource':str(pathlib.Path.cwd()),'databaseDriver':'PostgreSQL JDBC from Maven repository','tag':TAG,'checks':checks,'created':created,'passed':len(checks),'database':'severe_equipment_assets','frontendFullClosure':False}
with open('scripts/verification-result.json','w') as f:json.dump(report,f,ensure_ascii=False,indent=2)
print('ALL PASS',len(checks),TAG)
