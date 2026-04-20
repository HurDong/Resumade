package com.resumade.api.workspace.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumade.api.workspace.prompt.QuestionCategory;
import com.resumade.api.workspace.prompt.QuestionProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * QuestionAnalyzerAiService를 호출하여 QuestionProfile을 생성하는 서비스.
 *
 * <p>v2 파이프라인의 첫 단계로, 기존 ClassifierAiService + IntentExtractorAiService를 대체합니다.
 * 분석 실패 시 DEFAULT 카테고리로 안전하게 폴백합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionAnalysisService {

    private final QuestionAnalyzerAiService questionAnalyzerAiService;
    private final ObjectMapper objectMapper;

    /**
     * 문항 텍스트를 분석하여 QuestionProfile을 반환합니다.
     *
     * @param questionTitle 분석할 문항 텍스트
     * @return 분석 결과. 실패 시 DEFAULT 카테고리의 안전한 폴백 프로파일 반환
     */
    public QuestionProfile analyze(String questionTitle) {
        if (questionTitle == null || questionTitle.isBlank()) {
            log.debug("QuestionAnalysisService: empty question, returning DEFAULT profile");
            return QuestionProfile.simple(QuestionCategory.DEFAULT, List.of());
        }

        try {
            String raw = questionAnalyzerAiService.analyze(questionTitle.trim());
            QuestionProfile profile = parseProfile(raw);
            log.info("QuestionAnalysisService: category={} isCompound={} elements={} ragKeywords={} questionLength={}",
                    profile.primaryCategory(), profile.isCompound(),
                    profile.requiredElements().size(), profile.ragKeywords().size(),
                    questionTitle.length());
            return profile;
        } catch (Exception e) {
            log.warn("QuestionAnalysisService: analysis failed, falling back to DEFAULT. reason={}", e.getMessage());
            return QuestionProfile.simple(QuestionCategory.DEFAULT, List.of());
        }
    }

    private QuestionProfile parseProfile(String raw) throws Exception {
        String sanitized = sanitizeJson(raw);
        JsonNode node = objectMapper.readTree(sanitized);

        QuestionCategory category = QuestionCategory.fromString(
                node.path("primaryCategory").asText(QuestionCategory.DEFAULT.name()));

        boolean isCompound = node.path("isCompound").asBoolean(false);

        String framingNote = null;
        if (isCompound) {
            String fn = node.path("framingNote").asText(null);
            framingNote = (fn == null || fn.isBlank() || fn.equalsIgnoreCase("null")) ? null : fn;
        }

        List<String> requiredElements = new ArrayList<>();
        if (isCompound && node.has("requiredElements") && node.get("requiredElements").isArray()) {
            for (JsonNode el : node.get("requiredElements")) {
                String text = el.asText("").trim();
                if (!text.isBlank()) requiredElements.add(text);
            }
        }

        List<String> ragKeywords = new ArrayList<>();
        if (node.has("ragKeywords") && node.get("ragKeywords").isArray()) {
            for (JsonNode kw : node.get("ragKeywords")) {
                String text = kw.asText("").trim();
                if (!text.isBlank()) ragKeywords.add(text);
            }
        }

        return new QuestionProfile(category, isCompound, framingNote, requiredElements, ragKeywords);
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
}
