package com.jada.severe.controller;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import java.util.Map;
@RestControllerAdvice
public class ApiExceptionHandler {
 private ResponseEntity<?> error(HttpStatus status,String message){return ResponseEntity.status(status).body(Map.of("success",false,"message",message,"code",status.name(),"error",Map.of("code",status.name(),"message",message)));}
 @ExceptionHandler(ResponseStatusException.class) public ResponseEntity<?> status(ResponseStatusException e){return error(HttpStatus.valueOf(e.getStatusCode().value()),e.getReason()==null?"请求失败":e.getReason());}
 @ExceptionHandler(DataIntegrityViolationException.class) public ResponseEntity<?> conflict(Exception e){return error(HttpStatus.CONFLICT,"数据约束冲突，请检查唯一编号及关联账号、角色和权限");}
 @ExceptionHandler({IllegalArgumentException.class,MethodArgumentTypeMismatchException.class,HttpMessageNotReadableException.class,org.springframework.web.bind.MethodArgumentNotValidException.class,jakarta.validation.ConstraintViolationException.class,org.springframework.web.method.annotation.HandlerMethodValidationException.class}) public ResponseEntity<?> bad(Exception e){return error(HttpStatus.BAD_REQUEST,"请求参数无效");}
 @ExceptionHandler(org.springframework.dao.EmptyResultDataAccessException.class) public ResponseEntity<?> missing(Exception e){return error(HttpStatus.NOT_FOUND,"记录不存在");}
 @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class) public ResponseEntity<?> tooLarge(Exception e){return error(HttpStatus.PAYLOAD_TOO_LARGE,"上传文件超过允许大小");}
 @ExceptionHandler(org.springframework.dao.TransientDataAccessException.class) public ResponseEntity<?> retry(Exception e){return error(HttpStatus.SERVICE_UNAVAILABLE,"数据库暂时不可用，请稍后重试");}
 @ExceptionHandler(Exception.class) public ResponseEntity<?> other(Exception e){org.slf4j.LoggerFactory.getLogger(getClass()).error("API failure",e);return error(HttpStatus.INTERNAL_SERVER_ERROR,"服务器处理失败");}
}
