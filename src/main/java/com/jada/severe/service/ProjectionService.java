package com.jada.severe.service;

import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.nio.charset.StandardCharsets;

/** Read models come from authoritative tables, never prototype records. */
@Service
public class ProjectionService {
 @org.springframework.beans.factory.annotation.Autowired private ReportService reports;
 private final JdbcTemplate db; private final AccessService access; private final AuditService audit;
 @org.springframework.beans.factory.annotation.Value("${app.include-test-data:true}") private boolean tests;
 public ProjectionService(JdbcTemplate db,AccessService access,AuditService audit){this.db=db;this.access=access;this.audit=audit;}
 public boolean handles(String module){return Set.of("users","roleperm","approval","expenseTodo","expenseMyApply","audit","approvalTemplate","dashboard","dailyReport","expensePaymentRecord").contains(module);}
 public void writable(String module){if(handles(module))throw new ResponseStatusException(HttpStatus.CONFLICT,"该模块使用专用账号、权限、审批或审计接口维护");}
 public Map<String,Object> list(String module,int page,int size,String keyword,String status){
  if(page<1||size<1||size>100||page>1000000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"分页参数超出范围");
  List<Map<String,Object>> rows=rows(module);var columns=db.queryForList("select column_key,column_label from app_module_columns where module_id=? order by sort_order",module);
  var records=new ArrayList<Map<String,Object>>();for(var row:rows){String state=Objects.toString(row.get("status"),"active");if(status!=null&&!status.isBlank()&&!Set.of("全部","全部状态").contains(status)&&!status.equals(state))continue;
   if(keyword!=null&&!keyword.isBlank()&&!audit.json(row).toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT)))continue;
   String key=row.get("id").toString();UUID id;try{id=UUID.fromString(key);}catch(IllegalArgumentException ex){id=UUID.nameUUIDFromBytes((module+":"+key).getBytes(StandardCharsets.UTF_8));}
   var record=new LinkedHashMap<String,Object>();record.put("id",id);record.put("sourceId",key);record.put("recordNo",row.getOrDefault("record_no",key));record.put("title",row.getOrDefault("title",row.getOrDefault("name",key)));record.put("status",state);record.put("payload",row);
   var cells=new ArrayList<Object>();for(var c:columns){String label=c.get("column_label").toString();cells.add(Set.of("状态","当前状态").contains(label)?state:row.getOrDefault(label,row.getOrDefault(c.get("column_key").toString(),"")));}record.put("cells",cells);record.put("createdAt",Objects.toString(row.get("created_at"),null));record.put("updatedAt",Objects.toString(row.get("updated_at"),null));records.add(record);
  }
  int from=Math.min(records.size(),(page-1)*size),to=Math.min(records.size(),from+size);return Map.of("list",records.subList(from,to),"total",records.size());
 }
 public Map<String,Object> get(String module,UUID id){for(int page=1;;page++){var result=list(module,page,100,null,null);for(Object row:(List<?>)result.get("list")){var r=(Map<String,Object>)row;if(id.equals(r.get("id")))return r;}if(page*100>=((Number)result.get("total")).intValue())break;}throw new ResponseStatusException(HttpStatus.NOT_FOUND,"记录不存在或不在数据范围内");}
 private List<Map<String,Object>> rows(String module){
  if(module.equals("dashboard")){var row=new LinkedHashMap<String,Object>(reports.dashboard());row.put("id","dashboard");row.put("title","工作台实时统计");row.put("status","active");return List.of(row);}
  if(module.equals("dailyReport")){var row=new LinkedHashMap<String,Object>(reports.daily(java.time.LocalDate.now()));row.put("id",row.get("date"));row.put("报告日期",row.get("date"));row.put("分析对象","已授权设备业务");row.put("核心指标",Map.of("workHours",row.get("reportedWorkHours"),"distanceKm",row.get("reportedDistanceKm")));row.put("status","generated");return List.of(row);}

  if(module.equals("users")){access.require("system","manage");return db.queryForList("select u.id,u.name,u.id as 账号,case when u.enabled then 'enabled' else 'disabled' end status,coalesce(u.project_name,u.department_id,u.supplier_id,u.warehouse_id) as 所属主体,(select string_agg(r.name,',') from app_user_roles ur join app_roles r on r.id=ur.role_id where ur.user_id=u.id) as 账号类型 from app_users u"+(tests?"":" where not u.is_test")+" order by u.id");}
  if(module.equals("roleperm")){access.require("system","manage");return db.queryForList("select r.id,r.name,r.name as 角色模板,r.data_scope as 数据范围,(select string_agg(distinct p.module_id,',') from app_role_permissions rp join app_permissions p on p.id=rp.permission_id where rp.role_id=r.id) as 功能模块权限,'active' as status from app_roles r"+(tests?"":" where not r.is_test")+" order by r.id");}
  if(module.equals("audit")){access.require("system","audit");var rows=db.queryForList("select id,id as 日志ID,module_id as 操作对象,operator as 操作人,result as status,action,record_id,role,request_no,created_at,before_data,after_data from app_audit_logs order by id desc limit 10000");for(var r:rows){r.put("before_data",audit.parse(r.get("before_data")));r.put("after_data",audit.parse(r.get("after_data")));}return rows;}
  if(module.equals("approvalTemplate")){return db.queryForList("select id,name,name as 配置编号,module_id as 可审批功能模块,nodes as 可审批节点,case when enabled then 'enabled' else 'disabled' end status,created_at from app_approval_templates order by created_at desc");}
  if(module.equals("expensePaymentRecord")){var args=new ArrayList<Object>();String scope=access.filter(args);var rows=db.queryForList("select r.id,r.record_no,r.title,r.status,r.payload::text as source_payload,r.created_at,r.updated_at,p.id as payment_id,p.reference as 银行流水号,p.amount as 本次付款金额,p.paid_at as 付款日期,v.reason as 冲销原因 from app_records r join app_finance_payments p on r.payload->>'paymentId'=p.id::text left join app_finance_reversals v on v.payment_id=p.id where r.module_id='expensePaymentRecord' and r.id in (select id from app_records where deleted_at is null"+scope+") order by r.updated_at desc",args.toArray());for(var row:rows){var payload=audit.read(row.get("source_payload").toString());for(var entry:payload.entrySet())if(!Set.of("id","record_no","title","status","created_at","updated_at","module_id").contains(entry.getKey()))row.put(entry.getKey(),entry.getValue());}return rows;}
  var args=new ArrayList<Object>();String where="deleted_at is null";if(module.equals("expenseMyApply")){args.add(access.user());where+=" and created_by=?";}where+=access.filter(args);
  if(module.equals("approval")||module.equals("expenseTodo")){args.add(access.user());var rows=db.queryForList("select t.id,r.module_id as 类型,r.record_no as 审批单,r.record_no as 待办单据,r.created_by as 申请人,r.project_name as \"申请人/项目\",r.title,r.status,r.id as record_id,r.module_id,i.current_step as 当前节点,t.status as task_status,r.payload::text as source_payload,t.created_at from app_approval_tasks t join app_approval_instances i on i.id=t.instance_id join app_records r on r.id=i.record_id where r.id in (select id from app_records where "+where+") and t.assignee=? and t.status='pending' and i.status='pending' and t.step=i.current_step and t.cycle=i.current_cycle order by t.created_at desc",args.toArray());return rows.stream().filter(r->access.allowed(r.get("module_id").toString(),"view")).toList();}
  var rows=db.queryForList("select id,module_id,record_no,record_no as \"单据编号/主题\",module_id as 单据类型,title,status,payload::text as source_payload,created_at,updated_at from app_records where "+where+" order by updated_at desc",args.toArray());for(var r:rows){var p=audit.read(r.get("source_payload").toString());r.put("金额/币种",Objects.toString(p.get("amount"),Objects.toString(p.get("金额"),""))+" "+Objects.toString(p.get("currency"),""));}return rows.stream().filter(r->access.allowed(r.get("module_id").toString(),"view")).toList();
 }
}
