package com.jada.severe.service;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;
import java.math.BigDecimal;
import java.time.*;
@Service
public class SchemaService {
 private final JdbcTemplate db;private final AuditService audit;private final AccessService access;
 public SchemaService(JdbcTemplate db,AuditService audit,AccessService access){this.db=db;this.audit=audit;this.access=access;}
 public void number(String module,Map<String,Object> payload,String number){for(var field:db.queryForList("select field_key,field_label,field_type from app_module_fields where module_id=?",module)){String label=field.get("field_label").toString();if((field.get("field_type").equals("autonumber")||label.matches(".*(单号|申请号|工单号|派车单号|派工单号|合同编号|计划号|标准编号|配置编号|车辆编号|资产编号)$"))&&blank(payload.get(label))&&blank(payload.get(field.get("field_key").toString()))){payload.put(label,number);payload.put(field.get("field_key").toString(),number);}}}
 private boolean blank(Object value){return value==null||value.toString().isBlank()||(value instanceof List<?> l&&l.isEmpty());}
 public void validate(String module,Map<String,Object> payload,boolean complete){validate(module,payload,complete,null);}
 public void validate(String module,Map<String,Object> payload,boolean complete,UUID record){
  for(var field:db.queryForList("select * from app_module_fields where module_id=? order by sort_order",module)){
   String label=field.get("field_label").toString(),key=field.get("field_key").toString(),type=field.get("field_type").toString();Object value=payload.containsKey(label)?payload.get(label):payload.get(key);
   if(blank(value)){if(complete&&Boolean.TRUE.equals(field.get("required")))bad("缺少必填字段: "+label);continue;}
   String text=value.toString();if(text.length()>32767)bad("字段过长: "+label);
   try{switch(type){case "number" -> {if(new BigDecimal(text).signum()<0)bad("数值不能为负: "+label);}case "date" -> LocalDate.parse(text);case "datetime-local" -> {try{LocalDateTime.parse(text);}catch(Exception ignored){OffsetDateTime.parse(text);}}case "file" -> {if(complete){List<?> files=value instanceof List<?> values?values:List.of(value);for(Object item:files){String raw=item instanceof Map<?,?> map?Objects.toString(map.get("id"),""):item.toString();UUID id=UUID.fromString(raw);if(record==null||!Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from app_attachments where id=? and record_id=? and deleted_at is null and sha256 is not null)",Boolean.class,id,record)))bad("请上传该单据的真实附件: "+label);}}}case "email" -> {if(!text.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))bad("邮箱格式无效: "+label);}case "url" -> {var uri=java.net.URI.create(text);if(!Set.of("http","https").contains(uri.getScheme())||uri.getHost()==null)bad("网址格式无效: "+label);}case "select" -> {var options=audit.parse(field.get("options"));if(options instanceof List<?> list&&!list.isEmpty()&&list.stream().noneMatch(o->text.equals(o.toString())||(o instanceof Map<?,?> option&&text.equals(Objects.toString(option.get("value"))))))bad("选项无效: "+label);}default -> {}}}catch(ResponseStatusException e){throw e;}catch(Exception e){bad("字段格式无效: "+label);}
  }
  for(var pair:List.of(List.of("开始日期","结束日期"),List.of("租赁开始日期","租赁结束日期"),List.of("费用开始日期","费用结束日期"),List.of("服务开始日期","服务结束日期"))){if(!blank(payload.get(pair.get(0)))&&!blank(payload.get(pair.get(1))))try{if(LocalDate.parse(payload.get(pair.get(1)).toString()).isBefore(LocalDate.parse(payload.get(pair.get(0)).toString())))bad("结束日期不能早于开始日期");}catch(java.time.format.DateTimeParseException e){bad("日期格式无效");}}
 }
 public void references(String module,Map<String,Object> payload){
  if(Set.of("engineeringEquipment","vehicleEquipment").contains(module))return;
  if(!payload.containsKey("equipmentId"))for(String key:List.of("设备","资产编号","关联资产","车头/车辆","工程设备","租赁资产","退租资产","检查对象")){Object value=payload.get(key);if(blank(value))continue;UUID id=findAsset(value.toString());if(id!=null){payload.put("equipmentId",id.toString());break;}}
  if(payload.get("equipmentId")!=null){UUID id;try{id=UUID.fromString(payload.get("equipmentId").toString());}catch(Exception e){bad("equipmentId 不是有效 UUID");return;}var targets=db.queryForList("select module_id from app_records where id=? and deleted_at is null",String.class,id);if(targets.isEmpty())bad("关联设备不存在");String target=targets.get(0);if(!Set.of("engineeringEquipment","vehicleEquipment").contains(target))bad("关联记录不是设备台账");access.record(target,id);}
 }
 private UUID findAsset(String value){var args=new ArrayList<Object>();args.add(value);args.add(value);args.add(value);args.add(value);args.add(value);String scope=access.filter(args);var ids=db.queryForList("select id from app_records where module_id in ('engineeringEquipment','vehicleEquipment') and deleted_at is null and (id::text=? or record_no=? or payload->>'资产编号'=? or payload->>'车辆编号'=? or title=?)"+scope,UUID.class,args.toArray());if(ids.size()>1)bad("关联资产不唯一，请使用 equipmentId");return ids.isEmpty()?null:ids.get(0);}
 private void bad(String message){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
}
