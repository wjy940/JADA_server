package com.jada.severe.controller;

import com.jada.severe.service.ModuleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/modules")
public class ModuleController {
    private final ModuleService moduleService;
    private final com.jada.severe.service.ExcelService excel;

    public ModuleController(ModuleService moduleService,com.jada.severe.service.ExcelService excel) {
        this.moduleService = moduleService;this.excel=excel;
    }

    @GetMapping
    public Map<String, Object> modules() {
        return Map.of("success", true, "data", moduleService.listModules());
    }

    @GetMapping("/{moduleId}/list")
    public Map<String, Object> listRecords(
        @PathVariable String moduleId,
        @RequestParam(defaultValue = "1") @Min(1) int page,
        @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String status
    ) {
        return moduleService.listRecords(moduleId, page, pageSize, keyword, status);
    }

    @GetMapping("/{moduleId}/{id}")
    public Map<String, Object> getRecord(@PathVariable String moduleId, @PathVariable UUID id) {
        return moduleService.getRecord(moduleId, id);
    }

    @PostMapping("/{moduleId}")
    public Map<String, Object> createRecord(@PathVariable String moduleId, @Valid @RequestBody Map<String, Object> body) {
        return moduleService.createRecord(moduleId, body);
    }

    @PutMapping("/{moduleId}/{id}")
    public Map<String, Object> updateRecord(
        @PathVariable String moduleId,
        @PathVariable UUID id,
        @Valid @RequestBody Map<String, Object> body
    ) {
        return moduleService.updateRecord(moduleId, id, body);
    }

    @PatchMapping("/{moduleId}/{id}")
    public Map<String, Object> patchRecord(
        @PathVariable String moduleId,
        @PathVariable UUID id,
        @RequestBody Map<String, Object> body
    ) {
        return moduleService.updateRecord(moduleId, id, body);
    }

    @DeleteMapping("/{moduleId}/{id}")
    public Map<String, Object> deleteRecord(@PathVariable String moduleId, @PathVariable UUID id) {
        moduleService.deleteRecord(moduleId, id);
        return Map.of("success", true, "message", "deleted");
    }

    @PostMapping("/{moduleId}/{id}/submit")
    public Map<String, Object> submitRecord(@PathVariable String moduleId, @PathVariable UUID id) {
        return moduleService.submitRecord(moduleId, id);
    }
    @PostMapping("/{moduleId}/{id}/{action:approve|reject|void|archive|saveDraft|startReview}")
    public Map<String,Object> action(@PathVariable String moduleId,@PathVariable UUID id,@PathVariable String action,@RequestBody(required=false) Map<String,Object> body){return moduleService.transition(moduleId,id,action,body==null?Map.of():body);}
    @PostMapping(value="/{moduleId}/import",consumes="application/json")
    public Object importJson(@PathVariable String moduleId,@RequestBody java.util.List<Map<String,Object>> records){return Map.of("success",true,"data",moduleService.importRecords(moduleId,records));}
    @PostMapping(value="/{moduleId}/import",consumes="multipart/form-data")
    public Object importFile(@PathVariable String moduleId,@RequestParam("file") org.springframework.web.multipart.MultipartFile file) throws java.io.IOException {
        return Map.of("success",true,"data",excel.importFile(moduleId,file));
    }
    @PostMapping("/{moduleId}/export")
    public Object export(@PathVariable String moduleId,@RequestBody(required=false) Map<String,Object> params){return Map.of("success",true,"data",moduleService.exportRecords(moduleId,params==null?null:(String)params.get("keyword"),params==null?null:(String)params.get("status")));}
    @GetMapping("/{moduleId}/{id}/attachments")
    public Object attachments(@PathVariable String moduleId,@PathVariable UUID id){return Map.of("success",true,"data",moduleService.attachments(moduleId,id));}
    @PostMapping("/{moduleId}/{id}/attachments")
    public Object attach(@PathVariable String moduleId,@PathVariable UUID id,@RequestBody Map<String,Object> body){return Map.of("success",true,"data",moduleService.addAttachment(moduleId,id,body));}
    @PostMapping("/{moduleId}/export/excel")
    public org.springframework.http.ResponseEntity<byte[]> exportExcel(@PathVariable String moduleId,@RequestBody(required=false) Map<String,String> body){return org.springframework.http.ResponseEntity.ok().header("Content-Disposition","attachment; filename=records.xlsx").contentType(org.springframework.http.MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(excel.export(moduleId,body==null?null:body.get("keyword"),body==null?null:body.get("status")));}
}
