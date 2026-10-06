package com.jada.severe.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.postgresql.util.PGobject;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.Objects;

@Service
public class ModuleService {
    @org.springframework.beans.factory.annotation.Autowired private ProjectionService projections;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AccessService access; private final AuditService audit; private final BusinessService business; private final ApprovalService approvals;private final SchemaService schema;

    public ModuleService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, AccessService access, AuditService audit, BusinessService business, ApprovalService approvals, SchemaService schema) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper; this.access=access; this.audit=audit; this.business=business;this.approvals=approvals;this.schema=schema;
    }

    public List<Map<String, Object>> listModules() {
        List<Map<String, Object>> modules = jdbcTemplate.queryForList("""
            select module_id, group_key as module_group, module_name, route_path, page_title, page_subtitle,
                   icon, stats, tabs, process_steps, sort_order, enabled
            from app_modules
            where enabled = true
            order by sort_order, module_id
            """);

        Map<String, List<Map<String, Object>>> columns = groupByModule("""
            select module_id, column_key, column_label, data_type, sort_order
            from app_module_columns
            order by module_id, sort_order
            """);

        Map<String, List<Map<String, Object>>> fields = groupByModule("""
            select module_id, field_key, field_label, field_type, db_type, sample_value, options, required, sort_order
            from app_module_fields
            order by module_id, sort_order
            """);

        for (Map<String, Object> module : modules) {
            normalizeJsonColumns(module, "stats", "tabs", "process_steps");
            String moduleId = String.valueOf(module.get("module_id"));
            module.put("list_columns", columns.getOrDefault(moduleId, List.of()));
            module.put("form_schema", fields.getOrDefault(moduleId, List.of()));
            if(!access.allowed(moduleId,"view"))continue;
            if(moduleId.equals("iot")){module.put("data_source","external GPS API");module.put("stats",List.of());continue;}
            var countArgs=new ArrayList<Object>();countArgs.add(moduleId);String countScope=access.filter(countArgs);
            var counts=jdbcTemplate.queryForList("select status,count(*) as count from app_records where module_id=? and deleted_at is null"+countScope+" group by status",countArgs.toArray());
            module.put("record_counts",counts);module.put("stats",counts);
            module.put("workflow_states",jdbcTemplate.queryForList("select status,name from app_workflow_states order by status"));
            module.put("workflow_transitions",jdbcTemplate.queryForList("select from_state,action,to_state from app_workflow_transitions where module_id=? order by from_state,action",moduleId));
        }
        return modules.stream().filter(m -> access.allowed(m.get("module_id").toString(), "view")).toList();
    }

    public Map<String, Object> listRecords(String moduleId, int page, int pageSize, String keyword, String status) {
        requireModule(moduleId); access.require(moduleId,"view");
        if(projections.handles(moduleId))return projections.list(moduleId,page,pageSize,keyword,status);
        List<Map<String, Object>> columns = listColumns(moduleId);
        if(page<1||pageSize<1||pageSize>100||page>1000000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"分页参数超出范围");
        int offset = (page - 1) * pageSize;

        List<Object> values = new ArrayList<>();
        values.add(moduleId);
        StringBuilder where = new StringBuilder("module_id = ? and deleted_at is null" + access.filter(values));

        if (keyword != null && !keyword.isBlank()) {
            where.append(" and (record_no ilike ? or title ilike ? or payload::text ilike ?)");
            String like = "%" + keyword.trim() + "%";
            values.add(like);
            values.add(like);
            values.add(like);
        }

        if (status != null && !status.isBlank() && !Set.of("全部","全部状态").contains(status)) {
            where.append(" and status = ?");
            values.add(status);
        }

        Integer total = jdbcTemplate.queryForObject(
            "select count(*)::int from app_records where " + where,
            Integer.class,
            values.toArray()
        );

        values.add(pageSize);
        values.add(offset);
        List<Map<String, Object>> rows = jdbcTemplate.query(
            """
            select id, module_id, record_no, title, status, project_name, payload::text as payload,
                   created_at, updated_at
            from app_records
            where %s
            order by updated_at desc, created_at desc
            limit ? offset ?
            """.formatted(where),
            (rs, rowNum) -> toFrontendRecord(rs, columns),
            values.toArray()
        );

        return Map.of("list", rows, "total", total == null ? 0 : total);
    }

    public Map<String, Object> getRecord(String moduleId, UUID id) {
        requireModule(moduleId);access.require(moduleId,"view");
        if(projections.handles(moduleId))return projections.get(moduleId,id);
        access.record(moduleId,id);
        List<Map<String, Object>> columns = listColumns(moduleId);
        try {
            return jdbcTemplate.queryForObject(
                """
                select id, module_id, record_no, title, status, project_name, payload::text as payload,
                       created_at, updated_at
                from app_records
                where module_id = ? and id = ?
                """,
                (rs, rowNum) -> toFrontendRecord(rs, columns),
                moduleId,
                id
            );
        } catch (EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found");
        }
    }

    @Transactional
    public Map<String,Object> createRecord(String moduleId,Map<String,Object> body){
        requireModule(moduleId);projections.writable(moduleId);access.require(moduleId,"create");body=normalize(moduleId,body,true);
        if(body.containsKey("status")&&!"draft".equals(body.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"新记录必须为草稿");
        var payload=new LinkedHashMap<String,Object>(body);payload.put("status","draft");String recordNo=createRecordNo(moduleId);schema.number(moduleId,payload,recordNo);schema.validate(moduleId,payload,false);schema.references(moduleId,payload);boolean isTest=testRecord();
        if(Set.of("engineeringEquipment","vehicleEquipment").contains(moduleId)){
            String asset=stringValue(firstValue(payload,"资产编号","asset_no","assetNo"),null);
            if(asset!=null){jdbcTemplate.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))","asset:"+asset);if(Boolean.TRUE.equals(jdbcTemplate.queryForObject("select exists(select 1 from app_records where module_id=? and deleted_at is null and coalesce(payload->>'资产编号',payload->>'asset_no',payload->>'assetNo')=?)",Boolean.class,moduleId,asset)))throw new ResponseStatusException(HttpStatus.CONFLICT,"资产编号已存在");}
        }
        UUID id=jdbcTemplate.queryForObject("insert into app_records(module_id,record_no,title,status,project_name,payload,created_by,updated_by,is_test) values(?,?,?,'draft',?,?::jsonb,?,?,?) returning id",UUID.class,moduleId,recordNo,stringValue(firstValue(body,"title","name","资产编号"),moduleId+" record"),stringValue(firstValue(body,"projectName","project_name","项目","项目名称","所属项目"),null),audit.json(payload),access.user(),access.user(),isTest);
        access.record(moduleId,id);var record=getRecord(moduleId,id);audit.log(moduleId,id,"create",null,record,null,"draft");return Map.of("success",true,"message","created","record",record,"data",record);
    }
    @org.springframework.beans.factory.annotation.Autowired private jakarta.servlet.http.HttpServletRequest request;
    @org.springframework.beans.factory.annotation.Value("${app.include-test-data:true}") private boolean includeTests;
    private boolean testRecord(){boolean test="true".equalsIgnoreCase(request.getHeader("X-Test-Data"));if(test){if(!includeTests)access.deny();access.require("system","manage");}return test;}
    private Map<String,Object> lock(String moduleId,UUID id){access.record(moduleId,id);jdbcTemplate.queryForObject("select id from app_records where id=? for update",UUID.class,id);return getRecord(moduleId,id);}
    @Transactional
    public Map<String,Object> updateRecord(String moduleId,UUID id,Map<String,Object> body){
        projections.writable(moduleId);access.require(moduleId,"edit");body=normalize(moduleId,body,false);var before=lock(moduleId,id);String status=before.get("status").toString();
        if(Set.of("engineeringEquipment","vehicleEquipment").contains(moduleId)){
            var current=(Map<?,?>)before.get("payload");
            if(Boolean.TRUE.equals(jdbcTemplate.queryForObject("select exists(select 1 from app_equipment_reservations where equipment_id=?)",Boolean.class,id)))throw new ResponseStatusException(HttpStatus.CONFLICT,"设备正在业务占用中，请通过对应单据操作");
            for(String field:List.of("equipmentStatus","activeDocumentId"))if(body.containsKey(field)&&!Objects.equals(body.get(field),current.get(field)))throw new ResponseStatusException(HttpStatus.CONFLICT,"设备运行状态由业务联动维护");
        }
        schema.validate(moduleId,body,false);schema.references(moduleId,body);
        if(!Set.of("draft","rejected").contains(status))throw new ResponseStatusException(HttpStatus.CONFLICT,"仅草稿和驳回记录可编辑");
        if(body.containsKey("status")&&!status.equals(body.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"请使用流程接口变更状态");
        if(Set.of("engineeringEquipment","vehicleEquipment").contains(moduleId)){
            String asset=stringValue(firstValue(body,"资产编号","asset_no","assetNo"),null);
            if(asset!=null){jdbcTemplate.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))","asset:"+asset);if(Boolean.TRUE.equals(jdbcTemplate.queryForObject("select exists(select 1 from app_records where module_id=? and id<>? and deleted_at is null and coalesce(payload->>'资产编号',payload->>'asset_no',payload->>'assetNo')=?)",Boolean.class,moduleId,id,asset)))throw new ResponseStatusException(HttpStatus.CONFLICT,"资产编号已存在");}
        }
        jdbcTemplate.update("update app_records set title=coalesce(?,title),project_name=coalesce(?,project_name),payload=payload || ?::jsonb,updated_by=?,updated_at=now() where id=?",stringValue(body.get("title"),null),stringValue(firstValue(body,"projectName","project_name","项目","项目名称","所属项目"),null),audit.json(body),access.user(),id);
        access.record(moduleId,id);var after=getRecord(moduleId,id);audit.log(moduleId,id,"edit",before,after,status,status);return Map.of("success",true,"message","updated","record",after,"data",after);
    }
    @Transactional
    public void deleteRecord(String moduleId,UUID id){projections.writable(moduleId);access.require(moduleId,"delete");var before=lock(moduleId,id);if(Boolean.TRUE.equals(jdbcTemplate.queryForObject("select exists(select 1 from app_equipment_reservations where equipment_id=?)",Boolean.class,id)))throw new ResponseStatusException(HttpStatus.CONFLICT,"占用中的设备不能删除");if(!Set.of("draft","rejected","voided","archived").contains(before.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"流程中记录不能删除");jdbcTemplate.update("update app_records set deleted_at=now(),updated_at=now(),updated_by=? where id=?",access.user(),id);audit.log(moduleId,id,"delete",before,null,before.get("status").toString(),before.get("status").toString());}
    @Transactional
    public Map<String,Object> submitRecord(String moduleId,UUID id){return transition(moduleId,id,"submit",Map.of());}
    @Transactional
    public Map<String,Object> transition(String moduleId,UUID id,String action,Map<String,Object> body){
        projections.writable(moduleId);access.require(moduleId,action.equals("saveDraft")?"edit":action.equals("startReview")?"approve":action);
        access.record(moduleId,id);
        if(Set.of("approve","reject").contains(action)&&approvals.active(id)&&!approvals.finalizing())return approvals.decideRecord(id,action,body);
        if(Set.of("approve","reject").contains(action)&&!approvals.finalizing())throw new ResponseStatusException(HttpStatus.CONFLICT,"单据没有有效审批实例，请重新提交审批");
        if(action.equals("void"))approvals.cancelRecord(id);
        var before=lock(moduleId,id);String from=before.get("status").toString();
        var targets=jdbcTemplate.queryForList("select to_state from app_workflow_transitions where module_id=? and from_state=? and action=?",String.class,moduleId,from,action);
        if(targets.isEmpty())throw new ResponseStatusException(HttpStatus.CONFLICT,"非法状态流转: "+from+" -> "+action);
        String to=targets.get(0);
        var effective=new LinkedHashMap<String,Object>((Map<String,Object>)before.get("payload"));
        if(action.equals("submit")){schema.validate(moduleId,effective,true,id);schema.references(moduleId,effective);jdbcTemplate.update("update app_records set payload=?::jsonb where id=?",audit.json(effective),id);}
        if(action.equals("saveDraft")&&!body.isEmpty())updateRecord(moduleId,id,body);
        business.apply(moduleId,id,from,action,effective,body);
        jdbcTemplate.update("update app_records set status=?,payload=payload || jsonb_build_object('status',?::text),updated_by=?,updated_at=now() where id=?",to,to,access.user(),id);
        if(action.equals("submit"))approvals.open(moduleId,id,effective);
        if(Set.of("reject","void","archive").contains(action))jdbcTemplate.update("update app_todos set status='completed',completed_at=now() where record_id=? and status='pending'",id);
        // Return already authorized record even when completing the last personal todo removes visibility.
        var after=jdbcTemplate.queryForObject("select id,module_id,record_no,title,status,project_name,payload::text as payload,created_at,updated_at from app_records where id=?",(rs,n)->toFrontendRecord(rs,listColumns(moduleId)),id);
        audit.log(moduleId,id,action,before,after,from,to);return Map.of("success",true,"message",to,"record",after,"data",after);
    }
    private Map<String,Object> normalize(String module,Map<String,Object> body,boolean isNew){
        var out=new LinkedHashMap<String,Object>(body);
        if(body.get("payload") instanceof Map<?,?> nested)nested.forEach((k,v)->out.put(k.toString(),v));
        for(var f:jdbcTemplate.queryForList("select field_key,field_label from app_module_fields where module_id=?",module)){
            String key=f.get("field_key").toString(),label=f.get("field_label").toString();
            if(out.containsKey(key)&&!out.containsKey(label))out.put(label,out.get(key));
        }
        if(isNew&&!access.user().equals("anonymous")){var profile=jdbcTemplate.queryForMap("select project_name,warehouse_id,supplier_id,department_id from app_users where id=?",access.user());for(var pair:Map.of("projectName","project_name","warehouseId","warehouse_id","supplierId","supplier_id","departmentId","department_id").entrySet())if(!out.containsKey(pair.getKey())&&profile.get(pair.getValue())!=null)out.put(pair.getKey(),profile.get(pair.getValue()));}
        if(isNew&&Set.of("engineeringEquipment","vehicleEquipment").contains(module)&&!out.containsKey("equipmentStatus")){
            String usage=Objects.toString(firstValue(out,"在用/闲置","field_014"),"闲置");
            out.put("equipmentStatus",switch(usage){case "在用" -> "inUse";case "停用" -> "disabled";default -> "available";});
        }
        return out;
    }
    @Transactional
    public Map<String,Object> importRecords(String module,List<Map<String,Object>> records){
        requireModule(module);access.require(module,"import");if(records.isEmpty()||records.size()>500)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"导入需 1 至 500 条记录");
        var ids=new ArrayList<Object>();for(var row:records){var result=createRecord(module,row);ids.add(((Map<?,?>)result.get("record")).get("id"));}
        UUID batch=jdbcTemplate.queryForObject("insert into app_import_batches(module_id,created_by,record_count) values(?,?,?) returning id",UUID.class,module,access.user(),records.size());
        audit.log(module,null,"import",null,Map.of("batchId",batch,"recordIds",ids),null,null);return Map.of("batchId",batch,"recordIds",ids);
    }
    @Transactional
    public Map<String,Object> exportRecords(String module,String keyword,String status){access.require(module,"export");var page=listRecords(module,1,100,keyword,status);var result=new ArrayList<Object>();int total=((Number)page.get("total")).intValue();if(total>10000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"导出最多10000条，请缩小筛选范围");result.addAll((List<?>)page.get("list"));for(int n=2;(n-1)*100<total;n++)result.addAll((List<?>)listRecords(module,n,100,keyword,status).get("list"));audit.log(module,null,"export",null,Map.of("count",result.size()),null,null);return Map.of("records",result,"total",result.size());}
    public List<Map<String,Object>> attachments(String module,UUID id){access.require(module,"attachments");access.record(module,id);audit.log(module,id,"viewAttachments",null,null,null,null);return jdbcTemplate.queryForList("select id,name,storage_key,created_at from app_attachments where record_id=? and deleted_at is null",id);}
    @Transactional
    public Map<String,Object> addAttachment(String module,UUID id,Map<String,Object> body){access.require(module,"edit");access.record(module,id);if(firstValue(body,"name")==null||firstValue(body,"storageKey")==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"name/storageKey 必填");UUID aid=jdbcTemplate.queryForObject("insert into app_attachments(record_id,name,storage_key,created_by) values(?,?,?,?) returning id",UUID.class,id,body.get("name"),body.get("storageKey"),access.user());audit.log(module,id,"addAttachment",null,body,null,null);return Map.of("id",aid);}
    private void validateRequired(String m,Map<String,Object> payload){for(var f:jdbcTemplate.queryForList("select field_key,field_label from app_module_fields where module_id=? and required=true",m)){if(firstValue(payload,f.get("field_key").toString(),f.get("field_label").toString())==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"缺少必填字段: "+f.get("field_label"));}}

    private void requireModule(String moduleId) {
        if(moduleId.equals("iot"))throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"GPS 数据由外部 API 提供，本服务不提供定位数据接口");
        Boolean exists = jdbcTemplate.queryForObject(
            "select exists(select 1 from app_modules where module_id = ? and enabled = true)",
            Boolean.class,
            moduleId
        );
        if (!Boolean.TRUE.equals(exists)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Module not found: " + moduleId);
        }
    }

    private List<Map<String, Object>> listColumns(String moduleId) {
        return jdbcTemplate.queryForList(
            """
            select column_key, column_label, data_type, sort_order
            from app_module_columns
            where module_id = ?
            order by sort_order
            """,
            moduleId
        );
    }

    private Map<String, List<Map<String, Object>>> groupByModule(String sql) {
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList(sql)) {
            normalizeJsonColumns(row, "options");
            String moduleId = String.valueOf(row.get("module_id"));
            grouped.computeIfAbsent(moduleId, key -> new ArrayList<>()).add(row);
        }
        return grouped;
    }

    private Map<String, Object> toFrontendRecord(ResultSet rs, List<Map<String, Object>> columns) throws SQLException {
        Map<String, Object> payload = fromJson(rs.getString("payload"));
        List<Object> cells = new ArrayList<>();
        for (Map<String, Object> column : columns) {
            String label = String.valueOf(column.get("column_label"));
            String key = String.valueOf(column.get("column_key"));
            cells.add(Set.of("status","current_status").contains(key) || Set.of("状态","当前状态").contains(label) ? rs.getString("status") : payload.getOrDefault(label, payload.getOrDefault(key, "")));
        }

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", rs.getObject("id", UUID.class));
        record.put("recordNo", rs.getString("record_no"));
        record.put("title", rs.getString("title"));
        record.put("status", rs.getString("status"));
        record.put("payload", payload);
        record.put("cells", cells);
        record.put("createdAt", formatTime(rs.getObject("created_at", OffsetDateTime.class)));
        record.put("updatedAt", formatTime(rs.getObject("updated_at", OffsetDateTime.class)));
        return record;
    }

    private PGobject toJsonb(Map<String, Object> value) {
        try {
            PGobject object = new PGobject();
            object.setType("jsonb");
            object.setValue(objectMapper.writeValueAsString(value == null ? Map.of() : value));
            return object;
        } catch (SQLException | JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid JSON payload", ex);
        }
    }

    private Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (JsonProcessingException ex) {
            return new LinkedHashMap<>();
        }
    }

    private void normalizeJsonColumns(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            Object value = row.get(key);
            if (value == null) {
                continue;
            }
            row.put(key, parseJsonValue(String.valueOf(value)));
        }
    }

    private Object parseJsonValue(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException ex) {
            return json;
        }
    }

    private String createRecordNo(String moduleId) {
        String prefix = moduleId.replaceAll("[^a-zA-Z0-9]", "").toUpperCase(Locale.ROOT);
        if (prefix.length() > 8) {
            prefix = prefix.substring(0, 8);
        }
        return prefix + "-" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(OffsetDateTime.now()) + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
    }

    private Object firstValue(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String stringValue(Object value, String fallback) {
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback;
        }
        return String.valueOf(value);
    }

    private String formatTime(OffsetDateTime time) {
        return time == null ? null : time.toString();
    }
}
