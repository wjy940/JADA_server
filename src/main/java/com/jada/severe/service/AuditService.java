package com.jada.severe.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
@Service
public class AuditService {
 private final JdbcTemplate db;private final AccessService access;private final ObjectMapper json;
 public AuditService(JdbcTemplate db,AccessService access,ObjectMapper json){this.db=db;this.access=access;this.json=json;}
 public String json(Object o){try{return json.writeValueAsString(o);}catch(Exception e){throw new IllegalArgumentException("Invalid JSON",e);}}
 public Object parse(Object value){if(value==null)return null;try{return json.readValue(value.toString(),Object.class);}catch(Exception e){throw new IllegalArgumentException("Invalid stored JSON",e);}}
 public Map<String,Object> read(String text){try{return json.readValue(text,new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});}catch(Exception e){throw new IllegalArgumentException("Invalid stored JSON",e);}}
 public void log(String m,UUID id,String action,Object before,Object after,String bs,String as){db.update("insert into app_audit_logs(operator,role,module_id,record_id,action,before_status,after_status,before_data,after_data,request_no,result) values(?,?,?,?,?,?,?,?::jsonb,?::jsonb,?,'success')",access.user(),json(access.roles()),m,id,action,bs,as,json(before),json(after),access.requestNo());}
}
