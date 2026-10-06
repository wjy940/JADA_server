package com.jada.severe.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {
    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> data = jdbcTemplate.queryForMap("""
            select current_database() as database,
                   current_user as "user",
                   (
                     select count(*)::int
                     from information_schema.tables
                     where table_schema = 'public'
                       and table_type = 'BASE TABLE'
                   ) as tables
            """);
        return Map.of("success", true, "data", data);
    }
}
