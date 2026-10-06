package com.jada.severe.config;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.*;
@Configuration
public class JsonConfiguration {
 @Bean public ObjectMapper objectMapper(){return new ObjectMapper().findAndRegisterModules();}
}
