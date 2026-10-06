#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate checked-in API reference and OpenAPI from an explicit Controller inventory + live DB metadata."""
import os,json,pathlib,re,datetime,urllib.request
root=pathlib.Path(__file__).resolve().parent.parent
base=os.getenv('API_BASE','http://127.0.0.1:8080').rstrip('/')
req=urllib.request.Request(base+'/api/modules',headers={'X-User-Id':os.getenv('DOC_USER','admin')})
with urllib.request.urlopen(req,timeout=30) as response:modules=json.load(response)['data']
(root/'scripts/api-module-schema.json').write_text(json.dumps(modules,ensure_ascii=False,indent=2))
obj={'type':'object','additionalProperties':True}
ref=lambda n:{'$ref':'#/components/schemas/'+n}
scopeValues=['company','project','warehouse','supplier','own','todo','department','projectWarehouse']
schemas={
 'Envelope':{'type':'object','required':['success','message'],'properties':{'success':{'type':'boolean'},'data':{},'message':{'type':'string'}},'additionalProperties':True},
 'Error':{'type':'object','required':['success','message','code'],'properties':{'success':{'type':'boolean','enum':[False]},'message':{'type':'string'},'code':{'type':'string'},'error':obj}},
 'RecordInput':{'type':'object','properties':{'title':{'type':'string'},'projectName':{'type':'string'},'payload':obj,'status':{'type':'string','description':'新增仅 draft；编辑不得改变当前状态'}},'additionalProperties':True,'description':'动态表单可平铺或放入 payload；必填、类型、选项由 GET /api/modules 的 form_schema 决定。草稿允许不完整，提交必须完整。'},
 'Record':{'type':'object','properties':{'id':{'type':'string','format':'uuid'},'recordNo':{'type':'string'},'title':{'type':'string'},'status':{'type':'string'},'payload':obj,'cells':{'type':'array','items':{}},'sourceId':{'type':'string','description':'投影来源 ID；不要把投影 ID 当作原业务记录/账号 ID'},'createdAt':{'type':'string','nullable':True},'updatedAt':{'type':'string','nullable':True}}},
 'ListData':{'type':'object','properties':{'list':{'type':'array','items':ref('Record')},'total':{'type':'integer'}}},
 'Login':{'type':'object','required':['account','password'],'properties':{'account':{'type':'string'},'password':{'type':'string','format':'password'}}},
 'UserInput':{'type':'object','required':['name'],'properties':{'id':{'type':'string','description':'创建必填；更新以路径 ID 为准'},'name':{'type':'string'},'enabled':{'type':'boolean','default':True},'projectName':{'type':'string','nullable':True},'warehouseId':{'type':'string','nullable':True},'supplierId':{'type':'string','nullable':True},'departmentId':{'type':'string','nullable':True},'roles':{'type':'array','items':{'type':'string'},'description':'提供时替换全部角色绑定'},'dataScope':{'type':'string','enum':scopeValues,'nullable':True}}},
 'RoleInput':{'type':'object','required':['name'],'properties':{'id':{'type':'string','description':'创建必填'},'name':{'type':'string'},'dataScope':{'type':'string','enum':scopeValues,'default':'own'}}},
 'Password':{'type':'object','required':['password'],'properties':{'password':{'type':'string','format':'password','minLength':12,'description':'最多72 UTF-8字节'},'currentPassword':{'type':'string','format':'password','description':'普通账号修改本人密码必填；system:manage 可重置'}}},
 'Permissions':{'type':'object','properties':{'permissions':{'type':'array','items':{'type':'string'},'description':'完整替换；空数组或省略 permissions 会清空授权'}}},
 'ApprovalNode':{'type':'object','required':['nodeName','assignees'],'properties':{'nodeName':{'type':'string'},'assignees':{'type':'array','minItems':1,'items':{'type':'string'}},'mode':{'type':'string','enum':['all','any'],'default':'any'},'minAmount':{'type':'number'},'maxAmount':{'type':'number'}}},
 'Template':{'type':'object','required':['moduleId','nodes'],'properties':{'moduleId':{'type':'string'},'name':{'type':'string','default':'审批模板'},'enabled':{'type':'boolean','default':True},'nodes':{'type':'array','minItems':1,'maxItems':20,'items':ref('ApprovalNode')}}},
 'Instance':{'type':'object','required':['businessType','businessId'],'properties':{'businessType':{'type':'string'},'businessId':{'type':'string','format':'uuid'}}},
 'Action':{'type':'object','properties':{'comment':{'type':'string'},'assignee':{'type':'string'},'targetStep':{'type':'integer','default':-1},'equipmentStatus':{'type':'string','enum':['available','disabled']},'contractEnded':{'type':'boolean'}}},
 'Filter':{'type':'object','properties':{'keyword':{'type':'string'},'status':{'type':'string'}}},
 'AttachmentMetadata':{'type':'object','required':['name','storageKey'],'properties':{'name':{'type':'string'},'storageKey':{'type':'string'}},'description':'兼容元数据登记，不上传文件，不能代替必填附件'},
 'Execution':{'type':'object','required':['completedAt'],'properties':{'completedAt':{'type':'string','format':'date-time'},'actualHours':{'type':'number','minimum':0,'default':0},'actualDistanceKm':{'type':'number','minimum':0,'default':0},'comment':{'type':'string'}}},
 'Confirm':{'type':'object','required':['amount'],'properties':{'amount':{'type':'string','pattern':r'^\d+(\.\d{1,2})?$'},'currency':{'type':'string','enum':['AED','SAR','USD','CNY'],'description':'省略沿用当前币种'}}},
 'Payment':{'type':'object','required':['amount','reference','paidAt'],'properties':{'amount':{'type':'string','pattern':r'^\d+(\.\d{1,2})?$','description':'必须大于0'},'reference':{'type':'string','maxLength':100},'paidAt':{'type':'string','format':'date-time'}}},
 'Reverse':{'type':'object','required':['reason','reference'],'properties':{'reason':{'type':'string'},'reference':{'type':'string'}}}
}
paths={}; catalog=[]
def param(name,typ='string',required=False,fmt=None,**extra):
 schema={'type':typ,**extra}
 if fmt:schema['format']=fmt
 return {'name':name,'in':'query','required':required,'schema':schema}
