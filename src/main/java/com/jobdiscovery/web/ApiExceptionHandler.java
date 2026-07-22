package com.jobdiscovery.web;

import com.jobdiscovery.source.adzuna.AdzunaClientException;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Translates known application exceptions into clean JSON error responses. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(AdzunaClientException.class)
    public ResponseEntity<Map<String, Object>> handleAdzuna(AdzunaClientException e) {
        // 502 Bad Gateway: we failed while talking to an upstream dependency.
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "timestamp", Instant.now().toString(),
                "error", "adzuna_client_error",
                "message", e.getMessage()));
    }
}
