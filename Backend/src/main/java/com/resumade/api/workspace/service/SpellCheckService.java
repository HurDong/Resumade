package com.resumade.api.workspace.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumade.api.workspace.dto.SpellCheckResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 한국어 맞춤법 검사 서비스.
 *
 * <p>{@link SpellCheckAiService}의 래퍼 역할을 담당하며,
 * LLM 호출 실패 시 빈 제안 목록으로 안전하게 폴백(Graceful Degradation)한다.</p>
 *
 * <p><b>입력 제약</b>
 * <ul>
 *   <li>텍스트가 null 이거나 공백뿐이면 LLM 을 호출하지 않고 즉시 빈 응답을 반환한다.</li>
 *   <li>텍스트 길이가 {@code MAX_TEXT_LENGTH}를 초과하면 LLM 을 호출하지 않는다.
 *       자기소개서 한 문항 최대 글자 수(통상 1,500자)를 고려한 상한이다.</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpellCheckService {

    /** 단일 호출 텍스트 상한 (자기소개서 1문항 최대치 고려) */
    private static final int MAX_TEXT_LENGTH = 3_000;

    private final SpellCheckAiService spellCheckAiService;
    private final ObjectMapper objectMapper;

    /**
     * 주어진 텍스트의 맞춤법·띄어쓰기 오류를 감지하여 제안 목록을 반환한다.
     *
     * @param text 검사 대상 원문 텍스트
     * @return 오류 제안 목록 (오류가 없거나 호출 실패 시 빈 리스트)
     */
    public SpellCheckResponse check(String text) {
        if (text == null || text.isBlank()) {
            return SpellCheckResponse.empty();
        }

        if (text.length() > MAX_TEXT_LENGTH) {
            log.warn("SpellCheck skipped: text exceeds max length ({} chars)", text.length());
            return SpellCheckResponse.empty();
        }

        log.info("[SpellCheck] calling LLM, textLength={}", text.length());
        try {
            String raw = spellCheckAiService.check(text);
            log.info("[SpellCheck] raw LLM response: {}", raw);

            if (raw == null || raw.isBlank()) {
                log.warn("[SpellCheck] empty raw response → fallback");
                return SpellCheckResponse.empty();
            }

            String json = raw.strip();
            if (json.startsWith("```")) {
                json = json.replaceAll("(?s)^```[a-zA-Z]*\\n?", "").replaceAll("```$", "").strip();
            }

            SpellCheckResponse response = objectMapper.readValue(json, SpellCheckResponse.class);

            if (response == null || response.getCorrections() == null) {
                log.warn("[SpellCheck] deserialization produced null → fallback");
                return SpellCheckResponse.empty();
            }

            log.info("[SpellCheck] completed: corrections={}, items={}",
                    response.getCorrections().size(), response.getCorrections());
            return response;

        } catch (Exception e) {
            log.error("[SpellCheck] AI call failed", e);
            return SpellCheckResponse.empty();
        }
    }
}
