package com.resumade.api.workspace.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumade.api.workspace.prompt.ClassificationResult;
import com.resumade.api.workspace.prompt.QuestionCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 자기소개서 문항을 {@link QuestionCategory}로 분류하는 서비스.
 *
 * <h2>설계 원칙</h2>
 * <ul>
 *   <li>빠른 분류를 위해 소형 모델(gpt-4o-mini)을 사용합니다.</li>
 *   <li>분류 실패(예외, 파싱 오류) 시 반드시 {@link QuestionCategory#DEFAULT}로 fallback합니다.</li>
 *   <li>복합 문항 감지 시 {@link IntentExtractorAiService}로 세부 항목을 추출합니다.
 *       추출 실패 시에도 파이프라인은 중단되지 않습니다.</li>
 *   <li>분류 결과를 로그에 남기되, 문항 내용 자체는 WARN 레벨 이상에서 노출하지 않습니다(PII 준수).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionClassifierService {

    private final ClassifierAiService classifierAiService;
    private final IntentExtractorAiService intentExtractorAiService;
    private final ObjectMapper objectMapper;

    // 복합 문항 판별 패턴: "및", "또한", "그리고", "뿐만 아니라", "~며", "~하며", "~이며" 등
    private static final Pattern COMPOUND_PATTERN = Pattern.compile(
            "및|또한|그리고|뿐만\\s*아니라|~며|(?<=\\S)(?:하며|이며|며,|며\\s)"
    );

    /**
     * 문항을 분류하고 복합 문항이면 세부 intents도 함께 반환합니다.
     *
     * @param questionTitle 분류할 문항 텍스트
     * @return 분류 결과. 단순 문항이면 allIntents는 빈 리스트.
     */
    public ClassificationResult classifyWithIntents(String questionTitle) {
        if (questionTitle == null || questionTitle.isBlank()) {
            log.debug("QuestionClassifier: empty question title, returning DEFAULT");
            return ClassificationResult.simple(QuestionCategory.DEFAULT);
        }

        String trimmed = questionTitle.trim();
        QuestionCategory category = classifyCategory(trimmed);

        if (!isLikelyCompound(trimmed)) {
            return ClassificationResult.simple(category);
        }

        List<String> intents = extractIntents(trimmed);
        if (intents.size() <= 1) {
            return ClassificationResult.simple(category);
        }

        log.info("QuestionClassifier: compound detected category={} intents={} questionLength={}",
                category, intents.size(), trimmed.length());
        return ClassificationResult.compound(category, intents);
    }

    /** 하위 호환용 단순 분류 메서드. */
    public QuestionCategory classify(String questionTitle) {
        return classifyWithIntents(questionTitle).primaryCategory();
    }

    // -------------------------------------------------------------------------

    private QuestionCategory classifyCategory(String question) {
        try {
            String rawResult = classifierAiService.classify(question);
            QuestionCategory category = QuestionCategory.fromString(rawResult);
            log.info("QuestionClassifier: category={} (raw=\"{}\") questionLength={}",
                    category,
                    rawResult != null ? rawResult.trim() : "null",
                    question.length());
            return category;
        } catch (Exception e) {
            log.warn("QuestionClassifier: classification failed, falling back to DEFAULT. reason={}", e.getMessage());
            return QuestionCategory.DEFAULT;
        }
    }

    /**
     * 휴리스틱으로 복합 문항 여부를 판별합니다.
     * LLM 호출 없이 빠르게 판단하여 단순 문항의 추가 비용을 0으로 유지합니다.
     */
    private boolean isLikelyCompound(String question) {
        if (question.length() < 25) {
            return false;
        }
        // "및", "또한", "그리고" 등 복합 연결어 포함 여부
        if (COMPOUND_PATTERN.matcher(question).find()) {
            return true;
        }
        // 종결 요청어("~해주세요", "~기술하시오", "~말씀해주세요")가 2개 이상이면 복합
        long terminatorCount = countTerminators(question);
        return terminatorCount >= 2;
    }

    private long countTerminators(String question) {
        String[] terminators = {"해주세요", "기술하시오", "서술하시오", "말씀해주세요", "설명해주세요", "작성하시오"};
        long count = 0;
        for (String t : terminators) {
            int idx = 0;
            while ((idx = question.indexOf(t, idx)) >= 0) {
                count++;
                idx += t.length();
            }
        }
        return count;
    }

    private List<String> extractIntents(String question) {
        try {
            String raw = intentExtractorAiService.extractIntents(question);
            String sanitized = sanitizeJsonArray(raw);
            List<String> intents = objectMapper.readValue(sanitized, new TypeReference<>() {});
            // "단일 문항" 반환은 사실상 단순 문항 신호
            if (intents.size() == 1 && intents.get(0).contains("단일")) {
                return List.of();
            }
            return intents;
        } catch (Exception e) {
            log.warn("QuestionClassifier: intent extraction failed, treating as simple. reason={}", e.getMessage());
            return List.of();
        }
    }

    private static String sanitizeJsonArray(String raw) {
        if (raw == null) return "[]";
        String trimmed = raw.strip();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?\\s*", "");
            trimmed = trimmed.replaceAll("```\\s*$", "").strip();
        }
        return trimmed;
    }
}
