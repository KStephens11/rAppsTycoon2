package com.rapptycoon.exception;

import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.OptimisticLockException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(SessionNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleSessionNotFound(SessionNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(SessionFullException.class)
    public ResponseEntity<Map<String, Object>> handleSessionFull(SessionFullException ex) {
        return buildResponse(HttpStatus.CONFLICT, "SESSION_FULL", ex.getMessage());
    }

    @ExceptionHandler(InvalidStateException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidState(InvalidStateException ex) {
        return buildResponse(HttpStatus.CONFLICT, "INVALID_STATE", ex.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(ForbiddenException ex) {
        return buildResponse(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthorized(UnauthorizedException ex) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", ex.getMessage());
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleEntityNotFound(EntityNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage());
    }

    @ExceptionHandler({OptimisticLockException.class, ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<Map<String, Object>> handleOptimisticLock(Exception ex) {
        return buildResponse(HttpStatus.CONFLICT, "CONFLICT", "Resource was modified by another request. Please retry.");
    }

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String error, String message) {
        Map<String, Object> body = Map.of(
                "error", error,
                "message", message,
                "timestamp", LocalDateTime.now().toString()
        );
        return ResponseEntity.status(status).body(body);
    }
}
