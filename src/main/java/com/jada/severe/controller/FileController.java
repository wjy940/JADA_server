package com.jada.severe.controller;
import com.jada.severe.service.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.*;
import org.springframework.core.io.Resource;
import java.util.*;
@RestController
@RequestMapping("/api")
public class FileController {
 private final FileService files;private final ModuleService modules;
 public FileController(FileService files,ModuleService modules){this.files=files;this.modules=modules;}
 @PostMapping(value="/attachments",consumes="multipart/form-data") public Object upload(@RequestParam String moduleId,@RequestParam UUID recordId,@RequestParam MultipartFile file){return Map.of("success",true,"data",files.upload(moduleId,recordId,file));}
 @GetMapping("/attachments") public Object list(@RequestParam String moduleId,@RequestParam UUID recordId){return Map.of("success",true,"data",modules.attachments(moduleId,recordId));}
 @GetMapping({"/attachments/{id}/download","/attachments/{id}/preview"}) public ResponseEntity<Resource> download(@PathVariable UUID id,jakarta.servlet.http.HttpServletRequest request){var metadata=files.metadata(id,"attachments");boolean preview=request.getRequestURI().endsWith("/preview");String type=Objects.toString(metadata.get("content_type"),"application/octet-stream");var disposition=preview&&!type.equals("application/octet-stream")?ContentDisposition.inline():ContentDisposition.attachment();return ResponseEntity.ok().header("X-Content-Type-Options","nosniff").header("Content-Disposition",disposition.filename(metadata.get("name").toString(),java.nio.charset.StandardCharsets.UTF_8).build().toString()).contentType(MediaType.parseMediaType(type)).body(files.download(id));}
 @DeleteMapping("/attachments/{id}") public Object delete(@PathVariable UUID id){files.delete(id);return Map.of("success",true,"data",Map.of());}
}
