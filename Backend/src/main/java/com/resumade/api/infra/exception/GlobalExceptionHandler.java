package com.resumade.api.infra.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleAllExceptions(Exception e, HttpServletRequest request, HttpServletResponse response) {
        log.error("Global exception caught: {}", e.getMessage(), e);

        String accept = request.getHeader("Accept");
        if (response.isCommitted() || (accept != null && accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE))) {
            return null;
        }

        Map<String, String> responseBody = new HashMap<>();
        responseBody.put("message", e.getMessage());
        responseBody.put("type", e.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(responseBody);
    }
}