def add(method,path,title,group,permission,description='',body=None,params=None,binary=None,public=False):
 operation={'summary':title,'tags':[group],'operationId':method.lower()+'_'+re.sub(r'[^a-zA-Z0-9]+','_',path).strip('_'),'description':description+'\n权限：'+permission,'responses':{}}
 parameters=list(params or [])
 for n in re.findall(r'{(\w+)}',path):
  uuid=n=='id' and not path.startswith(('/api/users/','/api/roles/'))
  parameters.insert(0,{'name':n,'in':'path','required':True,'schema':{'type':'string',**({'format':'uuid'} if uuid else {})}})
 if parameters:operation['parameters']=parameters
 if public:operation['security']=[]
 if body:
  operation['requestBody']={'required':True,'content':{'application/json':{'schema':ref(body)}}}
 if binary:success={'description':'成功，返回文件字节','content':{binary:{'schema':{'type':'string','format':'binary'}}}}
 else:success={'description':'成功；响应具体字段见中文接口文档','content':{'application/json':{'schema':ref('Envelope')}}}
 operation['responses']['200']=success
 for code,label in [('400','参数无效'),('401','未认证或令牌失效'),('403','权限不足或禁止模拟身份'),('404','不存在或超出数据范围'),('409','非法状态或业务冲突'),('500','服务端错误'),('503','服务不可用；GPS 为外部 API')]:operation['responses'][code]={'description':label,'content':{'application/json':{'schema':ref('Error')}}}
 paths.setdefault(path,{})[method.lower()]=operation
 catalog.append((group,method,path,title,permission,body or '—'))
 return operation
add('GET','/health','数据库健康检查','基础','无需身份',public=True)
add('GET','/api/modules','可见模块、字段、列和流程元数据','基础','各模块 view','开发匿名仅工程设备；正式环境必须认证。返回 data 数组。')
listparams=[param('page','integer',default=1,minimum=1,maximum=1000000),param('pageSize','integer',default=10,minimum=1,maximum=100),param('keyword'),param('status')]
add('GET','/api/modules/{moduleId}/list','分页列表','业务记录','{moduleId}:view','返回外层 list/total 和 data.list/data.total；cells 顺序对应 list_columns。approvalType 当前不支持服务端筛选。',params=listparams)
add('GET','/api/modules/{moduleId}/{id}','记录详情','业务记录','{moduleId}:view + 范围','投影模块 id 是列表显示 ID；其原始 ID 通过 sourceId 或 payload 中 record_id 取得。')
add('POST','/api/modules/{moduleId}','新增草稿','业务记录','{moduleId}:create','返回 record 和 data；动态业务字段见元数据。系统投影模块不能普通写入。',body='RecordInput')
for method in ['PUT','PATCH']:add(method,'/api/modules/{moduleId}/{id}','合并编辑','业务记录','{moduleId}:edit','只允许 draft/rejected；PUT 也是合并更新，不是整条替换；不提供强制版本号/ETag 并发校验。',body='RecordInput')
add('DELETE','/api/modules/{moduleId}/{id}','软删除','业务记录','{moduleId}:delete','仅 draft/rejected/voided/archived；占用设备禁止删除。成功仅 success/message，不保证 data 字段。')
for action,title in [('saveDraft','保存草稿'),('submit','提交并创建审批实例'),('approve','审批当前任务'),('reject','驳回当前任务'),('void','作废并取消审批'),('archive','归档'),('startReview','进入审核中')]:
 op=add('POST','/api/modules/{moduleId}/{id}/'+action,title,'状态流转','{moduleId}:'+('edit' if action=='saveDraft' else 'approve' if action=='startReview' else action),'以 app_workflow_transitions 为准；approve/reject 仍按当前审批任务授权，不绕过审批。',body=None if action=='submit' else 'RecordInput' if action=='saveDraft' else 'Action')
 if 'requestBody' in op:op['requestBody']['required']=False
