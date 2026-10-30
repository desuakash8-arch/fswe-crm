package com.klu.crmbackend.config;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private ResponseEntity<?> error(int status, String message) {
        return ResponseEntity.status(status).body(Map.of("status", status, "message", message == null ? "Request failed" : message));
    }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> status(ResponseStatusException ex) { return error(ex.getStatusCode().value(), ex.getReason()); }
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> denied(AccessDeniedException ex) { return error(403, "You do not have permission for this action"); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> conflict(DataIntegrityViolationException ex) {
        return error(409, "This record conflicts with existing data or is still referenced by another record");
    }
    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<?> invalid(Exception ex) { return error(400, "Invalid request: " + (ex instanceof IllegalArgumentException ? ex.getMessage() : "check required fields and date formats")); }
    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<?> badParameter(Exception ex) { return error(400,"Invalid query or path parameter"); }
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<?> runtime(RuntimeException ex) { log.error("Request failed", ex); return error(500, "An unexpected error occurred. Check the backend log."); }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> unexpected(Exception ex) { log.error("Unexpected request error", ex); return error(500, "An unexpected error occurred"); }
}
