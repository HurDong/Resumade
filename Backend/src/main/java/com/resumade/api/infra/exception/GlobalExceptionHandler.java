package com.resumade.api.infra.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleAllExceptions(Exception e, HttpServletRequest request, HttpServletResponse response) {
        if (isClientDisconnectException(e)) {
            log.info("Client disconnected during async response: {}", e.getMessage());
            return null;
        }

        if (e instanceof AsyncRequestTimeoutException) {
            log.warn("Async request timed out for {} {}", request.getMethod(), request.getRequestURI());
            return null;
        }

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

    private boolean isClientDisconnectException(Exception e) {
        if (e instanceof AsyncRequestNotUsableException) {
            return true;
        }

        if (e instanceof IOException) {
            String message = e.getMessage();
            if (message == null) {
                return true;
            }

            String normalized = message.toLowerCase(Locale.ROOT);
            return normalized.contains("broken pipe")
                    || normalized.contains("connection reset")
                    || normalized.contains("forcibly closed")
                    || normalized.contains("software caused connection abort")
                    || normalized.contains("현재 연결은 사용자의 호스트 시스템의 소프트웨어의 의해 중단되었습니다");
        }

        return false;
    }
}
