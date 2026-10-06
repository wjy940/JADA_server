package com.jada.severe.controller;
import com.jada.severe.service.ExecutionService;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
public class ExecutionController {
 private final ExecutionService execution;public ExecutionController(ExecutionService execution){this.execution=execution;}
 @PostMapping("/api/modules/{moduleId}/{id}/complete")public Object complete(@PathVariable String moduleId,@PathVariable UUID id,@RequestBody Map<String,Object> body){return Map.of("success",true,"data",execution.complete(moduleId,id,body));}
}
