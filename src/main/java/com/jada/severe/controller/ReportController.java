package com.jada.severe.controller;
import com.jada.severe.service.ReportService;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.time.LocalDate;
@RestController
@RequestMapping("/api")
public class ReportController {
 private final ReportService reports;public ReportController(ReportService reports){this.reports=reports;}
 @GetMapping({"/workbench","/dashboard/stats"}) public Object dashboard(){return Map.of("success",true,"data",reports.dashboard());}
 @GetMapping("/reports/daily-equipment") public Object daily(@RequestParam(required=false) LocalDate date){return Map.of("success",true,"data",reports.daily(date==null?LocalDate.now():date));}
 @GetMapping("/notifications") public Object notices(){return Map.of("success",true,"data",reports.notifications());}
 @PostMapping("/notifications/{id}/read") public Object read(@PathVariable UUID id){reports.readNotification(id);return Map.of("success",true,"data",Map.of());}
}
