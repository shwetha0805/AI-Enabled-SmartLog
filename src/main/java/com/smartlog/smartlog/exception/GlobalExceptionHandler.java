package com.smartlog.smartlog.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleException(Exception e) {

        // Print the real error in the Spring Boot console
        e.printStackTrace();

        Map<String, String> error = new HashMap<>();

        error.put("status", "ERROR");

        error.put(
                "message",
                e.getMessage() != null
                        ? e.getMessage()
                        : e.getClass().getSimpleName()
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error);
    }
}