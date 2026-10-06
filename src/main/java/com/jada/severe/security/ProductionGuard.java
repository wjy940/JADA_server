package com.jada.severe.security;
import org.springframework.stereotype.Component;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
@Component
public class ProductionGuard implements ApplicationRunner {
 private final JdbcTemplate db;private final AuthService auth;private final Environment environment;
 public ProductionGuard(JdbcTemplate db,AuthService auth,Environment environment){this.db=db;this.auth=auth;this.environment=environment;}
 public void run(ApplicationArguments arguments){
  String bootstrap=System.getenv("APP_BOOTSTRAP_ADMIN_PASSWORD");if(bootstrap!=null&&!bootstrap.isBlank())db.update("update app_users set password_hash=?,password_changed_at=now() where id='admin' and password_hash is null",auth.encode(bootstrap));
  if(Arrays.asList(environment.getActiveProfiles()).contains("prod")){
   for(String key:List.of("DB_URL","DB_USERNAME","DB_PASSWORD","ATTACHMENT_DIR"))if(System.getenv(key)==null||System.getenv(key).isBlank())throw new IllegalStateException("Production requires "+key);
   if("jd_dev_password".equals(environment.getProperty("spring.datasource.password")))throw new IllegalStateException("Development database password is forbidden in production");
   if(environment.getProperty("app.include-test-data",Boolean.class,true))throw new IllegalStateException("Production must exclude QA data");
   if(environment.getProperty("app.security.allow-header-auth",Boolean.class,true)||environment.getProperty("app.security.public-ledger-read",Boolean.class,true))throw new IllegalStateException("Production must disable simulated identity and public business data");
   if(!Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from app_users u join app_user_roles r on r.user_id=u.id where r.role_id='superAdmin' and u.enabled and u.password_hash is not null)",Boolean.class)))throw new IllegalStateException("Provision an administrator password before production startup");
   for(String hash:db.queryForList("select u.password_hash from app_users u join app_user_roles r on r.user_id=u.id where r.role_id='superAdmin' and u.enabled and u.password_hash is not null",String.class))if(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().matches("QA-Dev-Only-2026!",hash))throw new IllegalStateException("Rotate the QA administrator password before production");
  }
 }
}
