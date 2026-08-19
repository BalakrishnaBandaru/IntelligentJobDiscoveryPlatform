package com.jobdiscovery.web;

import com.jobdiscovery.profile.ProfileNotFoundException;
import com.jobdiscovery.source.SourceApiException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Translates known application exceptions into clean JSON error responses. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(SourceApiException.class)
    public ResponseEntity<Map<String, Object>> handleSourceApi(SourceApiException e) {
        // 502 Bad Gateway: we failed while talking to an upstream source API.
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "timestamp", Instant.now().toString(),
                "error", "source_api_error",
                "source", e.getSource(),
                "message", e.getMessage()));
    }

    @ExceptionHandler(ProfileNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleProfileNotFound(ProfileNotFoundException e) {
        // 404 Not Found: no candidate profile has been created yet.
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "timestamp", Instant.now().toString(),
                "error", "profile_not_found",
                "message", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        // 400 Bad Request: the request body failed bean validation. Report each
        // offending field with its message so the caller can fix the payload.
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "timestamp", Instant.now().toString(),
                "error", "validation_failed",
                "fields", fields));
    }
}
