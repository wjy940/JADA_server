package com.jada.severe.security;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.filter.OncePerRequestFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
@Component
@Order(Ordered.HIGHEST_PRECEDENCE+20)
public class IdentityFilter extends OncePerRequestFilter {
 private final JdbcTemplate db;private final ObjectMapper json;
 @Value("${app.security.allow-header-auth:true}") private boolean headers;
 @Value("${app.security.public-ledger-read:true}") private boolean publicRead;
 @Value("${app.include-test-data:true}") private boolean includeTests;
 public IdentityFilter(JdbcTemplate db,ObjectMapper json){this.db=db;this.json=json;}
 protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
  String path=req.getRequestURI();if(!path.startsWith("/api/")||req.getMethod().equals("OPTIONS")||path.equals("/api/auth/login")){chain.doFilter(req,res);return;}
  String authorization=req.getHeader("Authorization"),user=null;
  if(authorization!=null){if(!authorization.startsWith("Bearer ")){fail(res,401,"认证格式无效");return;}var users=db.queryForList("select s.user_id from app_auth_sessions s join app_users u on u.id=s.user_id where s.token_hash=? and s.revoked_at is null and s.expires_at>now() and u.enabled and (? or not u.is_test) and (u.locked_until is null or u.locked_until<=now())",String.class,AuthService.digest(authorization.substring(7)),includeTests);if(users.isEmpty()){fail(res,401,"登录已失效");return;}user=users.get(0);}
  else if(req.getHeader("X-User-Id")!=null){if(!headers){fail(res,403,"正式环境禁止模拟身份请求头");return;}user=req.getHeader("X-User-Id");if(!Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from app_users where id=? and enabled)",Boolean.class,user))){fail(res,403,"账号不存在或已禁用");return;}}
  if(user==null){boolean publicPath=path.equals("/api/modules")||path.matches("/api/modules/engineeringEquipment/(list|[0-9a-fA-F-]{36})");if(!(publicRead&&req.getMethod().equals("GET")&&publicPath)){fail(res,401,"请先登录");return;}}
  else req.setAttribute("authenticatedUser",user);
  chain.doFilter(req,res);
 }
 private void fail(HttpServletResponse response,int code,String message) throws IOException {response.setStatus(code);response.setContentType("application/json;charset=UTF-8");json.writeValue(response.getOutputStream(),Map.of("success",false,"message",message,"code",code==403?"FORBIDDEN":"UNAUTHORIZED"));}
}
