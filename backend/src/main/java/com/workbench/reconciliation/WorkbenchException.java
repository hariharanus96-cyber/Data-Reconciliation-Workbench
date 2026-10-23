package com.workbench.reconciliation;

import org.springframework.http.HttpStatus;

class NotFoundException extends RuntimeException {
    NotFoundException(String message) { super(message); }
}

class InvalidRequestException extends RuntimeException {
    InvalidRequestException(String message) { super(message); }
}

class WorkbenchException extends RuntimeException {
    private final HttpStatus status;
    WorkbenchException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
    HttpStatus getStatus() { return status; }
}