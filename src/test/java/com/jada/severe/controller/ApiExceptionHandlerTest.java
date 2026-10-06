package com.jada.severe.controller;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

class ApiExceptionHandlerTest {
    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void authorizationFailureKeepsItsStatusAndReason() {
        ResponseEntity<?> response = handler.status(
                new ResponseStatusException(HttpStatus.FORBIDDEN, "无操作权限"));
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertNotNull(body);
        assertEquals(false, body.get("success"));
        assertEquals("FORBIDDEN", body.get("code"));
        assertEquals("无操作权限", body.get("message"));
    }

    @Test
    void databaseConflictDoesNotExposeSqlOrCredentials() {
        ResponseEntity<?> response = handler.conflict(
                new DataIntegrityViolationException("SQL password=private-value"));
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertNotNull(body);
        assertEquals(false, body.get("success"));
        assertEquals("CONFLICT", body.get("code"));
        assertFalse(body.toString().contains("private-value"));
        assertFalse(body.toString().contains("SQL"));
    }

    @Test
    void invalidInputReturnsBadRequestWithoutInternalDetails() {
        ResponseEntity<?> response = handler.bad(new IllegalArgumentException("internal-detail"));
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertFalse(response.getBody().toString().contains("internal-detail"));
    }
}
