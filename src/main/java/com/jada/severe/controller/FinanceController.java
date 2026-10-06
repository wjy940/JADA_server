package com.jada.severe.controller;
import com.jada.severe.service.FinanceService;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
@RequestMapping("/api/finance")
public class FinanceController {
 private final FinanceService finance;public FinanceController(FinanceService finance){this.finance=finance;}
 @PostMapping("/settlements/{id}/confirm") public Object confirm(@PathVariable UUID id,@RequestBody Map<String,Object> body){return Map.of("success",true,"data",finance.confirm(id,body));}
 @PostMapping("/settlements/{id}/payments") public Object pay(@PathVariable UUID id,@RequestBody Map<String,Object> body){return Map.of("success",true,"data",finance.pay(id,body));}
 @GetMapping("/settlements/{id}/payments") public Object payments(@PathVariable UUID id){return Map.of("success",true,"data",finance.payments(id));}
 @PostMapping("/payments/{id}/reverse") public Object reverse(@PathVariable UUID id,@RequestBody Map<String,Object> body){return Map.of("success",true,"data",finance.reverse(id,body));}
}
