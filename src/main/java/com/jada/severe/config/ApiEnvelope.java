package com.jada.severe.config;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.core.MethodParameter;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.*;
import java.util.*;
@RestControllerAdvice
public class ApiEnvelope implements ResponseBodyAdvice<Object> {
 public boolean supports(MethodParameter m,Class<? extends HttpMessageConverter<?>> c){return true;}
 public Object beforeBodyWrite(Object body,MethodParameter m,MediaType t,Class<? extends HttpMessageConverter<?>> c,ServerHttpRequest q,ServerHttpResponse r){if(body instanceof Map<?,?> map){var out=new LinkedHashMap<String,Object>();map.forEach((k,v)->out.put(k.toString(),v));if(!out.containsKey("success")){out.put("success",true);out.put("data",body);}out.putIfAbsent("message","");return out;}return body;}
}
