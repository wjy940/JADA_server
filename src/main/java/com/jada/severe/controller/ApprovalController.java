package com.jada.severe.controller;
import com.jada.severe.service.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
@RequestMapping("/api/approval")
public class ApprovalController {
 private final ApprovalService approvals;private final ModuleService modules;
 public ApprovalController(ApprovalService approvals,ModuleService modules){this.approvals=approvals;this.modules=modules;}
 @GetMapping("/templates") public Object templates(){return Map.of("success",true,"data",approvals.templates());}
 @PostMapping("/templates") public Object create(@RequestBody Map<String,Object> body){return Map.of("success",true,"data",approvals.saveTemplate(null,body));}
 @PutMapping("/templates/{id}") public Object update(@PathVariable UUID id,@RequestBody Map<String,Object> body){return Map.of("success",true,"data",approvals.saveTemplate(id,body));}
 @GetMapping("/flow") public Object flow(@RequestParam String businessType,@RequestParam UUID businessId){return Map.of("success",true,"data",approvals.flow(businessType,businessId));}
 @PostMapping("/instances") public Object instance(@RequestBody Map<String,String> body){return modules.submitRecord(body.get("businessType"),UUID.fromString(body.get("businessId")));}
 @PostMapping("/tasks/{id}/{action:approve|reject|return|transfer|addSign}") public Object task(@PathVariable UUID id,@PathVariable String action,@RequestBody(required=false) Map<String,Object> body){return approvals.task(id,action,body==null?Map.of():body);}
}
