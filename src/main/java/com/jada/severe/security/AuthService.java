package com.jada.severe.security;

import com.jada.severe.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class AuthService {
 private final JdbcTemplate db; private final TransactionTemplate tx;private final HttpServletRequest request;private final AccessService access;private final AuditService audit;
 private final BCryptPasswordEncoder encoder=new BCryptPasswordEncoder(12);
 @Value("${app.include-test-data:true}") private boolean includeTests;
 @Value("${app.security.session-hours:12}") private int hours;
 public AuthService(JdbcTemplate db,PlatformTransactionManager manager,HttpServletRequest request,AccessService access,AuditService audit){this.db=db;tx=new TransactionTemplate(manager);this.request=request;this.access=access;this.audit=audit;}
 public static String digest(String token){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 public static String randomToken(){byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
 public String encode(String password){if(password==null||password.length()<12||password.getBytes(StandardCharsets.UTF_8).length>72)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"密码必须至少12位且不超过72字节");return encoder.encode(password);}
 public Map<String,Object> login(Map<String,Object> body){
  String account=Objects.toString(body.getOrDefault("account",body.get("username")),""),password=Objects.toString(body.get("password"),"");
  String ip=request.getRemoteAddr();String token=tx.execute(status->{
   int attempts=db.queryForObject("select count(*) from app_login_attempts where ip_address=? and not success and created_at>now()-interval '15 minutes'",Integer.class,ip);
   if(attempts>=20)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"登录尝试过多，请15分钟后重试");
   var users=db.queryForList("select *,coalesce(locked_until>now(),false) as locked from app_users where id=? for update",account);
   boolean valid=false;if(!users.isEmpty()){var user=users.get(0);String hash=Objects.toString(user.get("password_hash"),"");valid=Boolean.TRUE.equals(user.get("enabled"))&&(includeTests||!Boolean.TRUE.equals(user.get("is_test")))&&!Boolean.TRUE.equals(user.get("locked"))&&!hash.isEmpty()&&encoder.matches(password,hash);}
   db.update("insert into app_login_attempts(account,ip_address,success) values(?,?,?)",account,ip,valid);
   if(!valid){if(!users.isEmpty())db.update("update app_users set failed_logins=failed_logins+1,locked_until=case when failed_logins+1>=5 then now()+interval '15 minutes' else locked_until end where id=?",account);return null;}
   db.update("update app_users set failed_logins=0,locked_until=null where id=?",account);
   String value=randomToken();db.update("insert into app_auth_sessions(token_hash,user_id,expires_at,ip_address) values(?,?,now()+(?*interval '1 hour'),?)",digest(value),account,hours,ip);return value;
  });
  if(token==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"账号、密码错误或账号已锁定");
  request.setAttribute("authenticatedUser",account);audit.log("auth",null,"login",null,Map.of("account",account),null,null);
  var result=new LinkedHashMap<String,Object>();result.put("token",token);result.put("tokenType","Bearer");result.put("expiresIn",hours*3600);result.put("user",access.me());return result;
 }
 @org.springframework.transaction.annotation.Transactional
 public void logout(){String token=request.getHeader("Authorization");if(token!=null&&token.startsWith("Bearer "))db.update("update app_auth_sessions set revoked_at=now() where token_hash=?",digest(token.substring(7)));audit.log("auth",null,"logout",null,null,null,null);}
 public void password(String id,String current,String password){if(!access.user().equals(id))access.require("system","manage");String hash=encode(password);tx.executeWithoutResult(status->{var user=db.queryForMap("select password_hash from app_users where id=? for update",id);if(access.user().equals(id)&&!access.allowed("system","manage")&&!encoder.matches(current==null?"":current,Objects.toString(user.get("password_hash"),"")))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"原密码错误");db.update("update app_users set password_hash=?,password_changed_at=now(),failed_logins=0,locked_until=null where id=?",hash,id);db.update("update app_auth_sessions set revoked_at=now() where user_id=?",id);audit.log("users",null,"passwordChange",null,Map.of("id",id),null,null);});}
}
