package com.jada.severe.config;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.jdbc.core.JdbcTemplate;
import java.io.IOException;
import java.util.UUID;
@Component
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE+10)
public class RequestAuditFilter extends OncePerRequestFilter {
 private final JdbcTemplate db;public RequestAuditFilter(JdbcTemplate db){this.db=db;}
 protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
  String number=UUID.randomUUID().toString();request.setAttribute("requestNo",number);response.setHeader("X-Request-Id",number);response.setHeader("X-Content-Type-Options","nosniff");response.setHeader("X-Frame-Options","DENY");if(request.getRequestURI().startsWith("/api/"))response.setHeader("Cache-Control","no-store");
  try{chain.doFilter(request,response);}finally{
   if(request.getRequestURI().startsWith("/api/")&&!request.getMethod().equals("OPTIONS")){
    String user=java.util.Objects.toString(request.getAttribute("authenticatedUser"),request.getHeader("X-User-Id"));String result=response.getStatus()<400?"success":"failure";String error=response.getStatus()<400?null:"HTTP "+response.getStatus();
    try{db.update("insert into app_operation_logs(operator,action,request_no,result,error_message) values(?,?,?,?,?)",user==null?"anonymous":user,request.getMethod()+" "+request.getRequestURI(),number,result,error);
     if(response.getStatus()>=400||request.getMethod().equals("GET")){
      String[] parts=request.getRequestURI().split("/");String module=parts.length>3&&parts[2].equals("modules")?parts[3]:parts.length>2?parts[2]:null;UUID record=null;
      if(parts.length>4&&parts[2].equals("modules"))try{record=UUID.fromString(parts[4]);}catch(IllegalArgumentException ignored){}
      String roles=db.queryForObject("select string_agg(role_id,',') from app_user_roles where user_id=?",String.class,user);
      db.update("insert into app_audit_logs(operator,role,module_id,record_id,action,request_no,result,error_message) values(?,?,?,?,?,?,?,?)",user==null?"anonymous":user,roles,module,record,request.getMethod()+" "+request.getRequestURI(),number,result,error);
     }
    }catch(Exception e){org.slf4j.LoggerFactory.getLogger(getClass()).error("Request audit persistence failed: {}",number,e);}
   }
  }
 }
}
