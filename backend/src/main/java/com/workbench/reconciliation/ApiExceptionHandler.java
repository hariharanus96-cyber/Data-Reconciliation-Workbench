package com.workbench.reconciliation;

import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<?> notFound(NotFoundException exception) { return response(HttpStatus.NOT_FOUND, exception.getMessage()); }

    @ExceptionHandler(InvalidRequestException.class)
    ResponseEntity<?> invalid(InvalidRequestException exception) { return response(HttpStatus.BAD_REQUEST, exception.getMessage()); }

    @ExceptionHandler(Exception.class)
    ResponseEntity<?> internal(Exception exception) { return response(HttpStatus.INTERNAL_SERVER_ERROR, "Request could not be completed."); }

    private ResponseEntity<?> response(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("status", status.value(), "message", message,
                "timestamp", LocalDateTime.now()));
    }
}