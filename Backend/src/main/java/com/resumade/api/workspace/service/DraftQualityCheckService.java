package com.resumade.api.workspace.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumade.api.workspace.dto.DraftQualityResult;
import com.resumade.api.workspace.prompt.QuestionProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 2-Tier 초안 품질 검수 서비스.
 *
 * <ul>
 *   <li>Tier-1: 서버사이드 글자수 체크 — LLM 호출 없음</li>
 *   <li>Tier-2: requiredElements 충족 여부 — 경량 LLM 호출 (복합 문항만)</li>
 * </ul>
 *
 * <p>Tier-1 실패 시 Tier-2를 건너뛰고 바로 lengthFail 반환.
 * Tier-1 통과 + requiredElements 없으면 즉시 ok 반환.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DraftQualityCheckService {

    private final DraftQualityCheckerAiService draftQualityCheckerAiService;
    private final ObjectMapper objectMapper;

    /**
     * 초안의 글자수 및 필수 요소 충족 여부를 검사합니다.
     *
     * @param draft            검사할 초안 텍스트
     * @param profile          QuestionProfile (requiredElements 포함)
     * @param minTargetChars   최소 목표 글자수
     * @return 검수 결과
     */
    public DraftQualityResult check(String draft, QuestionProfile profile, int minTargetChars, int maxTargetChars) {
        if (draft == null || draft.isBlank()) {
            return DraftQualityResult.lengthFail(0, minTargetChars);
        }

        // Tier-1: 글자수 체크 (서버사이드)
        int currentChars = countVisibleChars(draft);
        if (currentChars < minTargetChars) {
            log.info("DraftQualityCheck: Tier-1 FAIL currentChars={} minTarget={}", currentChars, minTargetChars);
            return DraftQualityResult.lengthFail(currentChars, minTargetChars);
        }
        if (currentChars > maxTargetChars) {
            log.info("DraftQualityCheck: Tier-1 too long currentChars={} maxTarget={} → skip to length fitting", currentChars, maxTargetChars);
            return DraftQualityResult.ok();
        }

        // Tier-2: requiredElements 충족 체크 (복합 문항만)
        List<String> required = profile.requiredElements();
        if (required.isEmpty()) {
            return DraftQualityResult.ok();
        }

        try {
            String elementsStr = String.join("\n", required.stream()
                    .map(e -> "- " + e).toList());
            String raw = draftQualityCheckerAiService.check(elementsStr, draft);
            return parseCheckResult(raw);
        } catch (Exception e) {
            log.warn("DraftQualityCheck: Tier-2 LLM failed, treating as passed. reason={}", e.getMessage());
            return DraftQualityResult.ok();
        }
    }

    private DraftQualityResult parseCheckResult(String raw) {
        try {
            String sanitized = sanitizeJson(raw);
            JsonNode node = objectMapper.readTree(sanitized);

            boolean passed = node.path("passed").asBoolean(true);
            if (passed) {
                return DraftQualityResult.ok();
            }

            List<String> missing = new ArrayList<>();
            if (node.has("missingElements") && node.get("missingElements").isArray()) {
                for (JsonNode el : node.get("missingElements")) {
                    String text = el.asText("").trim();
                    if (!text.isBlank()) missing.add(text);
                }
            }

            String feedback = node.path("feedback").asText("");
            log.info("DraftQualityCheck: Tier-2 FAIL missingElements={}", missing);
            return DraftQualityResult.elementsFail(missing, feedback);

        } catch (Exception e) {
            log.warn("DraftQualityCheck: Tier-2 parse failed, treating as passed. reason={}", e.getMessage());
            return DraftQualityResult.ok();
        }
    }

    /** 자소서 글자수 카운트 — 공백·줄바꿈 포함, 제목 줄 제외 */
    private int countChars(String text) {
        if (text == null) return 0;
        String[] lines = text.split("\n", 2);
        // 첫 줄이 제목(짧고 다음 줄이 있으면)이면 제외
        if (lines.length == 2 && lines[0].trim().length() <= 50) {
            return lines[1].replaceAll("\\s", "").length();
        }
        return text.replaceAll("\\s", "").length();
    }

    private static String sanitizeJson(String raw) {
        if (raw == null) return "{}";
        String trimmed = raw.strip();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?\\s*", "");
            trimmed = trimmed.replaceAll("```\\s*$", "").strip();
        }
        return trimmed;
    }

    private int countVisibleChars(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        int count = 0;
        for (int i = 0; i < normalized.length();) {
            int codePoint = normalized.codePointAt(i);
            i += Character.charCount(codePoint);

            if (codePoint == '\n') {
                count++;
                continue;
            }

            if (Character.isISOControl(codePoint)) {
                continue;
            }

            if (Character.getType(codePoint) == Character.FORMAT) {
                continue;
            }

            count++;
        }
        return count;
    }
}
