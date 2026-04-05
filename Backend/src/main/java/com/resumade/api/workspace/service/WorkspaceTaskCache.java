package com.resumade.api.workspace.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 백그라운드 LLM 파이프라인의 태스크 상태 및 결과를 Redis에 캐싱한다.
 * 사용자가 페이지를 벗어나도 작업이 완료되면 결과를 저장하고,
 * 복귀 시 폴링으로 완료 페이로드를 수령할 수 있게 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkspaceTaskCache {

    private static final String KEY_PREFIX = "workspace:task:";
    private static final Duration RUNNING_TTL = Duration.ofHours(2);
    private static final Duration RESULT_TTL = Duration.ofHours(1);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public void setRunning(Long questionId) {
        try {
            Map<String, Object> entry = new HashMap<>();
            entry.put("status", "RUNNING");
            redisTemplate.opsForValue().set(KEY_PREFIX + questionId,
                    objectMapper.writeValueAsString(entry), RUNNING_TTL);
        } catch (Exception e) {
            log.warn("Failed to set RUNNING task status for questionId={}", questionId, e);
        }
    }

    public void setComplete(Long questionId, Map<String, Object> payload) {
        try {
            Map<String, Object> entry = new HashMap<>();
            entry.put("status", "COMPLETE");
            entry.put("payload", payload);
            redisTemplate.opsForValue().set(KEY_PREFIX + questionId,
                    objectMapper.writeValueAsString(entry), RESULT_TTL);
        } catch (Exception e) {
            log.warn("Failed to set COMPLETE task status for questionId={}", questionId, e);
        }
    }

    public void setError(Long questionId, String errorMessage) {
        try {
            Map<String, Object> entry = new HashMap<>();
            entry.put("status", "ERROR");
            entry.put("errorMessage", errorMessage);
            redisTemplate.opsForValue().set(KEY_PREFIX + questionId,
                    objectMapper.writeValueAsString(entry), RESULT_TTL);
        } catch (Exception e) {
            log.warn("Failed to set ERROR task status for questionId={}", questionId, e);
        }
    }

    public Optional<Map<String, Object>> getStatus(Long questionId) {
        try {
            String raw = redisTemplate.opsForValue().get(KEY_PREFIX + questionId);
            if (raw == null) return Optional.empty();
            Map<String, Object> parsed = objectMapper.readValue(raw, new TypeReference<>() {});
            return Optional.of(parsed);
        } catch (Exception e) {
            log.warn("Failed to get task status for questionId={}", questionId, e);
            return Optional.empty();
        }
    }

    public void delete(Long questionId) {
        try {
            redisTemplate.delete(KEY_PREFIX + questionId);
        } catch (Exception e) {
            log.warn("Failed to delete task status for questionId={}", questionId, e);
        }
    }
}