op=add('POST','/api/modules/{moduleId}/import','整批导入','导入导出','import + create + view','JSON数组或 multipart(file)，xls/xlsx/json，最多500条/5MB；错误整批回滚。')
op['requestBody']={'required':True,'content':{'application/json':{'schema':{'type':'array','minItems':1,'maxItems':500,'items':ref('RecordInput')}},'multipart/form-data':{'schema':{'type':'object','required':['file'],'properties':{'file':{'type':'string','format':'binary'}}}}}}
for suffix,title,mime in [('export','JSON导出',None),('export/excel','XLSX导出','application/vnd.openxmlformats-officedocument.spreadsheetml.sheet')]:
 op=add('POST','/api/modules/{moduleId}/'+suffix,title,'导入导出','{moduleId}:export + view','按 keyword/status 和用户范围导出，最多10000条。',body='Filter',binary=mime);op['requestBody']['required']=False
add('POST','/api/auth/login','密码登录','认证','无需已登录身份','account 也兼容 username。成功 data.token/tokenType/expiresIn/user。账号5次失败锁15分钟；IP15分钟20次失败限流429。',body='Login',public=True)
add('POST','/api/auth/logout','退出并撤销当前令牌','认证','已认证')
add('GET','/api/auth/me','当前身份、角色和范围','认证','已认证','data.id/account/name/profile/role/roleName/roles/dataScope/approvalNodes。')
add('GET','/api/auth/permissions','当前账号权限点','认证','已认证','data 是权限对象数组，包含 id/module_id/action；多角色可能重复，客户端去重。')
for typ,schema,label in [('users','UserInput','账号'),('roles','RoleInput','角色')]:
 for method,suffix,title,body in [('GET','','列表',None),('POST','','创建',schema),('GET','/{id}','详情',None),('PUT','/{id}','更新',schema)]:add(method,'/api/'+typ+suffix,label+title,'账号角色','system:manage','写入使用驼峰字段，查询数据库字段为下划线；账号/角色 ID 是字符串。',body=body)
add('POST','/api/users/{id}/password','设置或修改密码','账号角色','本人正确原密码，或 system:manage','成功撤销该账号全部会话；最少12字符、最多72 UTF-8字节。',body='Password')
add('GET','/api/roles/{id}/permissions','角色权限点','账号角色','system:manage')
add('POST','/api/roles/{id}/permissions','替换角色全部权限','账号角色','system:manage','不是增量授权；缺省或空数组清空。permission ID 必须存在。',body='Permissions')
for method,suffix,title,body,perm in [('GET','','模板列表',None,'view'),('POST','','创建模板','Template','edit'),('PUT','/{id}','更新模板','Template','edit')]:add(method,'/api/approval/templates'+suffix,title,'审批','approvalTemplate:'+perm,'开启模板会停用同模块原启用模板；节点1–20个，实际提交时快照。',body=body)
add('GET','/api/approval/flow','查看最新审批实例','审批','源模块 view + 创建者/参与者或记录范围','无实例时 data={nodes:[],tasks:[]}；有实例时含 id/record_id/module_id/current_step/current_cycle/status/nodes/tasks。',params=[param('businessType',required=True),param('businessId',required=True,fmt='uuid')])
add('POST','/api/approval/instances','提交源单据并创建审批实例','审批','源模块 submit + view','等同源模块 submit；不是脱离源单据新建审批。返回业务状态变更结果。',body='Instance')
for action,title in [('approve','审批通过'),('reject','驳回'),('return','退回'),('transfer','转交'),('addSign','加签')]:
 op=add('POST','/api/approval/tasks/{id}/'+action,title,'审批','源模块 '+('reject' if action in ['reject','return'] else 'approve')+' + 本人当前待办','return: targetStep=-1退回申请人，非负退回更早节点；transfer/addSign必填assignee；comment可选。响应 data 为最新审批流，不是源业务记录。',body='Action');op['requestBody']['required']=action in ['transfer','addSign']
 if action in ['transfer','addSign']:op['requestBody']['content']['application/json']['schema']={'allOf':[ref('Action'),{'type':'object','required':['assignee']}]}
