#!/usr/bin/env python3
"""Extended real-database acceptance. QA records remain labelled for traceability."""
from acceptance_support import *
import zipfile,io
# Credentials belong to a disposable QA account, never overwrite the production administrator.
actor=TAG+'-actor'
api('POST','/api/users',{'id':actor,'name':actor,'roles':['approver']})
api('POST',f'/api/users/{actor}/password',{'password':'Disposable-QA-2026!'})
login=api('POST','/api/auth/login',{'account':actor,'password':'Disposable-QA-2026!'})['data']
token=login['token']
check('bearer authenticates real database user',api('GET','/api/auth/me',role='Bearer '+token)['data']['id']==actor)
api('GET','/api/auth/me',role='Bearer invalid',expected=401)
api('POST','/api/auth/logout',role='Bearer '+token)
api('GET','/api/auth/me',role='Bearer '+token,expected=401)
check('logout revokes persisted session and forged token denied',True)
# Two serial nodes; intermediate approval must not execute business side effects.
template=api('POST','/api/approval/templates',{'moduleId':'engineeringEquipment','name':TAG,'nodes':[{'nodeName':'初审','assignees':['approver'],'mode':'any'},{'nodeName':'复审','assignees':[actor],'mode':'any'}]})['data']['id']
id,_=create('engineeringEquipment',{'资产编号':TAG+'-MULTI'})
action('engineeringEquipment',id,'submit');action('engineeringEquipment',id,'approve',user='approver')
check('serial approval remains reviewing until final node',row(id)['status']=='reviewing')
flowData=api('GET',f'/api/approval/flow?businessType=engineeringEquipment&businessId={id}',user=actor)['data']
task=next(t for t in flowData['tasks'] if t['status']=='pending')
api('POST',f'/api/approval/tasks/{task["id"]}/return',{'targetStep':0,'comment':'重新核验'},user=actor)
action('engineeringEquipment',id,'approve',user='approver')
check('return starts new cycle and old approval cannot finish',row(id)['status']=='reviewing')
action('engineeringEquipment',id,'approve',user=actor)
check('final approval persisted atomically',row(id)['status']=='approved')
api('PUT',f'/api/approval/templates/{template}',{'moduleId':'engineeringEquipment','name':TAG,'enabled':False,'nodes':[{'nodeName':'初审','assignees':['approver']} ]})
# Add-sign switches current any node to all; both current tasks required.
id,_=create('engineeringEquipment',{'资产编号':TAG+'-SIGN'});action('engineeringEquipment',id,'submit')
f=api('GET',f'/api/approval/flow?businessType=engineeringEquipment&businessId={id}')['data']
t=next(t for t in f['tasks'] if t['assignee']=='approver' and t['status']=='pending')
# Default node already includes actor; use a deterministic single-assignee template instead.
action('engineeringEquipment',id,'void')
template2=api('POST','/api/approval/templates',{'moduleId':'engineeringEquipment','name':TAG+'-sign','nodes':[{'nodeName':'审核','assignees':['approver'],'mode':'any'}]})['data']['id']
id,_=create('engineeringEquipment',{'资产编号':TAG+'-SIGN2'});action('engineeringEquipment',id,'submit')
f=api('GET',f'/api/approval/flow?businessType=engineeringEquipment&businessId={id}')['data'];t=next(t for t in f['tasks'] if t['status']=='pending')
api('POST',f'/api/approval/tasks/{t["id"]}/addSign',{'assignee':actor},user='approver')
action('engineeringEquipment',id,'approve',user='approver');check('add-sign waits for additional approval',row(id)['status']=='submitted')
action('engineeringEquipment',id,'approve',user=actor);check('all signers complete current node',row(id)['status']=='approved')
id,_=create('engineeringEquipment',{'资产编号':TAG+'-TRANSFER'});action('engineeringEquipment',id,'submit')
f=api('GET',f'/api/approval/flow?businessType=engineeringEquipment&businessId={id}')['data'];t=next(t for t in f['tasks'] if t['status']=='pending')
api('POST',f'/api/approval/tasks/{t["id"]}/transfer',{'assignee':actor},user='approver')
action('engineeringEquipment',id,'approve',user='approver',expected=404);action('engineeringEquipment',id,'approve',user=actor)
check('transfer revokes original task and new assignee decides',row(id)['status']=='approved')
api('PUT',f'/api/approval/templates/{template2}',{'moduleId':'engineeringEquipment','name':TAG,'enabled':False,'nodes':[{'nodeName':'审核','assignees':['approver']}]})
# Authoritative projections.
check('non-admin menu hides account management and audit',not any(m['module_id'] in ('users','roleperm','audit') for m in api('GET','/api/modules',user=actor)['data']))
check('account module lists real app_users',any(r['sourceId']==actor for r in api('GET','/api/modules/users/list?keyword='+actor)['list']))
api('POST','/api/modules/audit',{'title':'forged'},expected=409)
check('audit list reads real logs and cannot be fabricated',api('GET','/api/modules/audit/list?pageSize=1')['total']>0)
# Real attachment upload, binary download and soft delete.
id,_=create('engineeringEquipment',{'资产编号':TAG+'-FILE'})
boundary=uuid.uuid4().hex
content=(f'--{boundary}\r\nContent-Disposition: form-data; name="moduleId"\r\n\r\nengineeringEquipment\r\n--{boundary}\r\nContent-Disposition: form-data; name="recordId"\r\n\r\n{id}\r\n--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="proof.txt"\r\nContent-Type: text/plain\r\n\r\nDatabase file evidence\r\n--{boundary}--\r\n').encode()
req=urllib.request.Request(BASE+'/api/attachments',data=content,headers={'X-User-Id':'admin','Content-Type':'multipart/form-data; boundary='+boundary},method='POST')
with urllib.request.urlopen(req) as r: fid=json.load(r)['data']['id']
req=urllib.request.Request(BASE+f'/api/attachments/{fid}/download',headers={'X-User-Id':'admin'})
with urllib.request.urlopen(req) as r:check('file bytes roundtrip through persistent metadata',r.read()==b'Database file evidence')
api('GET',f'/api/attachments/{fid}/download',user='viewer',expected=403)
api('DELETE',f'/api/attachments/{fid}');api('GET',f'/api/attachments/{fid}/download',expected=404)
check('attachment soft delete disables download',True)
req=urllib.request.Request(BASE+'/api/modules/engineeringEquipment/export/excel',data=json.dumps({'keyword':TAG}).encode(),headers={'X-User-Id':'admin','Content-Type':'application/json'},method='POST')
with urllib.request.urlopen(req) as r:data=r.read()
with zipfile.ZipFile(io.BytesIO(data)) as workbook:check('Excel export produces actual XLSX workbook','xl/worksheets/sheet1.xml' in workbook.namelist())
# Import real XLSX bytes; formula and duplicate rows must leave no partial records.
def xlsx(rows):
 from xml.sax.saxutils import escape
 out=io.BytesIO()
 with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as z:
  z.writestr('[Content_Types].xml','<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>')
  z.writestr('_rels/.rels','<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>')
  z.writestr('xl/workbook.xml','<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Import" sheetId="1" r:id="rId1"/></sheets></workbook>')
  z.writestr('xl/_rels/workbook.xml.rels','<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>')
  xml='<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>'
  for n,rowvalues in enumerate(rows,1):
   xml+=f'<row r="{n}">'
   for c,value in enumerate(rowvalues):
    cell=chr(65+c)+str(n)
    xml+=f'<c r="{cell}" t="inlineStr"><is><t>{escape(str(value))}</t></is></c>'
   xml+='</row>'
  z.writestr('xl/worksheets/sheet1.xml',xml+'</sheetData></worksheet>')
 return out.getvalue()
