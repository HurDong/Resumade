package com.resumade.api.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthCheckController {

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;

    @GetMapping
    public ResponseEntity<Map<String, Object>> checkHealth() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "UP");

        // 1. Check MySQL
        try {
            jdbcTemplate.execute("SELECT 1");
            status.put("mysql", "OK");
        } catch (Exception e) {
            status.put("mysql", "DOWN - " + e.getMessage());
        }

        // 2. Check Redis
        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
            status.put("redis", "OK");
        } catch (Exception e) {
            status.put("redis", "DOWN - " + e.getMessage());
        }

        // Return health check results
        return ResponseEntity.ok(status);
    }
}
