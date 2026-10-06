package com.jada.severe.service;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;
@Service
public class BusinessService {
 private final JdbcTemplate db;private final AccessService access;private final AuditService audit;private final DomainService domain;private final ExecutionService execution;
 public BusinessService(JdbcTemplate db,AccessService access,AuditService audit,DomainService domain,ExecutionService execution){this.db=db;this.access=access;this.audit=audit;this.domain=domain;this.execution=execution;}
 private String value(Map<String,Object> p,String... keys){for(String k:keys)if(p.get(k)!=null&&!p.get(k).toString().isBlank())return p.get(k).toString();return null;}
 private void conflict(String message){throw new ResponseStatusException(HttpStatus.CONFLICT,message);}
 public void apply(String m,UUID id,String from,String action,Map<String,Object> p,Map<String,Object> decision){
  domain.apply(m,id,action,p);
  if(Set.of("submit","approve").contains(action)&&Set.of("inbound","purchaseInbound","partsPurchase","maintenance","workorder").contains(m)){
   String sid=value(p,"supplierId"),name=value(p,"supplier_name","供应商","供应商名称");
   if(sid!=null||name!=null||Set.of("inbound","purchaseInbound","partsPurchase").contains(m)){
    if(sid!=null){if(!Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from app_records where id=? and module_id='supplier' and status='approved' and deleted_at is null)",Boolean.class,UUID.fromString(sid))))conflict("必须引用审批通过的合格供应商");}
    else{var ids=db.queryForList("select id from app_records where module_id='supplier' and status='approved' and deleted_at is null and (payload->>'供应商名称'=? or payload->>'supplier_name_2'=? or title=?)",UUID.class,name,name,name);if(ids.size()!=1)conflict("必须提供唯一的合格 supplierId 或供应商名称");p.put("supplierId",ids.get(0).toString());patch(m,id,Map.of("supplierId",ids.get(0).toString()),"supplierReference");}
   }
  }
  if(m.equals("supplier")&&action.equals("approve")){patch(m,id,Map.of("qualified",true),"supplierQualified");return;}
  if(action.equals("approve")&&Set.of("inbound","purchaseInbound").contains(m)){
   String part=value(p,"partNo","part_no","备件编号");
   if(part!=null){receiveStock(m,id,part,p);return;}
   String asset=value(p,"assetNo","asset_no","资产编号");if(asset==null)conflict("入库必须填写 assetNo 或资产编号");
   db.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))","asset:"+asset);
   String targetModule=Set.of("vehicle","车辆").contains(Objects.toString(p.get("assetType"),Objects.toString(p.get("设备类别"),"")))?"vehicleEquipment":"engineeringEquipment";
   var found=db.queryForList("select id from app_records where module_id=? and deleted_at is null and (payload->>'assetNo'=? or payload->>'资产编号'=? or payload->>'车辆编号'=?) for update",UUID.class,targetModule,asset,asset,asset);
   var data=new LinkedHashMap<String,Object>(p);data.put("assetNo",asset);data.put("equipmentStatus","available");data.put("在用/闲置","闲置");data.put("sourceRecordId",id.toString());data.put("status","draft");
   if(found.size()>1)conflict("资产编号对应多条台账，须先修复重复数据");
   data.put("资产编号",asset);if(targetModule.equals("vehicleEquipment"))data.put("车辆编号",asset);String model=value(p,"equipment_model","equipmentModel","设备型号","设备名称/型号");if(model!=null)data.put("设备型号",model);String maker=value(p,"manufacturer","生产厂家");if(maker!=null)data.put("生产厂家",maker);String location=value(p,"location","库位","现设备地点");if(location!=null)data.put("现设备地点",location);
   UUID equipment;if(found.isEmpty()){equipment=create(targetModule,asset,data); }else{equipment=found.get(0);if(Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from app_equipment_reservations where equipment_id=?)",Boolean.class,equipment)))conflict("入库设备已被业务占用");data.remove("status");patch(targetModule,equipment,data,"inbound");}
   link(id,equipment,"inbound");patch(m,id,Map.of("equipmentId",equipment.toString(),"inboundArchived",true),"inboundArchive");
  }
  if(m.equals("partsPurchase")&&action.equals("approve")){var data=new LinkedHashMap<String,Object>(p);data.put("sourceRecordId",id.toString());data.put("status","draft");UUID target=create("purchaseInbound","采购待入库",data);link(id,target,"purchase");}
  if(m.equals("warehouse")&&action.equals("approve")&&value(p,"partNo","part_no","备件编号")!=null){moveStock(id,p);return;}
  if(Set.of("maintenance","workorder","dispatch","vehicleDispatch","machineDispatch","rental","contract","returnPool","warehouse").contains(m)){
   String eid=value(p,"equipmentId","设备ID");
   boolean rentalContract=!m.equals("contract")||Set.of("设备租赁","rental").contains(Objects.toString(p.get("合同类型"),Objects.toString(p.get("contractType"),"设备租赁")));
   if(m.equals("contract")&&!rentalContract){if(action.equals("approve"))reserveSettlement(id,p);return;}if(eid==null&&((Set.of("maintenance","workorder").contains(m)&&action.equals("submit"))||action.equals("approve")))conflict("业务单据必须关联 equipmentId");
   if(action.equals("archive")&&Set.of("vehicleDispatch","machineDispatch").contains(m)&&!"completed".equals(p.get("executionStatus")))conflict("执行调度须先完成任务后归档");
   if(eid!=null){UUID equipment=UUID.fromString(eid);var rows=db.queryForList("select module_id,payload::text as payload from app_records where id=? and module_id in ('engineeringEquipment','vehicleEquipment') and deleted_at is null for update",equipment);if(rows.isEmpty())conflict("关联设备不存在");String em=rows.get(0).get("module_id").toString();
    Map<String,Object> update=new LinkedHashMap<>();
    if(Set.of("maintenance","workorder").contains(m)&&action.equals("submit")){update.put("equipmentStatus",m.equals("workorder")&&"operation".equals(value(p,"workType"))?"working":"maintenance");update.put("activeDocumentId",id.toString());}
    if(action.equals("approve")){
     if(Set.of("maintenance","workorder").contains(m)){String result=value(decision,"equipmentStatus");if(result!=null&&!Set.of("available","disabled").contains(result))conflict("维修结果状态只支持 available/disabled");update.put("equipmentStatus",result==null?"available":result);update.put("activeDocumentId","");}
     if(Set.of("rental","contract").contains(m)){if(value(p,"amount","金额","合同金额")==null||value(p,"startDate","开始日期")==null||value(p,"endDate","结束日期")==null||value(p,"lessee","承租方","合同对象")==null)conflict("合同必须包含金额、周期和承租方");String amount=value(p,"amount","金额","合同金额"),start=value(p,"startDate","开始日期"),end=value(p,"endDate","结束日期");
      try{if(new java.math.BigDecimal(amount).signum()<0||java.time.LocalDate.parse(end).isBefore(java.time.LocalDate.parse(start)))conflict("合同金额或周期无效");}catch(java.time.format.DateTimeParseException|NumberFormatException e){conflict("合同日期须为 YYYY-MM-DD，金额须为数字");}
      update.put("equipmentStatus","rented");update.put("activeDocumentId",id.toString());}
     if(m.equals("returnPool")){update.put("equipmentStatus","available");update.put("activeDocumentId","");}
     if(Set.of("vehicleDispatch","machineDispatch").contains(m)){execution.allocate(m,id,equipment,p);update.put("equipmentStatus","working");update.put("activeDocumentId",id.toString());}
     if(Set.of("dispatch","warehouse").contains(m)){String location=value(p,"location","地点","目标地点");String keeper=value(p,"custodian","保管单位","管理单位");if(location==null||keeper==null)conflict("调拨必须指定 location 和 custodian");update.put("location",location);update.put("现设备地点",location);update.put("custodian",keeper);update.put("管理单位",keeper);String wh=value(p,"warehouseId");if(wh!=null)update.put("warehouseId",wh);update.put("equipmentStatus","available");}
    }
    if(action.equals("archive")&&Set.of("rental","contract").contains(m)){if(action.equals("archive")&&!Boolean.TRUE.equals(decision.get("contractEnded")))conflict("合同归档释放设备需传 contractEnded=true");update.put("equipmentStatus","available");update.put("activeDocumentId","");}
    if(Set.of("reject","void").contains(action)&&Set.of("submitted","reviewing").contains(from)&&Set.of("maintenance","workorder").contains(m)){update.put("equipmentStatus","available");update.put("activeDocumentId","");}
    if(!update.isEmpty()){
     String active=db.queryForObject("select payload->>'activeDocumentId' from app_records where id=?",String.class,equipment);
     if(active!=null&&!active.isBlank()&&!active.equals(id.toString())&&!m.equals("returnPool"))conflict("设备已被另一业务单据占用");
     boolean acquire=(Set.of("maintenance","workorder").contains(m)&&action.equals("submit"))||(Set.of("rental","contract").contains(m)&&action.equals("approve"));
     boolean release=(Set.of("maintenance","workorder").contains(m)&&Set.of("approve","reject","void").contains(action))||(Set.of("rental","contract").contains(m)&&action.equals("archive"))||(m.equals("returnPool")&&action.equals("approve"));
     if(acquire){String condition=db.queryForObject("select coalesce(payload->>'equipmentStatus',case when payload->>'businessStatus'='出租中' then 'rented' when payload->>'在用/闲置'='停用' then 'disabled' when payload->>'在用/闲置'='在用' then 'inUse' else 'available' end) from app_records where id=?",String.class,equipment);if(!condition.equals("available")&&!condition.equals("inUse")&&Set.of("maintenance","workorder").contains(m))conflict("设备当前状态不能进入维修/作业");if(Set.of("rental","contract").contains(m)&&!condition.equals("available"))conflict("只有可用设备可以出租");if(Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from app_equipment_reservations where equipment_id=?)",Boolean.class,equipment)))conflict("设备已有有效占用");db.update("insert into app_equipment_reservations(equipment_id,source_id,previous_payload) select id,?,payload from app_records where id=?",id,equipment);}
     if(release){UUID source=m.equals("returnPool")?UUID.fromString(required(p,"contractId")):id;var reservations=db.queryForList("select previous_payload::text as payload from app_equipment_reservations where equipment_id=? and source_id=?",equipment,source);if(reservations.isEmpty())conflict("设备占用与当前单据不匹配");
      if(Set.of("reject","void","archive").contains(action)||m.equals("returnPool")){var original=audit.read(reservations.get(0).get("payload").toString());update.put("equipmentStatus",original.getOrDefault("equipmentStatus","available"));}
      db.update("delete from app_equipment_reservations where equipment_id=? and source_id=?",equipment,source);
     }
     patch(em,equipment,update,"equipmentState");link(id,equipment,"equipment");
    }
   }
  }
  if(action.equals("approve")&&Set.of("partsPurchase","contract","rental","maintenance","workorder","expenseReimburse","outsourcePayment").contains(m))reserveSettlement(id,p);
 }
 private String required(Map<String,Object> p,String key){String value=value(p,key);if(value==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,key+" 必填");return value;}
 private void receiveStock(String module,UUID source,String part,Map<String,Object> p){
  String warehouse=value(p,"warehouseId","库位"),quantity=value(p,"quantity","数量","入库数量","申请数量");if(warehouse==null||quantity==null)conflict("备件入库必须提供 warehouseId 和 quantity");
  java.math.BigDecimal qty;try{qty=new java.math.BigDecimal(quantity);}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"数量必须为数字");}if(qty.signum()<=0)conflict("入库数量必须大于0");
  db.update("insert into app_stock_movements(source_id,part_no,warehouse_id,quantity) values(?,?,?,?)",source,part,warehouse,qty);
  db.update("insert into app_stock_items(part_no,warehouse_id,quantity) values(?,?,?) on conflict(part_no,warehouse_id) do update set quantity=app_stock_items.quantity+excluded.quantity,updated_at=now()",part,warehouse,qty);
  syncStock(source,part,warehouse);patch(module,source,Map.of("inboundArchived",true),"stockReceive");
 }
 private void moveStock(UUID source,Map<String,Object> p){
  String action=required(p,"stockAction"),part=value(p,"partNo","part_no","备件编号"),warehouse=required(p,"warehouseId");
  if(action.equals("inbound")){receiveStock("warehouse",source,part,p);return;}
  if(!Set.of("outbound","transfer").contains(action))conflict("stockAction 必须为 inbound/outbound/transfer");
  java.math.BigDecimal quantity;try{quantity=new java.math.BigDecimal(required(p,"quantity"));}catch(NumberFormatException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"数量必须为数字");}
  if(quantity.signum()<=0)conflict("数量必须大于0");String target=action.equals("transfer")?required(p,"targetWarehouseId"):null;if(warehouse.equals(target))conflict("调出仓和调入仓不能相同");
  if(db.update("update app_stock_items set quantity=quantity-?,updated_at=now() where part_no=? and warehouse_id=? and quantity>=?",quantity,part,warehouse,quantity)==0)conflict("库存不足，出库或调拨已回滚");
  if(target!=null)db.update("insert into app_stock_items(part_no,warehouse_id,quantity) values(?,?,?) on conflict(part_no,warehouse_id) do update set quantity=app_stock_items.quantity+excluded.quantity,updated_at=now()",part,target,quantity);
  db.update("insert into app_stock_movements(source_id,part_no,warehouse_id,quantity,action,target_warehouse_id) values(?,?,?,?,?,?)",source,part,warehouse,quantity,action,target);
  syncStock(source,part,warehouse);if(target!=null)syncStock(source,part,target);patch("warehouse",source,Map.of("stockCompleted",true),"stock"+action);
 }
 private void syncStock(UUID source,String part,String warehouse){
  db.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))","stock:"+part+":"+warehouse);
  var quantity=db.queryForObject("select quantity from app_stock_items where part_no=? and warehouse_id=?",java.math.BigDecimal.class,part,warehouse);
  var data=new LinkedHashMap<String,Object>();data.put("sourceRecordId",source.toString());data.put("partNo",part);data.put("part_no",part);data.put("备件编号",part);data.put("warehouseId",warehouse);data.put("库位",warehouse);data.put("quantity",quantity);data.put("库存",quantity);data.put("库存数量",quantity);data.put("可用库存",quantity);
  for(var c:db.queryForList("select column_key,column_label from app_module_columns where module_id='parts'")){String label=c.get("column_label").toString();if((label.contains("库存")||label.equals("数量"))&&!label.contains("安全")&&!label.contains("最低")&&!label.contains("预警")){data.put(label,quantity);data.put(c.get("column_key").toString(),quantity);}}
  var ids=db.queryForList("select id from app_records where module_id='parts' and deleted_at is null and payload->>'partNo'=? and payload->>'warehouseId'=? for update",UUID.class,part,warehouse);
  UUID record;if(ids.isEmpty())record=create("parts",part,data);else{record=ids.get(0);patch("parts",record,data,"stockBalance");}link(source,record,"stock");
 }
 public void reserveSettlement(UUID source,Map<String,Object> p){
  java.math.BigDecimal amount=null;String raw=value(p,"amount","金额","合同金额","申请金额","本次付款金额","报销金额","折算本币合计","原币合计");
  if(raw!=null){try{amount=new java.math.BigDecimal(raw).setScale(2,java.math.RoundingMode.UNNECESSARY);}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"金额无效");}if(amount.signum()<0)conflict("金额不能为负数");}
  else if(value(p,"申请数量")!=null&&value(p,"预计单价")!=null)amount=new java.math.BigDecimal(value(p,"申请数量")).multiply(new java.math.BigDecimal(value(p,"预计单价"))).setScale(2,java.math.RoundingMode.HALF_UP);
  String currency=Objects.toString(value(p,"currency","币种"),"AED");if(!Set.of("AED","SAR","USD","CNY").contains(currency))conflict("币种无效");
  db.update("insert into app_finance_settlements(source_id,payload,amount,currency) values(?,?::jsonb,?,?) on conflict(source_id) do nothing",source,audit.json(p),amount,currency);
 }

 private UUID create(String m,String title,Map<String,Object> p){UUID id=UUID.randomUUID();boolean test=false;if(p.get("sourceRecordId")!=null)test=Boolean.TRUE.equals(db.queryForObject("select is_test from app_records where id=?",Boolean.class,UUID.fromString(p.get("sourceRecordId").toString())));db.update("insert into app_records(id,module_id,record_no,title,status,project_name,payload,created_by,updated_by,is_test) values(?,?,?,?,'draft',?,?::jsonb,?,?,?)",id,m,m+"-"+UUID.randomUUID(),title,value(p,"projectName","项目名称"),audit.json(p),access.user(),access.user(),test);audit.log(m,id,"linkCreate",null,p,null,"draft");return id;}
 private void link(UUID source,UUID target,String kind){db.update("insert into app_business_links values(?,?,?) on conflict do nothing",source,target,kind);}
 private void patch(String m,UUID id,Map<String,Object> p,String action){
  p=new LinkedHashMap<>(p);
  if(m.equals("engineeringEquipment")&&p.containsKey("equipmentStatus")){
   String state=p.get("equipmentStatus").toString();String usage=Set.of("inUse","rented","working").contains(state)?"在用":Set.of("disabled","maintenance").contains(state)?"停用":"闲置";p.put("在用/闲置",usage);if(state.equals("maintenance"))p.put("完好状态","需定期保养");
  }
  var before=db.queryForMap("select status,payload::text as payload from app_records where id=?",id);db.update("update app_records set payload=payload || ?::jsonb,updated_at=now(),updated_by=? where id=?",audit.json(p),access.user(),id);var after=db.queryForMap("select status,payload::text as payload from app_records where id=?",id);audit.log(m,id,action,before,after,before.get("status").toString(),after.get("status").toString());}
}