add('GET','/api/audit/logs','查询审计','审计待办','system:audit','四项筛选精确匹配，按日志id倒序，最多500条；没有分页参数。',params=[param(n) for n in ['moduleId','recordId','operator','action']])
add('GET','/api/todos','全部待办','审计待办','system:manage','最多500条。')
add('GET','/api/todos/my','本人待办','审计待办','已认证','返回本人待办含已完成项；客户端按 status 筛选。')
add('POST','/api/todos/{id}/complete','完成普通待办','审计待办','本人任务','审批中任务须审批/驳回；执行任务须用 complete 接口真实回报；不能用此接口跳过业务动作。')
op=add('POST','/api/attachments','真实文件上传','附件','源模块 edit + view + 范围','1字节–5MB；data.id/name/size。生成磁盘 UUID 和 SHA-256 元数据；不接受前端文件名充当已上传附件。')
op['requestBody']={'required':True,'content':{'multipart/form-data':{'schema':{'type':'object','required':['moduleId','recordId','file'],'properties':{'moduleId':{'type':'string'},'recordId':{'type':'string','format':'uuid'},'file':{'type':'string','format':'binary'}}}}}}
add('GET','/api/attachments','附件元数据列表','附件','源模块 attachments + view + 范围',params=[param('moduleId',required=True),param('recordId',required=True,fmt='uuid')])
for kind in ['preview','download']:add('GET','/api/attachments/{id}/'+kind,'预览' if kind=='preview' else '下载','附件','源模块 attachments + view + 范围','实际 MIME 可能为 application/pdf、image/png、image/jpeg、text/plain 或 application/octet-stream。无实体文件返回404。',binary='application/octet-stream')
add('DELETE','/api/attachments/{id}','软删除附件','附件','源模块 edit + view + 范围','保留文件用于存档，禁止继续下载。')
add('GET','/api/modules/{moduleId}/{id}/attachments','兼容附件列表','附件','{moduleId}:attachments + view')
add('POST','/api/modules/{moduleId}/{id}/attachments','兼容附件元数据登记','附件','{moduleId}:edit + view','只登记 name/storageKey，不上传文件；不能满足必填附件。优先使用真实 upload。',body='AttachmentMetadata')
add('POST','/api/modules/{moduleId}/{id}/complete','执行任务完成','执行任务','{moduleId}:complete + view + 范围 + 实际执行账号或 system:manage','只支持 vehicleDispatch/machineDispatch，须 approved，完成后释放占用并保存 usage；不重复完成。',body='Execution')
add('GET','/api/finance/settlements','结算预留列表','财务','expensePaymentRecord:view + 来源记录范围','最多500条；source_id是源业务记录UUID，结算id与源业务id不同。')
add('POST','/api/finance/settlements/{id}/confirm','确认应付金额','财务','expensePaymentRecord:approve + 源模块 view/范围','不低于已付金额；已付款不能改变币种。返回结算数据库字段。',body='Confirm')
add('POST','/api/finance/settlements/{id}/payments','登记银行付款','财务','expensePaymentRecord:approve + 源模块 view/范围','不是自动银行划款。reference全局唯一；同结算同金额重试返回既有付款；超过余额409。',body='Payment')
add('GET','/api/finance/settlements/{id}/payments','付款与冲销记录','财务','expensePaymentRecord:view + 源模块 view/范围','包含 reversal_id/reversal_reason。')
add('POST','/api/finance/payments/{id}/reverse','冲销一笔付款','财务','expensePaymentRecord:void + 源模块 view/范围','每笔仅一次，退款流水号唯一；更新余额与付款记录voided；返回data.id冲销UUID。',body='Reverse')
for path in ['/api/workbench','/api/dashboard/stats']:add('GET',path,'工作台真实统计','统计通知','dashboard:view','data.moduleStatusCounts/myPendingTodos/unreadNotifications/source。')
add('GET','/api/reports/daily-equipment','指定日期设备分析','统计通知','dailyReport:view + 授权范围','默认服务器当前日期；工时里程来自执行回报，GPS保持外部来源。',params=[param('date',fmt='date')])
add('GET','/api/notifications','本人通知','统计通知','notice:view','最多500条；其他账号通知不返回。')
add('POST','/api/notifications/{id}/read','本人通知已读','统计通知','notice:view + 本人接收','重复标记保持原read_at，别人的通知404。')
openapi={'openapi':'3.0.3','info':{'title':'Severe Equipment Assets 后端 API','version':'0.1.0','description':'依据当前 Java Controller/Service 和运行数据库元数据生成。当前无正式服务器/域名；localhost 可完整本地联调。Header身份仅开发，生产只接受Bearer。GPS使用外部API。'},'servers':[{'url':base,'description':'本地 Java 后端'}],'security':[{'BearerAuth':[]},{'DevIdentity':[]}],'paths':paths,'components':{'securitySchemes':{'BearerAuth':{'type':'http','scheme':'bearer','description':'POST /api/auth/login 返回的数据库会话令牌'},'DevIdentity':{'type':'apiKey','in':'header','name':'X-User-Id','description':'只限允许Header模拟身份的开发环境。生产禁用。'}},'schemas':schemas},'x-module-metadata':modules}
(root/'openapi.json').write_text(json.dumps(openapi,ensure_ascii=False,indent=2))
# Keep a human-readable catalog in generation order.
text=['# Severe Equipment Assets API 接口文档','',f'生成日期：{datetime.date.today().isoformat()}。依据当前 Java Controller/Service 和本地运行数据库元数据。','',f'接口数量：{len(catalog)} 个 HTTP 操作，{len(paths)} 个路径模板。动态 moduleId 覆盖35个元数据模块；这不是35套独立Controller。','', '本地后端：`'+base+'`；前端：`http://localhost:5173/workbench`；数据库：`severe_equipment_assets`。当前未购买服务器/域名，所有联调可以先在本机完成。GPS接口由外部系统提供。','', '配套文件：`openapi.json`（可导入接口工具）、`api.http`（IntelliJ请求样例）、`scripts/api-module-schema.json`（本次运行元数据快照）。','', '## 1. 认证、数据范围与返回约定','', '推荐所有业务请求发送 `Authorization: Bearer <token>`。先调用 `POST /api/auth/login` 获取令牌。开发阶段也可用 `X-User-Id`，必须对应数据库启用账号；生产禁止模拟身份。`X-User-Role` 只能选已绑定角色，`X-Data-Scope` 只能使用授权范围，不是前端可自行提升的权限。','', '| 范围值 | 含义 |','|---|---|']
for key,label in [('company','全公司'),('project','所属项目'),('warehouse','所属仓库'),('supplier','所属供应商'),('own','本人创建'),('todo','本人申请/待办及关联设备'),('department','本部门'),('projectWarehouse','所属项目与仓库交集')]:text.append(f'| `{key}` | {label} |')
text+=['','JSON成功常规返回：','```json','{"success":true,"data":{},"message":""}','```','','列表保留外层 `list/total`，详情保留外层记录字段，变更保留 `record`，便于现有客户端兼容。删除接口可能仅有 success/message。附件与Excel成功返回二进制。不要假设所有响应只需读取 data。','','| HTTP | 含义 |','|---|---|','| 200 | 请求成功；新增当前也使用200，不是201 |','| 400 | 参数/必填/日期/选项/文件格式错误 |','| 401 | 未登录、无效/过期/已撤销令牌 |','| 403 | 按钮权限不足、禁止模拟身份、伪造角色/范围 |','| 404 | 记录不存在或不在范围内；用于隐藏存在性 |','| 409 | 状态、唯一约束、库存不足、占用、超额付款等业务冲突 |','| 413 | 上传请求超过服务端大小限制 |','| 429 | 登录IP失败次数过多 |','| 500 / 503 | 服务端错误/暂时不可用；iot数据接口明确提示外部来源 |','','```json','{"success":false,"message":"无权限操作","code":"FORBIDDEN","error":{"code":"FORBIDDEN","message":"无权限操作"}}','```','','## 2. 全量接口清单','','权限表达式表示后端检查，不是依赖前端隐藏按钮。未单列身份的业务接口也必须识别身份。每条权限还受记录范围及当前状态限制。','','| 类别 | 方法 | 路径 | 用途 | 权限 | 请求体模型 |','|---|---|---|---|---|---|']
for group,method,path,title,permission,body in catalog:text.append(f'| {group} | {method} | `{path}` | {title} | {permission} | {body} |')
text+=['','## 3. 请求模型与示例','','模型中的JSON示例是调用结构，示例ID必须替换为真实ID。动态表单字段与选项以运行时 `/api/modules` 为准。']
examples={
'Login':{'account':'your-account','password':'your-real-password'},
'RecordInput':{'title':'设备草稿','projectName':'PROJECT-001','payload':{'资产编号':'EQ-202610-001','生产厂家':'设备制造商','设备型号':'MODEL-A','设备规格':'规格说明','采购单位':'采购单位','管理单位':'保管单位','现设备地点':'仓库A','采购/到场日期':'2026-10-05','在用/闲置':'闲置','完好状态':'正常'}},
'UserInput':{'id':'operator-001','name':'操作员姓名','enabled':True,'roles':['operator'],'projectName':'PROJECT-001','warehouseId':'WH-001','supplierId':None,'departmentId':'DEPT-001','dataScope':'todo'},
'RoleInput':{'id':'project-reviewer','name':'项目审批人','dataScope':'project'},
'Password':{'currentPassword':'original-password','password':'new-strong-password'},
'Permissions':{'permissions':['engineeringEquipment:view','engineeringEquipment:create','engineeringEquipment:submit']},
'Template':{'moduleId':'engineeringEquipment','name':'设备审批','enabled':True,'nodes':[{'nodeName':'设备主管初审','assignees':['reviewer-001'],'mode':'any'},{'nodeName':'终审','assignees':['reviewer-002','reviewer-003'],'mode':'all','minAmount':0}]},
'Instance':{'businessType':'engineeringEquipment','businessId':'00000000-0000-0000-0000-000000000001'},
'Action':{'comment':'审核说明'},
'Filter':{'keyword':'EQ-','status':'approved'},
'AttachmentMetadata':{'name':'历史证照.pdf','storageKey':'legacy-storage-key'},
'Execution':{'completedAt':'2026-10-05T10:30:00+04:00','actualHours':2.5,'actualDistanceKm':25,'comment':'实际完成'},
'Confirm':{'amount':'100.00','currency':'AED'},
'Payment':{'amount':'40.00','reference':'REAL-BANK-REFERENCE','paidAt':'2026-10-05T10:30:00+04:00'},
'Reverse':{'reason':'退款原因','reference':'REAL-REFUND-REFERENCE'}}
for name in ['Login','RecordInput','UserInput','RoleInput','Password','Permissions','Template','Instance','Action','Filter','Execution','Confirm','Payment','Reverse','AttachmentMetadata']:
 schema=schemas[name];text+=['',f'### {name}','','| 字段 | 类型 | 必填 | 说明 |','|---|---|---|---|']
 for key,value in schema.get('properties',{}).items():
  note=value.get('description','')
  if value.get('enum'):note+=' 可选：'+', '.join(value['enum'])
  if 'default' in value:note+=' 默认：'+str(value['default'])
  text.append(f'| `{key}` | {value.get("type","object")} | {"是" if key in schema.get("required",[]) else "否"} | {note} |')
 text+=['','```json',json.dumps(examples[name],ensure_ascii=False,indent=2),'```']
