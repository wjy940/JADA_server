package com.jada.severe.controller;
import com.jada.severe.service.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
@RequestMapping("/api")
public class AdminController {
 private final AccessService access;private final AdminService admin;private final AuditService audit;private final com.jada.severe.security.AuthService auth;
 public AdminController(AccessService access,AdminService admin,AuditService audit,com.jada.severe.security.AuthService auth){this.access=access;this.admin=admin;this.audit=audit;this.auth=auth;}
 private Map<String,Object> ok(Object data){return Map.of("success",true,"data",data,"message","");}
 @PostMapping("/auth/login") public Object login(@RequestBody Map<String,Object> body){return ok(auth.login(body));}
 @PostMapping("/auth/logout") public Object logout(){auth.logout();return ok(Map.of());}
 @PostMapping("/users/{id}/password") public Object password(@PathVariable String id,@RequestBody Map<String,String> body){auth.password(id,body.get("currentPassword"),body.get("password"));return ok(Map.of());}
 @GetMapping("/auth/me") public Object me(){return ok(access.me());}
 @GetMapping("/auth/permissions") public Object permissions(){return ok(access.permissions());}
 @GetMapping("/{type:users|roles}") public Object list(@PathVariable String type){return ok(admin.list(type));}
 @GetMapping("/{type:users|roles}/{id}") public Object get(@PathVariable String type,@PathVariable String id){return ok(admin.get(type,id));}
 @PostMapping("/{type:users|roles}") public Object create(@PathVariable String type,@RequestBody Map<String,Object> body){return ok(admin.save(type,Objects.toString(body.get("id"),null),body,true));}
 @PutMapping("/{type:users|roles}/{id}") public Object update(@PathVariable String type,@PathVariable String id,@RequestBody Map<String,Object> body){return ok(admin.save(type,id,body,false));}
 @GetMapping("/roles/{id}/permissions") public Object rolePermissions(@PathVariable String id){return ok(admin.rolePermissions(id));}
 @PostMapping("/roles/{id}/permissions") public Object assign(@PathVariable String id,@RequestBody Map<String,List<String>> body){admin.setPermissions(id,body.getOrDefault("permissions",List.of()));return ok(Map.of());}
 @GetMapping("/audit/logs") public Object logs(@RequestParam(required=false) String moduleId,@RequestParam(required=false) String recordId,@RequestParam(required=false) String operator,@RequestParam(required=false) String action){return ok(admin.logs(moduleId,recordId,operator,action));}
 @GetMapping("/todos") public Object todos(){return ok(admin.todos(false));}
 @GetMapping("/todos/my") public Object my(){return ok(admin.todos(true));}
 @PostMapping("/todos/{id}/complete") public Object complete(@PathVariable UUID id){admin.complete(id);return ok(Map.of());}
 @GetMapping("/finance/settlements") public Object settlements(){return ok(admin.settlements());}
}