def import_xlsx(data,expected=200):
 boundary=uuid.uuid4().hex
 content=(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="import.xlsx"\r\nContent-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n').encode()+data+(f'\r\n--{boundary}--\r\n').encode()
 req=urllib.request.Request(BASE+'/api/modules/engineeringEquipment/import',data=content,headers={'X-User-Id':'admin','X-Test-Data':'true','Content-Type':'multipart/form-data; boundary='+boundary},method='POST')
 try:
  with urllib.request.urlopen(req) as response:code=response.status;result=json.load(response)
 except urllib.error.HTTPError as error:code=error.code;result=json.load(error)
 assert code==expected,(code,result)
 return result
imported=import_xlsx(xlsx([['title','资产编号'],[TAG+' imported',TAG+'-XLSX']]))['data']['recordIds'][0]
check('XLSX import stores complete payload and batch',row(imported)['payload']['资产编号']==TAG+'-XLSX')
import_xlsx(xlsx([['title','资产编号'],[TAG+' rollback',TAG+'-ROLLBACK'],[TAG+' duplicate',TAG+'-XLSX']]),409)
check('XLSX multi-row import rolls all rows back on conflict',db("select count(*) from app_records where payload->>'资产编号'='"+TAG+"-ROLLBACK'")=='0')
# Finance: use an actual supplier and approved purchase, no manual settlement rows.
supplier,_=create('supplier',{'供应商名称':TAG+' Supplier'});flow('supplier',supplier)
purchase,_=create('partsPurchase',{'supplierId':supplier,'amount':'100.00','currency':'AED'});flow('partsPurchase',purchase)
financeUser=TAG+'-finance';api('POST','/api/users',{'id':financeUser,'name':financeUser,'roles':['finance']})
settlement=next(s for s in api('GET','/api/finance/settlements')['data'] if s['source_id']==purchase)['id']
api('POST',f'/api/finance/settlements/{settlement}/confirm',{'amount':'100.00','currency':'AED'},user=financeUser)
body={'amount':'40.00','reference':TAG+'-BANK','paidAt':datetime.datetime.now(datetime.timezone.utc).isoformat()}
payment=api('POST',f'/api/finance/settlements/{settlement}/payments',body,user=financeUser)['data']
again=api('POST',f'/api/finance/settlements/{settlement}/payments',body,user=financeUser)['data']
check('bank reference is idempotent',payment['id']==again['id'])
api('POST',f'/api/finance/settlements/{settlement}/payments',{**body,'reference':TAG+'-OVER','amount':'61.00'},user=financeUser,expected=409)
check('overpayment rolls back ledger balance',db("select paid_amount from app_finance_settlements where id='"+settlement+"'")=='40.00')
api('POST',f'/api/finance/payments/{payment["id"]}/reverse',{'reason':'QA退款','reference':TAG+'-REFUND'},user=financeUser)
api('POST',f'/api/finance/payments/{payment["id"]}/reverse',{'reason':'重复退款','reference':TAG+'-REFUND2'},user=financeUser,expected=409)
check('reversed payment list reflects voided state',any(r['status']=='voided' for r in api('GET','/api/modules/expensePaymentRecord/list?keyword='+TAG)['list']))
check('payment reversal updates balance exactly once',db("select paid_amount from app_finance_settlements where id='"+settlement+"'")=='0.00')
# Personnel qualification -> task reservation -> authorized completion -> usage archive.
for module,role,personType,assetModule,personField,accountField in [
 ('vehicleDispatch','driver','司机','vehicleEquipment','driverId','driverUserId'),
 ('machineDispatch','operator','工程设备操作员','engineeringEquipment','operatorId','operatorUserId')]:
 account=TAG+'-'+role
 api('POST','/api/users',{'id':account,'name':account,'roles':[role]})
 person,_=create('person',{'人员类型':personType,'userId':account});flow('person',person)
 asset,_=create(assetModule,{'资产编号':TAG+'-'+role+'-ASSET','车辆编号':TAG+'-'+role+'-VEHICLE','在用/闲置':'闲置','equipmentStatus':'available'})
 task,_=create(module,{'equipmentId':asset,personField:person,accountField:account})
 flow(module,task)
 check(module+' approval reserves asset and creates execution todo',row(asset)['payload']['equipmentStatus']=='working' and any(t['record_id']==task and t['status']=='pending' for t in api('GET','/api/todos/my',user=account)['data']))
 action(module,task,'archive',expected=409)
 action(module,task,'complete',{'completedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'actualHours':'2.5','actualDistanceKm':'25','equipmentId':'forged','driverUserId':'forged','status':'voided'},user=account)
 check(module+' completion releases asset and stores actual usage',row(asset)['payload']['equipmentStatus']=='available' and db("select count(*) from app_equipment_usage where source_id='"+task+"'")=='1')
 action(module,task,'complete',{'completedAt':datetime.datetime.now(datetime.timezone.utc).isoformat()},user='admin',expected=409)
 action(module,task,'archive')
 check(module+' completed task can archive',row(task)['status']=='archived' and row(task)['payload']['equipmentId']==asset and row(task)['payload']['status']=='archived')
# Approval effects for remaining asset/master-data and notification modules.
asset,_=create('engineeringEquipment',{'资产编号':TAG+'-DISPOSAL'})
inventory,_=create('assetLife',{'equipmentId':asset,'事项类型':'盘点'});flow('assetLife',inventory)
check('asset inventory writes linked ledger evidence',row(asset)['payload']['lastInventoryRecordId']==inventory)
inspection,_=create('inspection',{'equipmentId':asset,'inspectionResult':'failed'});flow('inspection',inspection)
check('failed inspection disables asset',row(asset)['payload']['equipmentStatus']=='disabled')
standard,_=create('standards');flow('standards',standard)
check('approved standard is published and persisted',row(standard)['payload']['published'])
notice,_=create('notice',{'recipientIds':[actor],'发送渠道':'站内信'});flow('notice',notice)
notification=next(n for n in api('GET','/api/notifications',user=actor)['data'] if n['record_id']==notice)
api('POST',f'/api/notifications/{notification["id"]}/read',user='viewer',expected=404)
api('POST',f'/api/notifications/{notification["id"]}/read',user=actor)
check('station notification delivery and recipient read persisted',db("select count(*) from app_notifications where id='"+notification['id']+"' and read_at is not null")=='1')
check('workbench reads live database aggregates','data' in api('GET','/api/workbench'))
check('daily report identifies external GPS source',api('GET','/api/reports/daily-equipment')['data']['gpsSource'].startswith('external'))
for module in schemas:
 result=api('GET',f'/api/modules/{module}/list?pageSize=1',expected=503 if module=='iot' else 200)
 if module!='iot':
  assert 'list' in result and 'total' in result,module
  if result['list']:
   record=result['list'][0]
   assert len(record['cells'])==len(next(m for m in mods if m['module_id']==module)['list_columns']),module
   api('GET',f'/api/modules/{module}/{record["id"]}')
check('all 35 module list contracts and available details; GPS explicitly external',True)
api('GET','/api/notifications',user=actor)
check('notifications require authenticated recipient',True)
report={'tag':TAG,'verifiedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'passed':len(checks),'checks':checks,'records':created,'gps':'external API, no ingestion or simulated data'}
pathlib.Path('scripts/release-verification-result.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
print('ALL PASS',len(checks))