text+=['','UserInput/RoleInput：创建额外要求 id。用户PUT会将未提供的 projectName/warehouseId/supplierId/departmentId 写为NULL，enabled默认true；请先读取用户，带齐需要保留的属性。roles提供时完全替换，省略则保留。角色PUT省略dataScope会使用own。账号读接口使用project_name等下划线字段，不会返回password_hash。普通用户没有system:manage时不能通过账号管理GET读取自己，使用/auth/me。','','RecordInput：示例是工程设备提交所需字段的当前形状；其他模块不可套用。首次POST只保存草稿；审批状态必须使用动作接口，不能PUT status=approved。PUT/PATCH都合并，不是整条替换；没有强制乐观版本参数。每次完整提交校验SchemaService和业务联动规则。','','Action 的不同用途：','','| 操作 | 附加参数 | 行为 |','|---|---|---|','| saveDraft | 平铺业务字段或payload对象 | 合并草稿，rejected可回draft |','| approve/reject | comment可选 | 处理本人当前任务；多级approve不一定使源记录approved |','| return | targetStep，默认-1；comment | -1返回申请人，非负必须小于当前step |','| transfer/addSign | assignee必填；comment可选 | 必须真实启用且有源模块审批权限；加签改为会签 |','| maintenance/workorder最终approve | equipmentStatus=available或disabled，可省略 | 控制设备维修后运行状态 |','| rental/contract archive | contractEnded=true | 确认合同结束，释放该合同设备占用 |','| void | 无必填体 | 按规则取消源单据与待办 |','','## 4. 返回数据模型','','### 4.1 登录与当前身份','','登录：`data.token`、`tokenType=Bearer`、`expiresIn`（默认43200秒）、`user`。`/auth/me` 返回 id/account/name/profile、role/roleName、roles对象数组、dataScope、approvalNodes。`/auth/permissions`返回对象数组，例如：','','```json','{"success":true,"data":[{"id":"engineeringEquipment:view","module_id":"engineeringEquipment","action":"view"}],"message":""}','```','','### 4.2 模块元数据与记录','','模块 data 数组每项包含 module_id/module_name/group/route信息、list_columns、form_schema、record_counts、workflow_states和workflow_transitions。实际列名有module_group、route_path等，不统一转为驼峰。form_schema含field_key、field_label、field_type、db_type、sample_value、options、required、sort_order。sample_value是表单提示，不是真实业务默认值或审批证据。','','```json','{"success":true,"list":[{"id":"00000000-0000-0000-0000-000000000001","recordNo":"ENGINEER-...","title":"设备草稿","status":"draft","payload":{"资产编号":"EQ-202610-001"},"cells":["按元数据列顺序排列"],"createdAt":"2026-10-05T10:00:00+04:00","updatedAt":"2026-10-05T10:00:00+04:00"}],"total":1,"data":{"list":["同一记录对象"],"total":1},"message":""}','```','','上例为结构示意；实际data.list与外层list相同，均为记录对象数组。详情的data是记录本身，并兼容外层id/recordNo/title/status/payload/cells。新增/编辑/通用状态操作通常同时有record与data，审批任务接口返回data为审批流。','','投影模块会额外有sourceId：users/roleperm的列表UUID是展示ID，账号/角色管理必须使用sourceId字符串。approval/expenseTodo列表id是taskId；源业务记录是payload.record_id，源模块是payload.module_id。expensePaymentRecord列表id是付款存档记录ID，payments操作使用payment_id或payload.paymentId；不要混用。dailyReport展示ID由日期生成，只在该日期对应报表存在时可再取详情。','','### 4.3 审批流与待办','','审批流data：id（instanceId）、record_id、module_id、template_id、nodes、current_step、current_cycle、status、created_by、created_at、updated_at、tasks。tasks项：id、instance_id、step、cycle、assignee、status、comment、completed_at、created_at、todo_id。判断有效待办需实例pending、任务pending且step/cycle等于当前实例。','','任务批准的响应示意：','','```json','{"success":true,"data":{"id":"审批实例UUID","record_id":"源记录UUID","module_id":"engineeringEquipment","current_step":1,"current_cycle":1,"status":"pending","nodes":[{"nodeName":"终审","assignees":["reviewer-002"],"mode":"any"}],"tasks":[]},"message":"审批操作已存档"}','```','','/todos项：id、record_id、assignee、status、created_at、completed_at。todoId、taskId、recordId是不同ID，审批不能调用/todos/{id}/complete替代。','','### 4.4 审计','','data日志数组：id（数值）、operator、role、module_id、record_id、action、before_status、after_status、before_data、after_data、created_at、request_no、result、error_message。action参数精确匹配，例如create/edit/submit/approve/reject/void/archive/import/export或approvalTask:approve。后端也记录敏感查询、附件和跨模块动作。','','### 4.5 附件与导入导出','','上传data：id、name、size；列表data项：id、name、storage_key、created_at。multipart参数名必须为moduleId、recordId、file。必填file表单字段填本单据已上传附件UUID或UUID数组，不填本地路径。下载/预览为二进制，客户端responseType=blob。文件名不能当作上传完成证据。','','import返回data.batchId、recordIds；记录为draft。export返回data.records、total，响应为JSON，不能把这个端点的blob直接命名为.xlsx。下载Excel使用/export/excel。Excel表头使用业务字段名/field_key，拒绝公式、空或重复表头；最多200列、500行导入，10000行导出。','','### 4.6 财务、执行和通知','','settlements data数组或confirm data对象：id、source_id、payload、status、amount、currency、paid_amount、created_at。amount可能NULL，必须先confirm再pay。status为pending/partial/paid。付款data：id、settlement_id、amount、reference、paid_at、created_by、created_at；付款查询还含reversal_id/reversal_reason。reverse返回data.id（冲销UUID）。','','complete返回data.completedAt/actualHours/actualDistanceKm/comment?/executionStatus=completed，源单据审批状态仍approved，之后才archive。complete仅支持两种执行调度；不是GPS实时数据上报。','','notifications项：id、user_id、title、message、record_id、read_at、created_at。已读动作返回data={}。来源通知发布仍由notice业务草稿提交审批；已配置渠道仅站内信，短信/邮件/APP推送会被明确拒绝。','','workbench data：moduleStatusCounts（module_id/status/count）、myPendingTodos、unreadNotifications、source=PostgreSQL。daily data：date、updatedRecordCounts、reportedWorkHours、reportedDistanceKm、maintenanceDocuments、gpsSource（外部API、不采集）。','','## 5. 状态规则与模块业务补充','','普通业务状态与设备状态分别存储。以模块元数据workflow_transitions为准，当前通用规则：','','| 起始 | 动作 | 目标 |','|---|---|---|','| draft/rejected | saveDraft | draft |','| draft/rejected | submit | submitted |','| submitted | startReview | reviewing |','| submitted/reviewing | approve | approved（只在最终审批节点） |','| submitted/reviewing | reject | rejected |','| draft/rejected/submitted/reviewing | void | voided |','| approved/voided | archive | archived |','','通用DELETE仅draft/rejected/voided/archived；占用设备禁止。write失败全部回滚；支付、设备、库存联动成功与源状态同事务。','','| 场景 | 业务字段及前置条件 |','|---|---|','| 合格供应商 | supplierId=已approved供应商记录UUID；新供应商提交先上传必填证照 |','| 设备入库 | assetNo或资产编号、supplierId；assetType=vehicle/车辆写车辆台账，其他写工程设备；型号/厂家/位置随payload归档 |','| 备件入库 | partNo、warehouseId、quantity>0；写库存及存档 |','| 备件调拨/出库 | stockAction=transfer/outbound、partNo、warehouseId、quantity；transfer另需targetWarehouseId且不能同仓 |','| 普通设备调拨 | equipmentId、location、custodian；同步设备位置与保管单位 |','| 维修/作业 | equipmentId；提交占用，完成审批释放；作业工单workType=operation |','| 出租/租赁合同 | equipmentId、amount、startDate/endDate（YYYY-MM-DD）、lessee；不能占用已被另一单据占用设备 |','| 退租 | equipmentId、contractId；必须匹配该合同当前设备占用 |','| 车辆执行 | equipmentId车辆台账、driverId人员资质UUID、driverUserId账号ID；资质userId与账号匹配、未过期 |','| 工程设备执行 | equipmentId工程台账、operatorId资质UUID、operatorUserId账号ID；资质已approved、未过期 |','| 人员资质 | 人员类型、证照/保险有效期；userId可绑定执行账号 |','| 资产事项 | 事项类型=盘点/调拨/减值/报废/处置；减值需carryingValue，调拨需location/custodian |','| 检查 | equipmentId、inspectionResult=passed/failed；失败停用；占用中的设备须先结束业务 |','| 通知发布 | recipientIds真实账号数组、发送渠道=站内信，其他元数据必填字段完整 |','','## 6. 本地联调顺序','','1. 运行 ./scripts/run-dev.sh；访问/health确认数据库。','2. 用真实密码登录取得token；查询/auth/me与/auth/permissions。','3. GET /api/modules，取moduleId的form_schema与list_columns。','4. POST业务草稿取得record.id；上传必填附件，PATCH附件UUID及完整表单。','5. POST /modules/{moduleId}/{id}/submit。','6. 审批人GET /approval/flow取得本人有效taskId，再调用tasks动作；多级逐节点完成。','7. 查询源记录、目标台账/库存/结算、audit/logs；确认联动一致。','8. 用受限账号复测403、范围外404、非法状态409。','9. 使用对应执行账号回报完成；真实银行流水登记与冲销使用财务账号。','','```bash','curl --fail http://127.0.0.1:8080/health','curl --fail -H "Authorization: Bearer $TOKEN" http://127.0.0.1:8080/api/modules','curl --fail -H "Authorization: Bearer $TOKEN" "http://127.0.0.1:8080/api/modules/engineeringEquipment/list?page=1&pageSize=1"','```','','localhost与127.0.0.1的5173前端跨域已允许。不需要购买域名即可本地验证。正式部署时再配置DB_URL/DB_USERNAME/DB_PASSWORD、prod认证、持久附件目录、/api反代与HTTPS。正式账户凭据不写入本文档。','','## 7. 前端 mock 的准确含义与接线差异','','本次只核对 ../WEB_Equipment_Assets，未修改任何前端文件。','','| 功能 | 当前源码 | 当前行为 | 接入目标 |','|---|---|---|---|','| 登录 | src/stores/app.ts:1162；src/api/auth.ts | mockApprovalUsers匹配账号密码，localStorage保存静态登录标记；auth函数backendPending | POST/auth/login，保留真实token；GET/auth/me/permissions；logout撤销会话 |','| 审批 | src/api/approval.ts | 查询/approve/reject/return/transfer/addSign返回backendPending，不发真实HTTP | /api/approval/flow、instances、tasks/{id}/动作；响应data按本文处理 |','| 附件 | src/api/attachment.ts | upload/list/preview/download/delete均占位 | multipart真实上传与授权下载，保存真实附件UUID |','| 列表及基础CRUD | src/api/modules.ts | enableBackend=true时已有HTTP分支；false时读pages原型rows | 使用实际Java地址与身份；这些功能并非一概mock |','| API地址与身份请求 | .env.local；src/api/http.ts | baseURL=http://127.0.0.1:3100/api（该端口有Node服务）；axios当前没有Bearer注入 | 核对3100是否为代理；或直连8080/api/代理/api；登录token加入Authorization |','','localStorage本身不是错误：真实系统也可缓存语言或客户端登录状态。问题是当前身份来源为前端模拟账号，没有服务端密码验证和会话；关闭页面/刷新后显示“已登录”不等于数据库授权。后端真实接口已经存在，但前端登录、审批、附件还没有发出对应请求，因此仅修改数据库或后端不能让这些占位函数自动工作。接线只需API/认证状态/事件数据绑定层，页面布局、菜单路由、UI样式可以保持。本文没有擅自修改前端范围。','','客户端其他差异：auth.permissions占位签名是string[]，后端实际返回权限对象数组；approval.getApprovalFlow占位签名是节点数组，后端实际返回包含nodes/tasks的实例对象；模块导出当前调用/export，返回JSON，Excel应调用/export/excel；approvalType查询参数尚未服务端实现，不能把它当作有效筛选。','','## 8. 验收证据与范围','','证据文件：scripts/verification-result.json（50项）；scripts/release-verification-result.json（34项）；scripts/security-verification-result.json（6项）。空库8个迁移版本在隔离schema通过验证后回滚。以上是已有本地验收结果，不代表逐个接口每一种组合都被覆盖，也不代表前端页面联调或正式服务器部署已完成。','','## 附录：35个模块与动态表单字段','','本附录来自本次运行的 /api/modules，数据库新增/修改字段后以运行接口为准。系统投影模块仍保留原元数据表单定义，但不能用普通CRUD维护；应使用专用接口。']
readonly={'users','roleperm','approval','expenseTodo','expenseMyApply','audit','approvalTemplate','dashboard','dailyReport','expensePaymentRecord'}
for module in modules:
 mid=module['module_id'];name=module['module_name'];mode='外部GPS，数据操作503' if mid=='iot' else '只读投影，写入走专用接口' if mid in readonly else '业务草稿/流程/存档'
 text+=['',f'### {mid} — {name}','',f'模式：{mode}。']
 cols=module.get('list_columns',[])
 text+=['','cells列顺序：'+(' → '.join(str(c['column_label']) for c in cols) if cols else '无列表列')+'。']
 fields=module.get('form_schema',[])
 if fields:
  text+=['','| field_key | 标签 | 类型 | 提交必填 | 选项 |','|---|---|---|---|---|']
  for f in fields:text.append('| `'+f['field_key']+'` | '+f['field_label'].replace('|','\\|')+' | '+f['field_type']+' | '+('是' if f['required'] else '否')+' | '+json.dumps(f.get('options') or [],ensure_ascii=False).replace('|','\\|')+' |')
for index,line in enumerate(text):
 if line.startswith('{"success":true,"list":'):
  example=json.loads(line);example['data']['list']=example['list'];text[index]=json.dumps(example,ensure_ascii=False,indent=2)
(root/'API接口文档.md').write_text('\n'.join(text)+'\n')
print(json.dumps({'operations':len(catalog),'paths':len(paths),'modules':len(modules),'documents':['API接口文档.md','openapi.json','scripts/api-module-schema.json']},ensure_ascii=False))
